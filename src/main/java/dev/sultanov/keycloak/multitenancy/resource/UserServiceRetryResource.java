package dev.sultanov.keycloak.multitenancy.resource;

import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import dev.sultanov.keycloak.multitenancy.util.Constants;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.managers.AuthenticationManager;

public class UserServiceRetryResource {

    private static final Logger log = Logger.getLogger(UserServiceRetryResource.class);

    private final KeycloakSession session;
    private final UserServiceRestClient userServiceRestClient = new UserServiceRestClient();

    public UserServiceRetryResource(KeycloakSession session) {
        this.session = session;
    }

    @POST
    public Response retrySync() {
        RealmModel realm = session.getContext().getRealm();

        var authResult = AuthenticationManager.authenticateIdentityCookie(session, realm, true);
        if (authResult == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        var user = authResult.getUser();
        String userId = user.getId();

        // Rate-limit (P5): only do work when a sync actually failed — no pending failure means nothing to retry.
        if (user.getFirstAttribute(Constants.USER_SERVICE_SYNC_RETRY_ATTR) == null) {
            log.debugf("User %s requested retry but no pending sync failure flag is set; no-op", userId);
            return Response.noContent().build();
        }

        // Rate-limit (P5): per-user cooldown to prevent retry-storm amplification against the shared pool.
        String last = user.getFirstAttribute(Constants.USER_SERVICE_RETRY_LAST_ATTEMPT_ATTR);
        long now = System.currentTimeMillis();
        if (last != null) {
            try {
                long elapsed = now - Long.parseLong(last);
                if (elapsed < Constants.USER_SERVICE_RETRY_COOLDOWN_MS) {
                    log.warnf("User %s retry throttled (%dms since last attempt, cooldown %dms)",
                            userId, elapsed, Constants.USER_SERVICE_RETRY_COOLDOWN_MS);
                    return Response.status(Response.Status.TOO_MANY_REQUESTS).build();
                }
            } catch (NumberFormatException nfe) {
                log.debugf("User %s has malformed retry-last-attempt attribute '%s'; proceeding", userId, last);
            }
        }
        user.setSingleAttribute(Constants.USER_SERVICE_RETRY_LAST_ATTEMPT_ATTR, String.valueOf(now));

        String realmId = realm.getId();
        var factory = session.getKeycloakSessionFactory();

        var provider = session.getProvider(TenantProvider.class);
        List<String> tenantIds = provider.getUserTenantsStream(realm, user)
                .map(t -> t.getId())
                .collect(Collectors.toList());

        log.infof("User %s requested user-service retry sync for tenants: %s", userId, tenantIds);

        userServiceRestClient.submitAsync(userId, tenantIds, List.of(), factory, realmId,
                Constants.USER_SERVICE_MAX_RETRIES, Constants.USER_SERVICE_RETRY_BACKOFF_SECONDS);

        return Response.accepted().build();
    }
}
