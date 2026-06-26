package dev.sultanov.keycloak.multitenancy.authentication.authenticators;

import brave.Span;
import dev.sultanov.keycloak.multitenancy.tracing.TracingHelper;
import jakarta.ws.rs.core.Response;
import org.keycloak.authentication.actiontoken.AbstractActionTokenHandler;
import org.keycloak.authentication.actiontoken.ActionTokenContext;
import org.keycloak.events.Errors;
import org.keycloak.events.EventType;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.services.messages.Messages;

public class MagicLinkActionTokenHandler extends AbstractActionTokenHandler<MagicLinkActionToken> {

    public MagicLinkActionTokenHandler() {
        super(
                MagicLinkActionToken.TOKEN_TYPE,
                MagicLinkActionToken.class,
                Messages.INVALID_PARAMETER,
                EventType.LOGIN,
                Errors.INVALID_TOKEN
        );
    }

    @Override
    public Response handleToken(MagicLinkActionToken token, ActionTokenContext<MagicLinkActionToken> context) {
        Span span = TracingHelper.startServerSpan("magic-link.handleToken");
        Throwable traceError = null;
        try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
            var session = context.getSession();
            var realm = context.getRealm();
            var user = session.users().getUserById(realm, token.getUserId());
            if (user == null || !user.isEnabled() || !user.isEmailVerified()) {
                String error = user == null ? Errors.USER_NOT_FOUND : Errors.USER_DISABLED;
                span.tag("error", user == null ? "user.not.found" : "user.disabled");
                context.getEvent().event(EventType.LOGIN).detail("auth_method", "magic-link").error(error);
                // Render a proper error page rather than a bare 400 (calm dead-end avoidance, AC#4).
                return errorPage(context);
            }
            span.tag("user.id", token.getUserId());
            var authSession = context.getAuthenticationSession();
            if (authSession == null) {
                // Without an auth session we cannot record verification, so the originating tab's
                // poll would never complete. Surface an error instead of a false "verified" page.
                span.tag("error", "auth.session.missing");
                context.getEvent().event(EventType.LOGIN).detail("auth_method", "magic-link").error(Errors.SESSION_EXPIRED);
                return errorPage(context);
            }
            authSession.setAuthNote(MagicLinkAuthenticator.MAGIC_LINK_VERIFIED, "true");
            authSession.setAuthenticatedUser(user);
            // Do NOT call processFlow here. The originating tab owns the auth session and must
            // complete the flow — its polling loop detects MAGIC_LINK_VERIFIED and calls
            // context.success(). Calling processFlow here would race with (and consume) that
            // session before Device A can finish. Instead show Device B a calm "link verified"
            // info page so they know to return to their original browser.
            return infoPage(context, "magicLinkVerifiedReturn");
        } catch (Exception ex) {
            traceError = ex;
            throw ex;
        } finally {
            TracingHelper.finishSpan(span, traceError);
        }
    }

    @Override
    public boolean canUseTokenRepeatedly(MagicLinkActionToken token, ActionTokenContext<MagicLinkActionToken> context) {
        return false;
    }

    private Response errorPage(ActionTokenContext<MagicLinkActionToken> context) {
        LoginFormsProvider forms = context.getSession().getProvider(LoginFormsProvider.class);
        if (context.getAuthenticationSession() != null) {
            forms.setAuthenticationSession(context.getAuthenticationSession());
        }
        return forms.setError(Messages.INVALID_REQUEST).createErrorPage(Response.Status.BAD_REQUEST);
    }

    private Response infoPage(ActionTokenContext<MagicLinkActionToken> context, String messageKey) {
        LoginFormsProvider forms = context.getSession().getProvider(LoginFormsProvider.class);
        if (context.getAuthenticationSession() != null) {
            forms.setAuthenticationSession(context.getAuthenticationSession());
        }
        return forms.setInfo(messageKey).createInfoPage();
    }
}
