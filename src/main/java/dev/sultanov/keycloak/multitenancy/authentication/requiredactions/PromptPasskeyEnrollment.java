package dev.sultanov.keycloak.multitenancy.authentication.requiredactions;

import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.Constants;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import lombok.extern.jbosslog.JBossLog;
import brave.Span;
import dev.sultanov.keycloak.multitenancy.tracing.TracingHelper;

@JBossLog
public class PromptPasskeyEnrollment implements RequiredActionProvider, RequiredActionFactory {

    public static final String ID = "prompt-passkey-enrollment";
    /** Per-login choice stored on the auth session. */
    private static final String ENROLLMENT_CHOICE_NOTE = "passkey-enrollment-choice";
    /** Form field — avoid name="action" (collides with KC required-action URL handling). */
    static final String ENROLLMENT_CHOICE_PARAM = "enrollmentChoice";
    private static final String WEBAUTHN_REGISTER_PASSWORDLESS = "webauthn-register-passwordless";

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        Span span = TracingHelper.startServerSpan("prompt-passkey-enrollment.evaluateTriggers");
        Throwable traceError = null;
        try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
            // Only add the required action when it is enabled in the realm — this is the standard
            // Keycloak pattern. Without this guard, evaluateTriggers() would add the action to every
            // user who logs in, even when the feature is intentionally disabled, which causes the
            // direct-grant (password) flow to return "Account is not fully set up".
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
            log.debugf("Evaluating triggers for user: %s", user.getId());

            // Passkeys are stored under the WebAuthn *passwordless* credential type, not "webauthn".
            boolean hasPasskey = user.credentialManager()
                    .getStoredCredentialsByTypeStream("webauthn-passwordless")
                    .findAny()
                    .isPresent();

            if (hasPasskey) {
                log.debugf("User %s already has a passkey — skipping enrollment prompt", user.getId());
                return;
            }

            var authSession = context.getAuthenticationSession();
            var choice = authSession.getAuthNote(ENROLLMENT_CHOICE_NOTE);
            if ("enroll".equals(choice) || "dismiss".equals(choice)) {
                log.debugf("User %s already chose '%s' this login — skipping enrollment prompt", user.getId(), choice);
                return;
            }

            boolean alreadyQueued = user.getRequiredActionsStream()
                    .anyMatch(a -> ID.equals(a));
            if (alreadyQueued) {
                log.debugf("User %s already has prompt-passkey-enrollment queued — skipping duplicate add", user.getId());
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
            log.debugf("Presenting passkey enrollment prompt to user: %s", user != null ? user.getId() : null);
            var challenge = context.form()
                    .createForm("passkey-enrollment-prompt.ftl");
            context.challenge(challenge);
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
            var formData = context.getHttpRequest().getDecodedFormParameters();
            String choice = formData.getFirst(ENROLLMENT_CHOICE_PARAM);
            log.infof("Processing passkey enrollment choice '%s' for user: %s", choice, user.getId());

            var authSession = context.getAuthenticationSession();
            if ("enroll".equals(choice)) {
                log.infof("User %s chose to enroll a passkey — routing to webauthn-register-passwordless", user.getId());
                authSession.setAuthNote(ENROLLMENT_CHOICE_NOTE, "enroll");
                // Treat as optional (skippable) app-initiated registration — allows "Not now" on
                // webauthn-register.ftl via cancel-aia without persisting a mandatory user action.
                authSession.setClientNote(Constants.KC_ACTION, WEBAUTHN_REGISTER_PASSWORDLESS);
                authSession.setClientNote(Constants.KC_ACTION_EXECUTING, WEBAUTHN_REGISTER_PASSWORDLESS);
                authSession.removeClientNote(Constants.KC_ACTION_ENFORCED);
                authSession.addRequiredAction(WEBAUTHN_REGISTER_PASSWORDLESS);
                user.removeRequiredAction(WEBAUTHN_REGISTER_PASSWORDLESS);
                context.success();
            } else {
                log.debugf("User %s dismissed the enrollment prompt — proceeding without passkey", user.getId());
                authSession.setAuthNote(ENROLLMENT_CHOICE_NOTE, "dismiss");
                authSession.removeClientNote(Constants.KC_ACTION);
                authSession.removeClientNote(Constants.KC_ACTION_EXECUTING);
                authSession.removeClientNote(Constants.KC_ACTION_ENFORCED);
                user.removeRequiredAction(WEBAUTHN_REGISTER_PASSWORDLESS);
                user.removeRequiredAction(ID);
                context.success();
            }
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
