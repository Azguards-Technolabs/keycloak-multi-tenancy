package dev.sultanov.keycloak.multitenancy.resource;

import dev.sultanov.keycloak.multitenancy.dto.BusinessStatus;
import dev.sultanov.keycloak.multitenancy.dto.BusinessStatusEntry;
import dev.sultanov.keycloak.multitenancy.dto.BusinessStatusRequest;
import dev.sultanov.keycloak.multitenancy.util.Constants;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.util.JsonSerialization;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import brave.Span;
import dev.sultanov.keycloak.multitenancy.tracing.TracingHelper;

public class UserServiceRestClient {

    private static final Logger log = Logger.getLogger(UserServiceRestClient.class);
    private static final String USER_SERVICE_URL = "http://host.docker.internal:4003/user-service/v1/business/updateStatus";

    // Backpressure config (P4) — overridable at deploy time, sane defaults for typical load.
    private static final int ASYNC_POOL_SIZE = Integer.getInteger("user-service.async.pool-size", 2);
    private static final int ASYNC_MAX_PENDING = Integer.getInteger("user-service.async.max-pending", 1000);

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);
    // Number of in-flight retry chains (one per submitAsync), used as a soft queue bound.
    private static final AtomicInteger PENDING_TASKS = new AtomicInteger(0);
    private static final ScheduledExecutorService ASYNC_EXECUTOR =
            Executors.newScheduledThreadPool(ASYNC_POOL_SIZE, r -> {
                Thread t = new Thread(r, "user-service-async-" + THREAD_COUNTER.getAndIncrement());
                t.setDaemon(true);
                return t;
            });

    private final HttpClient httpClient;

    public UserServiceRestClient() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        log.debug("Initialized UserServiceRestClient with connect timeout: 10 seconds");
    }

    public void updateUserTenantInvitationStatuses(String userId, List<String> accepted, List<String> rejected) {
        Span span = TracingHelper.startClientSpan("user-service.updateStatus");
        Throwable traceError = null;
        try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
            span.tag("user.id", userId);
            span.tag("http.url", USER_SERVICE_URL);
            span.tag("http.method", "PATCH");

            log.infof("Starting user service update for userId: %s, accepted: %s, rejected: %s", 
                      userId, accepted, rejected);

            if (ObjectUtils.isEmpty(accepted) && ObjectUtils.isEmpty(rejected)) {
                log.infof("No tenant invitations to update for userId: %s, skipping request", userId);
                return;
            }

            List<BusinessStatusEntry> businessStatusList = new ArrayList<>();

            if (!ObjectUtils.isEmpty(accepted)) {
                businessStatusList.add(new BusinessStatusEntry(BusinessStatus.ACTIVE.name(), accepted));
                log.debugf("Added Active status for userId: %s with businessIds: %s", userId, accepted);
            }

            if (!ObjectUtils.isEmpty(rejected)) {
                businessStatusList.add(new BusinessStatusEntry(BusinessStatus.INACTIVE.name(), rejected));
                log.debugf("Added Reject status for userId: %s with businessIds: %s", userId, rejected);
            }

            BusinessStatusRequest finalPayload = new BusinessStatusRequest(businessStatusList);

            String json = JsonSerialization.writeValueAsString(finalPayload);
            log.infof("Prepared payload for userId: %s: %s", userId, json);

            Map<String, String> traceHeaders = new HashMap<>();
            TracingHelper.injectB3Headers(span, traceHeaders);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(USER_SERVICE_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("userId", userId);
            traceHeaders.forEach((k, v) -> {
                if (StringUtils.isNotBlank(v)) {
                    reqBuilder.header(k, v);
                }
            });

            HttpRequest request = reqBuilder
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                .build();

            log.debugf("Constructed HTTP PATCH request for userId: %s, URL: %s", 
                       userId, USER_SERVICE_URL);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            span.tag("http.status_code", String.valueOf(response.statusCode()));

            log.infof("Received response from user service for userId: %s, status: %d, body: %s", 
                      userId, response.statusCode(), response.body());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.infof("User service status update succeeded for userId: %s. Response: %s", userId, response.body());
            } else {
                String errorMsg = String.format("User service update failed for userId: %s with status %d. Response: %s",
                        userId, response.statusCode(), response.body());
                log.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }

        } catch (Exception ex) {
            traceError = ex;
            log.errorf(ex, "Exception while processing user service update for userId: %s", userId);
            throw new RuntimeException("Error during user service status update: " + ex.getMessage(), ex);
        } finally {
            TracingHelper.finishSpan(span, traceError);
        }
    }

    public void submitAsync(String userId, List<String> accepted, List<String> rejected,
                            KeycloakSessionFactory sessionFactory, String realmId,
                            int maxRetries, long backoffSeconds) {
        log.debugf("Scheduling async user-service call for userId: %s (maxRetries=%d, backoff=%ds)",
                userId, maxRetries, backoffSeconds);

        // Soft queue bound (P4): refuse new chains once too many are in flight, so a downstream
        // outage during a login burst cannot grow the scheduler's queue without limit.
        int pending = PENDING_TASKS.incrementAndGet();
        if (pending > ASYNC_MAX_PENDING) {
            PENDING_TASKS.decrementAndGet();
            log.warnf("Async user-service queue full (%d in flight, max %d) — dropping sync for userId: %s; marking retry-needed",
                    pending - 1, ASYNC_MAX_PENDING, userId);
            setRetryFlagSafe(sessionFactory, realmId, userId);
            return;
        }

        try {
            ASYNC_EXECUTOR.schedule(() -> scheduleAttempt(userId, accepted, rejected, sessionFactory, realmId, maxRetries, backoffSeconds, 0),
                    0, TimeUnit.SECONDS);
        } catch (RejectedExecutionException ree) {
            PENDING_TASKS.decrementAndGet();
            log.warnf(ree, "Could not submit async user-service sync for userId: %s — executor unavailable; marking retry-needed", userId);
            setRetryFlagSafe(sessionFactory, realmId, userId);
        }
    }

    private void scheduleAttempt(String userId, List<String> accepted, List<String> rejected,
                                 KeycloakSessionFactory sessionFactory, String realmId,
                                 int maxRetries, long backoffSeconds, int attempt) {
        Span span = TracingHelper.startClientSpan("user-service.updateStatus.async");
        span.tag("retry.attempt", String.valueOf(attempt));
        span.tag("user.id", userId);
        Throwable traceError = null;
        boolean syncSucceeded = false;
        boolean retryScheduled = false;
        try {
            updateUserTenantInvitationStatuses(userId, accepted, rejected);
            syncSucceeded = true;
            log.infof("Async user-service call succeeded for userId: %s (attempt %d)", userId, attempt);
        } catch (Exception e) {
            traceError = e;
            if (attempt < maxRetries) {
                log.warnf(e, "Async user-service call failed for userId: %s (attempt %d) — retrying in %ds",
                        userId, attempt, backoffSeconds);
                try {
                    ASYNC_EXECUTOR.schedule(
                            () -> scheduleAttempt(userId, accepted, rejected, sessionFactory, realmId, maxRetries, backoffSeconds, attempt + 1),
                            backoffSeconds, TimeUnit.SECONDS);
                    retryScheduled = true;
                } catch (RejectedExecutionException ree) {
                    log.warnf(ree, "Could not schedule retry for userId: %s — executor unavailable; setting retry flag", userId);
                    setRetryFlagSafe(sessionFactory, realmId, userId);
                }
            } else {
                log.warnf(e, "User-service sync failed after all retries for user %s", userId);
                setRetryFlagSafe(sessionFactory, realmId, userId);
            }
        } finally {
            TracingHelper.finishSpan(span, traceError);
            // Release the queue slot only when this retry chain has reached a terminal state (P4).
            if (!retryScheduled) {
                PENDING_TASKS.decrementAndGet();
            }
        }

        // Clearing the retry flag runs OUTSIDE the retry-triggering try (P1): a failure here must
        // never re-send an already-successful sync nor leave a spurious retry-needed banner.
        if (syncSucceeded) {
            try {
                clearRetryFlag(sessionFactory, realmId, userId);
            } catch (Exception ce) {
                log.warnf(ce, "Sync succeeded but could not clear retry flag for user %s", userId);
            }
        }
    }

    // Guards setRetryFlag so a transaction failure here (P2) cannot escape into the executor and be
    // silently swallowed, leaving the terminal failure invisible.
    private void setRetryFlagSafe(KeycloakSessionFactory factory, String realmId, String userId) {
        try {
            setRetryFlag(factory, realmId, userId);
        } catch (Exception se) {
            log.errorf(se, "Could not persist retry flag for user %s", userId);
        }
    }

    private void clearRetryFlag(KeycloakSessionFactory factory, String realmId, String userId) {
        KeycloakModelUtils.runJobInTransaction(factory, session -> {
            var realm = session.realms().getRealm(realmId);
            if (realm == null) {
                log.debugf("clearRetryFlag: realm %s not found, skipping", realmId);
                return;
            }
            var user = session.users().getUserById(realm, userId);
            if (user == null) {
                log.debugf("clearRetryFlag: user %s not found in realm %s, skipping", userId, realmId);
                return;
            }
            user.removeAttribute(Constants.USER_SERVICE_SYNC_RETRY_ATTR);
            log.debugf("Cleared %s for user %s", Constants.USER_SERVICE_SYNC_RETRY_ATTR, userId);
        });
    }

    private void setRetryFlag(KeycloakSessionFactory factory, String realmId, String userId) {
        KeycloakModelUtils.runJobInTransaction(factory, session -> {
            var realm = session.realms().getRealm(realmId);
            if (realm == null) {
                log.warnf("setRetryFlag: realm %s not found, cannot set retry flag for user %s", realmId, userId);
                return;
            }
            var user = session.users().getUserById(realm, userId);
            if (user == null) {
                log.warnf("setRetryFlag: user %s not found in realm %s, cannot set retry flag", userId, realmId);
                return;
            }
            user.setSingleAttribute(Constants.USER_SERVICE_SYNC_RETRY_ATTR, "true");
            log.warnf("User-service sync failed after all retries for user %s", userId);
        });
    }
}
