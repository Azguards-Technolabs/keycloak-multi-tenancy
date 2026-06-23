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
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
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
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.AuthenticationFlowException;

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
                } else if (invitations.size() == 1) {
                    log.infof("Single invitation found for user: %s, auto-accepting", user.getId());
                    var inv = invitations.get(0);
                    autoAcceptInvitation(context, inv);
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

    private void autoAcceptInvitation(RequiredActionContext context, TenantInvitationModel inv) {
        var user = context.getUser();

        // Guard: the invitation's tenant may have been concurrently deleted. Do not halt login
        // with an NPE — proceed without auto-accepting.
        if (inv.getTenant() == null) {
            log.warnf("Auto-accept skipped: invitation %s has no tenant for user %s", inv.getId(), user.getId());
            context.getAuthenticationSession().setClientNote(REVIEWED_INVITATIONS, "true");
            context.success();
            return;
        }
        var tenantId = inv.getTenant().getId();
        var realm = context.getRealm();
        var provider = context.getSession().getProvider(TenantProvider.class);

        // D4: respect Identity-Provider tenant scoping. Granting a membership for a tenant outside the
        // user's IDP scope would later dead-end in SelectActiveTenant (ACCESS_DENIED). Skip instead.
        if (isTenantOutsideIdpScope(context, tenantId)) {
            log.warnf("Auto-accept skipped: tenant %s is outside the IDP scope for user %s — denying access",
                      tenantId, user.getId());
            // D2: deny access without mutating any state. We intentionally do NOT revoke the invitation:
            // (1) revoking before throwing risks the mutation being rolled back with the thrown flow
            //     exception, leaving inconsistent state; (2) the invitation is legitimate and must survive
            //     so the user can accept it via an in-scope identity provider later. Denying on every login
            //     is the correct enforcement while the tenant remains out of scope.
            throw new AuthenticationFlowException(
                    "User does not have access to the invited tenant under the current identity provider",
                    AuthenticationFlowError.ACCESS_DENIED);
        }

        // P9: if the user is already a member of this tenant, do not re-grant (would risk a duplicate
        // membership / unique-constraint violation). Just clean up the stale invitation and proceed.
        boolean alreadyMember = provider.getTenantMembershipsStream(realm, user)
                .anyMatch(m -> m.getTenant() != null && tenantId.equals(m.getTenant().getId()));

        Span span = TracingHelper.startServerSpan("review-invitations.auto-accept");
        Throwable traceError = null;
        try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
            span.tag("user.id", user.getId());
            span.tag("tenant.id", tenantId);
            span.tag("already_member", String.valueOf(alreadyMember));

            // Task 3.3: activate tenant if not already ACTIVE
            if (!"ACTIVE".equalsIgnoreCase(inv.getTenant().getStatus())) {
                inv.getTenant().setStatus("ACTIVE");
            }

            // Task 3.4: grant membership (skip if already a member — see P9 guard above)
            if (!alreadyMember) {
                inv.getTenant().grantMembership(user, inv.getRoles());
                log.infof("Granted membership for user: %s to tenant: %s", user.getId(), tenantId);
            } else {
                log.infof("User: %s already a member of tenant: %s — skipping grant", user.getId(), tenantId);
            }

            // Task 3.5: send acceptance email if invited by someone. Non-blocking: an SMTP failure
            // must not abort login after membership is already granted (AC-4) — log and continue.
            if (inv.getInvitedBy() != null) {
                try {
                    EmailSender.sendInvitationAcceptedEmail(
                            context.getSession(), inv.getInvitedBy(), user.getEmail(), inv.getTenant().getName());
                } catch (Exception e) {
                    log.warnf(e, "Failed to send acceptance email for user: %s, tenant: %s — continuing", user.getId(), tenantId);
                }
            }

            // Task 3.6: revoke invitation
            inv.getTenant().revokeInvitation(inv.getId());
            log.debugf("Revoked invitation: %s for user: %s", inv.getId(), user.getId());

            // Submit async user-service call (fire-and-forget; does not gate login — AC-1)
            final String capturedUserId = user.getId();
            final String capturedRealmId = context.getRealm().getId();
            final KeycloakSessionFactory factory = context.getSession().getKeycloakSessionFactory();
            userServiceRestClient.submitAsync(
                    capturedUserId, List.of(tenantId), List.of(),
                    factory, capturedRealmId, Constants.USER_SERVICE_MAX_RETRIES, Constants.USER_SERVICE_RETRY_BACKOFF_SECONDS);

            // Task 3.7: update required actions
            user.removeRequiredAction(CreateTenant.ID);
            user.removeRequiredAction(ID);
            user.addRequiredAction(SelectActiveTenant.ID);

            // Task 3.8: set reviewed client note
            context.getAuthenticationSession().setClientNote(REVIEWED_INVITATIONS, "true");

            // Toast notes set unconditionally (AC-7) — async outcome is orthogonal to toast display.
            // P5: Only set when tenant name is present to keep the toast all-or-nothing.
            String tenantName = inv.getTenant().getName();
            if (tenantName != null && !tenantName.isBlank()) {
                String inviterName = computeInviterName(inv.getInvitedBy());
                context.getAuthenticationSession().setUserSessionNote(
                        Constants.TOAST_INVITER_NAME_NOTE, inviterName);
                context.getAuthenticationSession().setUserSessionNote(
                        Constants.TOAST_TENANT_NAME_NOTE, tenantName);
                context.getAuthenticationSession().setUserSessionNote(
                        Constants.TOAST_TENANT_ID_NOTE, tenantId);
                log.debugf("Toast session notes set for user: %s, inviterName: %s, tenantName: %s",
                           user.getId(), inviterName, tenantName);
            } else {
                log.warnf("Skipping toast for user: %s — tenant name unavailable for tenant: %s",
                          user.getId(), tenantId);
            }

            context.success();
        } catch (Exception ex) {
            traceError = ex;
            // D1: never dead-end login with an error page on an unexpected failure (AC-4). Skip
            // auto-accept, mark reviewed so it does not loop this session, and let login continue.
            log.errorf(ex, "Auto-accept failed unexpectedly for user: %s, tenant: %s — proceeding without auto-accept",
                       user.getId(), tenantId);
            context.getAuthenticationSession().setClientNote(REVIEWED_INVITATIONS, "true");
            context.success();
        } finally {
            TracingHelper.finishSpan(span, traceError);
        }
    }

    private String computeInviterName(org.keycloak.models.UserModel invitedBy) {
        if (invitedBy == null) {
            return "Someone";
        }
        String firstName = invitedBy.getFirstName();
        String lastName = invitedBy.getLastName();
        if (firstName != null) firstName = firstName.trim();
        if (lastName != null) lastName = lastName.trim();
        boolean hasFirst = firstName != null && !firstName.isEmpty();
        boolean hasLast = lastName != null && !lastName.isEmpty();
        if (hasFirst || hasLast) {
            return (hasFirst ? firstName : "") + (hasFirst && hasLast ? " " : "") + (hasLast ? lastName : "");
        }
        String username = invitedBy.getUsername();
        return (username != null && !username.isBlank()) ? username : "Someone";
    }

    /**
     * D4: returns true when the user authenticated via an Identity Provider configured as
     * tenant-specific and the given tenant is NOT in that IDP's accessible tenant set. Mirrors the
     * filtering performed by {@link SelectActiveTenant} so auto-accept never grants a membership that
     * the tenant-selection step would later reject with ACCESS_DENIED.
     */
    private boolean isTenantOutsideIdpScope(RequiredActionContext context, String tenantId) {
        Optional<IdentityProviderTenantsConfig> idpConfig =
                getSessionNote(context, Constants.IDENTITY_PROVIDER_SESSION_NOTE)
                        .map(context.getSession().identityProviders()::getByAlias)
                        .map(IdentityProviderTenantsConfig::of);
        if (idpConfig.isPresent() && idpConfig.get().isTenantsSpecific()) {
            return !idpConfig.get().getAccessibleTenantIds().contains(tenantId);
        }
        return false;
    }

    private Optional<String> getSessionNote(RequiredActionContext context, String key) {
        var authSessionNote = Optional.ofNullable(context.getAuthenticationSession().getUserSessionNotes().get(key));
        var userSessionNote = Optional.ofNullable(
                        AuthenticationManager.authenticateIdentityCookie(context.getSession(), context.getRealm(), true))
                .map(authResult -> authResult.getSession().getNote(key));
        return authSessionNote.or(() -> userSessionNote);
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

                        log.infof("Granting membership for user: %s to tenant: %s with roles: %s",
                                  user.getId(), tenantName, inv.getRoles());
                        inv.getTenant().grantMembership(user, inv.getRoles());
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