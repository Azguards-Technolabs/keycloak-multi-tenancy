package dev.sultanov.keycloak.multitenancy.authentication.requiredactions;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.keycloak.Config;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.AuthenticationFlowException;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.managers.AuthenticationManager;
import dev.sultanov.keycloak.multitenancy.resource.UserServiceRestClient;
import dev.sultanov.keycloak.multitenancy.authentication.IdentityProviderTenantsConfig;
import dev.sultanov.keycloak.multitenancy.authentication.TenantsBean;
import dev.sultanov.keycloak.multitenancy.email.EmailSender;
import dev.sultanov.keycloak.multitenancy.model.TenantInvitationModel;
import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import dev.sultanov.keycloak.multitenancy.util.Constants;
import lombok.extern.jbosslog.JBossLog;
import brave.Span;
import dev.sultanov.keycloak.multitenancy.tracing.TracingHelper;

@JBossLog
public class ReviewTenantInvitations implements RequiredActionProvider, RequiredActionFactory {

    public static final String ID = "review-tenant-invitations";
    private static final String ACCEPTED_TENANTS_ATTR = "acceptedTenants";
    private static final String REJECTED_TENANTS_ATTR = "rejectedTenants";
    public static final String REVIEWED_INVITATIONS = "tenantInvitationsReviewed";


    private final UserServiceRestClient userServiceRestClient = new UserServiceRestClient();

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        Span span = TracingHelper.startServerSpan("review-invitations.evaluateTriggers");
        Throwable traceError = null;
        try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
            var user = context.getUser();
            if (user != null) {
                span.tag("user.id", user.getId());
            }
            log.debugf("Evaluating triggers for user: %s in realm: %s", user != null ? user.getId() : null, context.getRealm().getId());
            var realm = context.getRealm();
            
            String reviewedInSession = context.getAuthenticationSession().getClientNote(REVIEWED_INVITATIONS);
            if ("true".equals(reviewedInSession)) {
                log.debugf("Skipping invitation review – already reviewed in session.");
                return;
            }
            
            var provider = context.getSession().getProvider(TenantProvider.class);
            if (provider.getTenantInvitationsStream(realm, user).findAny().isPresent()) {
                log.infof("Pending invitations found for user: %s, adding required action: %s", user.getId(), ID);
                user.addRequiredAction(ID);
            } else {
                log.debugf("No pending invitations for user: %s", user.getId());
            }
        } catch (Exception ex) {
            traceError = ex;
            throw ex;
        } finally {
            TracingHelper.finishSpan(span, traceError);
        }
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        Span span = TracingHelper.startServerSpan("review-invitations.challenge");
        Throwable traceError = null;
        try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
            var user = context.getUser();
            if (user != null) {
                span.tag("user.id", user.getId());
            }
            log.debugf("Initiating required action challenge for user: %s in realm: %s",
                       user != null ? user.getId() : null, context.getRealm().getId());
            var realm = context.getRealm();
            var provider = context.getSession().getProvider(TenantProvider.class);

            if (ObjectUtils.isNotEmpty(user.getEmail()) && user.isEmailVerified()) {
                var invitations = provider.getTenantInvitationsStream(realm, user).toList();
                log.debugf("Found %d invitations for user: %s, email: %s",
                           invitations.size(), user.getId(), user.getEmail());
                if (invitations.isEmpty()) {
                    log.infof("No invitations to review for user: %s, marking as success", user.getId());
                    context.success();
                } else {
                    log.infof("Presenting %d invitations to user: %s for review", invitations.size(), user.getId());
                    boolean hasMemberships = provider.getTenantMembershipsStream(realm, user).anyMatch(x -> true);
                    var challenge = context.form()
                            .setAttribute("data", TenantsBean.fromInvitations(invitations))
                            .setAttribute("hasMemberships", hasMemberships)
                            .createForm("review-invitations.ftl");
                    context.challenge(challenge);
                }
            } else {
                log.warnf("User: %s has no email or email not verified, skipping challenge", user.getId());
            }
        } catch (Exception ex) {
            traceError = ex;
            throw ex;
        } finally {
            TracingHelper.finishSpan(span, traceError);
        }
    }

    @Override
    public void processAction(RequiredActionContext context) {
        Span span = TracingHelper.startServerSpan("review-invitations.processAction");
        Throwable traceError = null;
        try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
            var user = context.getUser();
            if (user != null) {
                span.tag("user.id", user.getId());
            }
            log.infof("Processing action for user: %s in realm: %s on button click",
                      user != null ? user.getId() : null, context.getRealm().getId());
            var realm = context.getRealm();
            var provider = context.getSession().getProvider(TenantProvider.class);
            var formData = context.getHttpRequest().getDecodedFormParameters();

            log.debugf("Received form data for user: %s, parameters: %s", user.getId(), formData.toString());
            String acceptedTenantsStr = formData.getFirst(ACCEPTED_TENANTS_ATTR);
            String rejectedTenantsStr = formData.getFirst(REJECTED_TENANTS_ATTR);

            List<String> acceptedTenants = ObjectUtils.isNotEmpty(acceptedTenantsStr)
                    ? Arrays.asList(acceptedTenantsStr.split(","))
                    : Collections.emptyList();
            List<String> rejectedTenants = ObjectUtils.isNotEmpty(rejectedTenantsStr)
                    ? Arrays.asList(rejectedTenantsStr.split(","))
                    : Collections.emptyList();

            log.infof("User: %s - Accepted tenants: %s, Rejected tenants: %s", 
                      user.getId(), acceptedTenants, rejectedTenants);

            boolean hasMemberships = provider.getTenantMembershipsStream(realm, user).findAny().isPresent();
            log.debugf("User: %s has existing memberships: %b", user.getId(), hasMemberships);

            boolean hasUnprocessedInvitations = provider.getTenantInvitationsStream(realm, user)
                    .anyMatch(inv -> !rejectedTenants.contains(inv.getTenant().getId()));
            log.debugf("User: %s has unprocessed invitations: %b", user.getId(), hasUnprocessedInvitations);

            if (!hasMemberships && acceptedTenants.isEmpty() && hasUnprocessedInvitations) {
                var invitations = provider.getTenantInvitationsStream(realm, user).collect(Collectors.toList());
                log.warnf("User: %s has no memberships and no accepted tenants, requiring at least one acceptance", user.getId());
                var challenge = context.form()
                        .setError("You must accept at least one tenant invitation to proceed if you have no existing memberships.")
                        .setAttribute("data", TenantsBean.fromInvitations(invitations))
                        .setAttribute("hasMemberships", hasMemberships)
                        .createForm("review-invitations.ftl");
                context.challenge(challenge);
                return;
            }

            // IDP tenant-scope guard: block acceptance of any tenant outside the identity provider's
            // configured scope BEFORE mutating anything. Granting such a membership would be filtered
            // out by SelectActiveTenant (ACCESS_DENIED) and, once the invitation is revoked, leave the
            // Agent permanently locked out with no recovery path. Throwing here rolls back the whole
            // action, so the invitation is preserved and the Agent can retry via an in-scope IdP.
            for (String tenantId : acceptedTenants) {
                if (isTenantOutsideIdpScope(context, tenantId)) {
                    log.warnf("User: %s attempted to accept invitation to tenant: %s which is outside the IDP tenant scope; blocking without mutating state",
                              user.getId(), tenantId);
                    throw new AuthenticationFlowException(
                            "Invited tenant is outside the identity provider's configured tenant scope",
                            AuthenticationFlowError.ACCESS_DENIED);
                }
            }

            Set<String> allProcessedTenants = new HashSet<>();
            allProcessedTenants.addAll(acceptedTenants);
            allProcessedTenants.addAll(rejectedTenants);
            log.debugf("Processing %d tenants for user: %s - %s",
                       allProcessedTenants.size(), user.getId(), allProcessedTenants);

            for (String tenantId : allProcessedTenants) {
                Optional<TenantInvitationModel> invitation = provider.getTenantInvitationsStream(realm, user)
                        .filter(inv -> inv.getTenant().getId().equals(tenantId))
                        .findFirst();
                if (invitation.isPresent()) {
                    var inv = invitation.get();
                    String tenantName = inv.getTenant().getName();
                    log.debugf("Processing tenant: %s (ID: %s) for user: %s", tenantName, tenantId, user.getId());

                    if (acceptedTenants.contains(tenantId)) {
                        // Update tenant status to Active if not already Active
                        if (!"ACTIVE".equalsIgnoreCase(inv.getTenant().getStatus())) {
                            log.infof("Updating tenant: %s (ID: %s) status to Active for user: %s",
                                      tenantName, tenantId, user.getId());
                            inv.getTenant().setStatus("ACTIVE");
                        }

                        // Already-member guard: skip the grant if the Agent already belongs to this
                        // tenant. grantMembership() persists + flushes a new row against a
                        // (TENANT_ID, USER_ID) unique constraint, so an unconditional grant would throw
                        // a PersistenceException and dead-end the login. This is reachable when a
                        // membership is granted between invitation creation and acceptance (e.g. by
                        // IdpTenantMembershipsCreatingAuthenticator in the same login, or concurrently).
                        // The invitation is still revoked below so it does not linger.
                        if (inv.getTenant().hasMembership(user)) {
                            log.infof("User: %s is already a member of tenant: %s; skipping duplicate grant, will still revoke the invitation",
                                      user.getId(), tenantName);
                        } else {
                            log.infof("Granting membership for user: %s to tenant: %s with roles: %s",
                                      user.getId(), tenantName, inv.getRoles());
                            inv.getTenant().grantMembership(user, inv.getRoles());
                        }
                        if (ObjectUtils.isNotEmpty(inv.getInvitedBy())) {
                            log.debugf("Sending acceptance email for user: %s, tenant: %s, invited by: %s",
                                       user.getId(), tenantName, inv.getInvitedBy());
                            EmailSender.sendInvitationAcceptedEmail(context.getSession(), inv.getInvitedBy(), user.getEmail(), tenantName);
                        }
                    } else if (rejectedTenants.contains(tenantId)) {
                        log.infof("Rejecting invitation for user: %s to tenant: %s", user.getId(), tenantName);
                        if (ObjectUtils.isNotEmpty(inv.getInvitedBy())) {
                            log.debugf("Sending rejection email for user: %s, tenant: %s, invited by: %s",
                                       user.getId(), tenantName, inv.getInvitedBy());
                            EmailSender.sendInvitationDeclinedEmail(context.getSession(), inv.getInvitedBy(), user.getEmail(), tenantName);
                        }
                    }
                    log.debugf("Revoking invitation for user: %s, tenant: %s, invitation ID: %s", 
                               user.getId(), tenantName, inv.getId());
                    inv.getTenant().revokeInvitation(inv.getId());
                } else {
                    log.warnf("No invitation found for tenant ID: %s for user: %s", tenantId, user.getId());
                }
            }

            // Submit async user-service call after all memberships are committed (fire-and-forget — AC-1)
            final String capturedUserId = user.getId();
            final String capturedRealmId = context.getRealm().getId();
            final KeycloakSessionFactory factory = context.getSession().getKeycloakSessionFactory();
            final List<String> finalAccepted = List.copyOf(acceptedTenants);
            final List<String> finalRejected = List.copyOf(rejectedTenants);
            userServiceRestClient.submitAsync(
                    capturedUserId, finalAccepted, finalRejected,
                    factory, capturedRealmId, Constants.USER_SERVICE_MAX_RETRIES, Constants.USER_SERVICE_RETRY_BACKOFF_SECONDS);

            if (provider.getTenantMembershipsStream(realm, user).findAny().isPresent()) {
                log.debugf("User: %s has memberships, removing CreateTenant and adding SelectActiveTenant action", user.getId());
                user.removeRequiredAction(CreateTenant.ID);
                user.removeRequiredAction(ID);
                user.addRequiredAction(SelectActiveTenant.ID);
            } else {
                log.debugf("User: %s has no memberships after processing", user.getId());
            }
            log.infof("Action processing completed successfully for user: %s", user.getId());
            context.getAuthenticationSession().setClientNote(REVIEWED_INVITATIONS, "true");
            context.success();
        } catch (Exception ex) {
            traceError = ex;
            throw ex;
        } finally {
            TracingHelper.finishSpan(span, traceError);
        }
    }

    /**
     * True when the realm's authenticating identity provider is tenant-specific and the given tenant
     * is not in its accessible set. Mirrors the enforcement in {@link SelectActiveTenant} so that an
     * invitation is never accepted into a tenant that {@code SelectActiveTenant} would later reject.
     */
    private boolean isTenantOutsideIdpScope(RequiredActionContext context, String tenantId) {
        Optional<IdentityProviderTenantsConfig> idpConfig = getIdentityProviderTenantsConfig(context);
        if (idpConfig.isPresent() && idpConfig.get().isTenantsSpecific()) {
            return !idpConfig.get().getAccessibleTenantIds().contains(tenantId);
        }
        return false;
    }

    private Optional<IdentityProviderTenantsConfig> getIdentityProviderTenantsConfig(RequiredActionContext context) {
        return getSessionNote(context, Constants.IDENTITY_PROVIDER_SESSION_NOTE)
                .map(context.getSession().identityProviders()::getByAlias)
                .map(IdentityProviderTenantsConfig::of);
    }

    private Optional<String> getSessionNote(RequiredActionContext context, String key) {
        var authSessionNote = Optional.ofNullable(context.getAuthenticationSession().getUserSessionNotes().get(key));
        var userSessionNote = Optional.ofNullable(AuthenticationManager.authenticateIdentityCookie(context.getSession(), context.getRealm(), true))
                .map(authResult -> authResult.getSession().getNote(key));

        return authSessionNote.or(() -> userSessionNote);
    }

    @Override
    public RequiredActionProvider create(KeycloakSession session) {
        return this;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayText() {
        return "Review tenant invitations";
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }
}