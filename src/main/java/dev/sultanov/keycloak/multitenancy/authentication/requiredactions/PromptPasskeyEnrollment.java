package dev.sultanov.keycloak.multitenancy.authentication.requiredactions;

import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.Constants;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;
import lombok.extern.jbosslog.JBossLog;
import brave.Span;
import dev.sultanov.keycloak.multitenancy.tracing.TracingHelper;

@JBossLog
public class PromptPasskeyEnrollment implements RequiredActionProvider, RequiredActionFactory {

    public static final String ID = "prompt-passkey-enrollment";
    /** Per-login choice — client note survives required-action transitions (see ReviewTenantInvitations). */
    private static final String ENROLLMENT_CHOICE_NOTE = "passkey-enrollment-choice";
    /** Form field — avoid name="action" (collides with KC required-action URL handling). */
    static final String ENROLLMENT_CHOICE_PARAM = "enrollmentChoice";
    private static final String WEBAUTHN_REGISTER_PASSWORDLESS = "webauthn-register-passwordless";

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        Span span = TracingHelper.startServerSpan("prompt-passkey-enrollment.evaluateTriggers");
        Throwable traceError = null;
        try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
            var actionProvider = context.getRealm().getRequiredActionProviderByAlias(ID);
            if (actionProvider == null || !actionProvider.isEnabled()) {
                log.debugf("prompt-passkey-enrollment is not enabled in the realm — skipping evaluateTriggers");
                return;
            }

            var user = context.getUser();
            if (user == null) {
                log.warnf("No user in context — skipping passkey enrollment evaluation");
                return;
            }
            span.tag("user.id", user.getId());

            var authSession = context.getAuthenticationSession();
            var choice = getEnrollmentChoice(authSession);
            if ("dismiss".equals(choice)) {
                log.debugf("User %s already dismissed passkey enrollment this login — clearing queues", user.getId());
                clearPasskeyEnrollmentFromQueues(user, authSession);
                return;
            }
            if ("enroll".equals(choice)) {
                log.debugf("User %s already chose enroll this login — not re-queuing prompt", user.getId());
                removePromptFromQueues(user, authSession);
                return;
            }

            boolean hasPasskey = user.credentialManager()
                    .getStoredCredentialsByTypeStream("webauthn-passwordless")
                    .findAny()
                    .isPresent();
            if (hasPasskey) {
                log.debugf("User %s already has a passkey — skipping enrollment prompt", user.getId());
                removePromptFromQueues(user, authSession);
                return;
            }

            if (isPromptQueued(user, authSession)) {
                log.debugf("User %s already has %s queued — skipping duplicate add", user.getId(), ID);
                return;
            }

            log.infof("User %s has no passkey — adding required action: %s", user.getId(), ID);
            user.addRequiredAction(ID);
        } catch (Exception ex) {
            traceError = ex;
            throw ex;
        } finally {
            TracingHelper.finishSpan(span, traceError);
        }
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        Span span = TracingHelper.startServerSpan("prompt-passkey-enrollment.challenge");
        Throwable traceError = null;
        try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
            var user = context.getUser();
            if (user != null) {
                span.tag("user.id", user.getId());
            }
            var authSession = context.getAuthenticationSession();

            // If dismiss was already processed but KC still invoked this action, advance without re-rendering.
            if ("dismiss".equals(getEnrollmentChoice(authSession))) {
                log.infof("Passkey enrollment already dismissed for user %s — skipping challenge",
                        user != null ? user.getId() : null);
                clearPasskeyEnrollmentFromQueues(user, authSession);
                context.success();
                return;
            }

            log.debugf("Presenting passkey enrollment prompt to user: %s", user != null ? user.getId() : null);
            context.challenge(context.form().createForm("passkey-enrollment-prompt.ftl"));
        } catch (Exception ex) {
            traceError = ex;
            throw ex;
        } finally {
            TracingHelper.finishSpan(span, traceError);
        }
    }

    @Override
    public void processAction(RequiredActionContext context) {
        Span span = TracingHelper.startServerSpan("prompt-passkey-enrollment.processAction");
        Throwable traceError = null;
        try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
            var user = context.getUser();
            if (user == null) {
                log.warnf("No user in context — completing passkey enrollment without action");
                context.success();
                return;
            }
            span.tag("user.id", user.getId());

            var authSession = context.getAuthenticationSession();
            var existingChoice = getEnrollmentChoice(authSession);
            if ("dismiss".equals(existingChoice)) {
                log.debugf("Passkey enrollment already dismissed for user %s — ignoring duplicate submit", user.getId());
                clearPasskeyEnrollmentFromQueues(user, authSession);
                context.success();
                return;
            }
            if ("enroll".equals(existingChoice)) {
                log.debugf("Passkey enrollment already accepted for user %s — ignoring duplicate submit", user.getId());
                removePromptFromQueues(user, authSession);
                context.success();
                return;
            }

            var formData = context.getHttpRequest().getDecodedFormParameters();
            String choice = formData.getFirst(ENROLLMENT_CHOICE_PARAM);
            if (choice == null || choice.isBlank()) {
                choice = formData.getFirst("action");
            }
            log.infof("Processing passkey enrollment choice '%s' for user: %s", choice, user.getId());

            if ("enroll".equals(choice)) {
                log.infof("User %s chose to enroll a passkey — routing to webauthn-register-passwordless", user.getId());
                setEnrollmentChoice(authSession, "enroll");
                authSession.setClientNote(Constants.KC_ACTION, WEBAUTHN_REGISTER_PASSWORDLESS);
                authSession.setClientNote(Constants.KC_ACTION_EXECUTING, WEBAUTHN_REGISTER_PASSWORDLESS);
                authSession.removeClientNote(Constants.KC_ACTION_ENFORCED);
                authSession.addRequiredAction(WEBAUTHN_REGISTER_PASSWORDLESS);
                removePromptFromQueues(user, authSession);
                context.success();
            } else {
                log.infof("User %s dismissed the enrollment prompt — proceeding without passkey", user.getId());
                setEnrollmentChoice(authSession, "dismiss");
                clearKcActionClientNotes(authSession);
                clearPasskeyEnrollmentFromQueues(user, authSession);
                context.success();
            }
        } catch (Exception ex) {
            traceError = ex;
            throw ex;
        } finally {
            TracingHelper.finishSpan(span, traceError);
        }
    }

    private static String getEnrollmentChoice(AuthenticationSessionModel authSession) {
        String choice = authSession.getClientNote(ENROLLMENT_CHOICE_NOTE);
        if (choice == null || choice.isBlank()) {
            choice = authSession.getAuthNote(ENROLLMENT_CHOICE_NOTE);
        }
        return choice;
    }

    private static void setEnrollmentChoice(AuthenticationSessionModel authSession, String choice) {
        authSession.setClientNote(ENROLLMENT_CHOICE_NOTE, choice);
        authSession.setAuthNote(ENROLLMENT_CHOICE_NOTE, choice);
    }

    private static boolean isPromptQueued(UserModel user, AuthenticationSessionModel authSession) {
        return user.getRequiredActionsStream().anyMatch(ID::equals)
                || authSession.getRequiredActions().contains(ID);
    }

    private static void removePromptFromQueues(UserModel user, AuthenticationSessionModel authSession) {
        authSession.removeRequiredAction(ID);
        user.removeRequiredAction(ID);
    }

    private static void clearPasskeyEnrollmentFromQueues(UserModel user, AuthenticationSessionModel authSession) {
        authSession.removeRequiredAction(ID);
        authSession.removeRequiredAction(WEBAUTHN_REGISTER_PASSWORDLESS);
        if (user != null) {
            user.removeRequiredAction(ID);
            user.removeRequiredAction(WEBAUTHN_REGISTER_PASSWORDLESS);
        }
    }

    private static void clearKcActionClientNotes(AuthenticationSessionModel authSession) {
        authSession.removeClientNote(Constants.KC_ACTION);
        authSession.removeClientNote(Constants.KC_ACTION_EXECUTING);
        authSession.removeClientNote(Constants.KC_ACTION_ENFORCED);
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
        return "Prompt passkey enrollment";
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
