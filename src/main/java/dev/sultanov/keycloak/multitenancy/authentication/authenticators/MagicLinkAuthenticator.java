package dev.sultanov.keycloak.multitenancy.authentication.authenticators;

import brave.Span;
import dev.sultanov.keycloak.multitenancy.tracing.TracingHelper;
import jakarta.ws.rs.core.Response;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationProcessor;
import org.keycloak.authentication.Authenticator;
import org.keycloak.common.util.Time;
import org.keycloak.email.EmailException;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.services.Urls;
import org.keycloak.sessions.AuthenticationSessionCompoundId;

import dev.sultanov.keycloak.multitenancy.email.EmailSender;

public class MagicLinkAuthenticator implements Authenticator {

    public static final String MAGIC_LINK_VERIFIED = "magic-link-verified";
    public static final String MAGIC_LINK_LAST_SENT_TS = "magic-link-last-sent-ts";
    public static final String MAGIC_LINK_USER_ID = "magic-link-user-id";

    private static final int EXPIRATION_SECONDS = 900;
    private static final int RESEND_COOLDOWN_SECONDS = 60;

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        Span span = TracingHelper.startServerSpan("magic-link.authenticate");
        Throwable traceError = null;
        try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
            if ("true".equals(context.getAuthenticationSession().getAuthNote(MAGIC_LINK_VERIFIED))) {
                context.success();
                return;
            }

            var formParams = context.getHttpRequest().getDecodedFormParameters();
            var username = formParams.getFirst("username");
            if (username == null || username.isBlank()) {
                var response = context.form()
                        .setAttribute("magicLinkError", "noUsername")
                        .createForm("login-magic-link.ftl");
                context.challenge(response);
                return;
            }

            // Resolve identity the same way normal login does (username, or email when the realm
            // allows login-with-email), respecting realm case/duplicate-email settings.
            var user = KeycloakModelUtils.findUserByNameOrEmail(context.getSession(), context.getRealm(), username.trim());
            if (user == null || !user.isEnabled() || !user.isEmailVerified()) {
                var response = context.form()
                        .setAttribute("magicLinkError", "notVerified")
                        .createForm("login-magic-link.ftl");
                context.challenge(response);
                return;
            }

            span.tag("user.id", user.getId());
            context.getAuthenticationSession().setAuthNote(MAGIC_LINK_USER_ID, user.getId());

            var response = context.form().createForm("login-magic-link.ftl");
            context.challenge(response);
        } catch (Exception ex) {
            traceError = ex;
            throw ex;
        } finally {
            TracingHelper.finishSpan(span, traceError);
        }
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        Span span = TracingHelper.startServerSpan("magic-link.action");
        Throwable traceError = null;
        try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
            if ("true".equals(context.getAuthenticationSession().getAuthNote(MAGIC_LINK_VERIFIED))) {
                context.success();
                return;
            }

            var formData = context.getHttpRequest().getDecodedFormParameters();
            var action = formData.getFirst("action");

            if ("tryAnotherWay".equals(action)) {
                context.attempted();
                return;
            }

            if ("check".equals(action)) {
                // MAGIC_LINK_VERIFIED fast-path (lines above) already called context.success()
                // if verified. Reaching here means verification is still pending — tell the
                // polling client to keep waiting.
                context.challenge(Response.ok("{\"verified\":false}", "application/json").build());
                return;
            }

            if (!"send".equals(action) && !"resend".equals(action)) {
                // Unknown or missing action — re-render the form rather than falling through
                // with no challenge/success (which would yield a blank page / processor error).
                var response = context.form().createForm("login-magic-link.ftl");
                context.challenge(response);
                return;
            }

            // Rate-limit check
            String lastSentStr = context.getAuthenticationSession().getAuthNote(MAGIC_LINK_LAST_SENT_TS);
            if (lastSentStr != null) {
                try {
                    long lastSentTs = Long.parseLong(lastSentStr);
                    long nowTs = Time.currentTime();
                    if (nowTs - lastSentTs < RESEND_COOLDOWN_SECONDS) {
                        var response = context.form()
                                .setAttribute("magicLinkSent", "true")
                                .setAttribute("magicLinkError", "rateLimited")
                                .createForm("login-magic-link.ftl");
                        context.challenge(response);
                        return;
                    }
                } catch (NumberFormatException ignored2) { /* treat as no-op */ }
            }

            // If the form re-submits a (corrected) username, re-resolve so the link is sent to the
            // currently-entered identity rather than a stale id bound on the first step.
            var submittedUsername = formData.getFirst("username");
            if (submittedUsername != null && !submittedUsername.isBlank()) {
                var resolved = KeycloakModelUtils.findUserByNameOrEmail(context.getSession(), context.getRealm(), submittedUsername.trim());
                if (resolved != null) {
                    context.getAuthenticationSession().setAuthNote(MAGIC_LINK_USER_ID, resolved.getId());
                } else {
                    // Submitted username does not resolve — do not silently fall back to the stale
                    // id (which would email a different account than the field shows).
                    var response = context.form()
                            .setAttribute("magicLinkError", "notVerified")
                            .createForm("login-magic-link.ftl");
                    context.challenge(response);
                    return;
                }
            }

            var userId = context.getAuthenticationSession().getAuthNote(MAGIC_LINK_USER_ID);
            // Guard against a send/resend POST that arrives without a prior successful username
            // step (stale/forged form): getUserById(realm, null) is provider-dependent.
            var user = userId == null ? null : context.getSession().users().getUserById(context.getRealm(), userId);
            if (user == null || !user.isEnabled() || !user.isEmailVerified()) {
                var response = context.form()
                        .setAttribute("magicLinkError", "notVerified")
                        .createForm("login-magic-link.ftl");
                context.challenge(response);
                return;
            }

            if (user.getEmail() == null || user.getEmail().isBlank()) {
                var response = context.form()
                        .setAttribute("magicLinkError", "notVerified")
                        .createForm("login-magic-link.ftl");
                context.challenge(response);
                return;
            }

            span.tag("user.id", userId);

            var session = context.getSession();
            var realm = context.getRealm();
            var uriInfo = session.getContext().getUri();
            var authSession = context.getAuthenticationSession();

            String compoundId = AuthenticationSessionCompoundId.fromAuthSession(authSession).getEncodedId();
            // DefaultActionToken expects an ABSOLUTE expiry (epoch second), not a relative TTL —
            // every built-in caller passes Time.currentTime() + lifespan. Passing the bare TTL
            // mints a token that is already expired (exp = 900 = 1970-01-01T00:15Z).
            var token = new MagicLinkActionToken(userId, Time.currentTime() + EXPIRATION_SECONDS, compoundId);
            String serialized = token.serialize(session, realm, uriInfo);

            String actionTokenUrl = Urls.actionTokenBuilder(
                    uriInfo.getBaseUri(),
                    serialized,
                    authSession.getClient().getClientId(),
                    authSession.getTabId(),
                    AuthenticationProcessor.getClientData(session, authSession)
            ).build(realm.getName()).toString();

            try {
                EmailSender.sendMagicLinkEmail(session, user, actionTokenUrl);
            } catch (EmailException ee) {
                // Delivery failed — do NOT claim "sent" and do NOT start the resend cooldown.
                span.tag("error", "email.send.failed");
                var response = context.form()
                        .setAttribute("magicLinkError", "sendFailed")
                        .createForm("login-magic-link.ftl");
                context.challenge(response);
                return;
            }

            context.getAuthenticationSession().setAuthNote(MAGIC_LINK_LAST_SENT_TS,
                    Long.toString(Time.currentTime()));

            var response = context.form()
                    .setAttribute("magicLinkSent", "true")
                    .createForm("login-magic-link.ftl");
            context.challenge(response);
        } catch (Exception ex) {
            traceError = ex;
            throw ex;
        } finally {
            TracingHelper.finishSpan(span, traceError);
        }
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    }

    @Override
    public void close() {
    }
}
