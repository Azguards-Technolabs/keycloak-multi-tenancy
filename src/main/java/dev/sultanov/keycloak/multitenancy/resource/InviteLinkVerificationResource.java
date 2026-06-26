package dev.sultanov.keycloak.multitenancy.resource;

import brave.Span;
import dev.sultanov.keycloak.multitenancy.model.TenantInvitationModel;
import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import dev.sultanov.keycloak.multitenancy.tracing.TracingHelper;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.Optional;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.Urls;

public class InviteLinkVerificationResource {

    private static final Logger log = Logger.getLogger(InviteLinkVerificationResource.class);

    private static final String ERROR_HTML =
            "<!DOCTYPE html>\n"
            + "<html lang=\"en\">\n"
            + "<head><meta charset=\"UTF-8\"><title>Invite Link Invalid</title></head>\n"
            + "<body style=\"font-family:system-ui,sans-serif;max-width:480px;margin:4rem auto;padding:1rem\">\n"
            + "  <h1 style=\"font-size:1.25rem\">This invite link is no longer valid</h1>\n"
            + "  <p>The link may have already been used or has expired.</p>\n"
            + "  <p>Contact your administrator to request a new invitation.</p>\n"
            + "</body>\n"
            + "</html>\n";

    private final KeycloakSession session;

    public InviteLinkVerificationResource(KeycloakSession session) {
        this.session = session;
    }

    @GET
    public Response verify(@QueryParam("token") String token) {
        Span span = TracingHelper.startServerSpan("invite-verify.verify");
        Throwable traceError = null;
        try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
            RealmModel realm = session.getContext().getRealm();

            if (token == null || token.isBlank()) {
                log.warnf("invite-verify called with missing token");
                return errorResponse();
            }

            TenantProvider tenantProvider = session.getProvider(TenantProvider.class);
            Optional<TenantInvitationModel> invitationOpt = tenantProvider.findInvitationById(realm, token);

            if (invitationOpt.isEmpty()) {
                // Do not log the raw token — it is a bearer credential for this action.
                log.warnf("invite-verify: no active invitation for the provided token");
                return errorResponse();
            }

            TenantInvitationModel invitation = invitationOpt.get();
            // Intentionally NOT tagging the span with the token/invitation id — it is a bearer
            // credential and would persist in tracing backends. user.id is tagged below instead.

            UserModel user = session.users().getUserByEmail(realm, invitation.getEmail());
            if (user == null) {
                log.warnf("invite-verify: no Keycloak user for email %s", invitation.getEmail());
                return errorResponse();
            }

            span.tag("user.id", user.getId());
            if (user.isEmailVerified()) {
                // Idempotent replay: the link was already used / the email is already verified.
                // Skip the mutation but still redirect so the agent proceeds into the login flow.
                span.tag("invite-verify.replay", "true");
                log.infof("invite-verify: user %s already emailVerified, skipping mutation (idempotent replay)", user.getId());
            } else {
                user.setEmailVerified(true);
                log.infof("invite-verify: set emailVerified=true for user %s", user.getId());
            }

            URI loginUri = Urls.accountBase(session.getContext().getUri().getBaseUri())
                    .build(realm.getName());
            return Response.seeOther(loginUri).build();

        } catch (Exception ex) {
            traceError = ex;
            // Return the calm error page rather than a raw 500/blank page (consistent with the decline flow).
            log.warnf(ex, "invite-verify: unexpected error — returning calm error page");
            return errorResponse();
        } finally {
            TracingHelper.finishSpan(span, traceError);
        }
    }

    private static Response errorResponse() {
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.TEXT_HTML)
                .entity(ERROR_HTML)
                .build();
    }
}
