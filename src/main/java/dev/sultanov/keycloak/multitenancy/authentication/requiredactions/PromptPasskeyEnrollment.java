package dev.sultanov.keycloak.multitenancy.authentication.requiredactions;

import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import lombok.extern.jbosslog.JBossLog;
import brave.Span;
import dev.sultanov.keycloak.multitenancy.tracing.TracingHelper;

@JBossLog
public class PromptPasskeyEnrollment implements RequiredActionProvider, RequiredActionFactory {

    public static final String ID = "prompt-passkey-enrollment";

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
            String action = formData.getFirst("action");
            log.infof("Processing passkey enrollment action '%s' for user: %s", action, user.getId());

            if ("enroll".equals(action)) {
                log.infof("User %s chose to enroll a passkey — routing to webauthn-register-passwordless", user.getId());
                user.addRequiredAction("webauthn-register-passwordless");
                context.success();
            } else {
                // "dismiss" or any other/null value — per-session dismiss (OQ-8 Option A)
                log.debugf("User %s dismissed the enrollment prompt — proceeding without passkey", user.getId());
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
