package dev.sultanov.keycloak.multitenancy.resource;

import brave.Span;
import dev.sultanov.keycloak.multitenancy.model.TenantMembershipModel;
import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import dev.sultanov.keycloak.multitenancy.tracing.TracingHelper;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.services.Urls;
import org.keycloak.services.managers.AuthenticationManager;

public class InviteDeclineResource {

    private static final Logger log = Logger.getLogger(InviteDeclineResource.class.getName());

    // Per-user-session note holding the one-time CSRF token for the decline confirmation form.
    private static final String DECLINE_CSRF_NOTE = "invite.decline.csrf";

    private final KeycloakSession session;

    public InviteDeclineResource(KeycloakSession session) {
        this.session = session;
    }

    /**
     * Step 1 (safe GET): render a confirmation page with a CSRF-protected POST form.
     * <p>
     * The actual membership revocation + logout is a state change and must NOT happen on GET
     * (a plain {@code <img src=...>} or prefetch could otherwise trigger it via CSRF). This GET
     * is side-effect free apart from minting a one-time CSRF token bound to the user session.
     */
    @GET
    public Response declineForm(@QueryParam("tenantId") String tenantId) {
        Span span = TracingHelper.startServerSpan("invite-decline.form");
        Throwable traceError = null;
        try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
            RealmModel realm = session.getContext().getRealm();
            URI loginUri = Urls.realmLoginPage(session.getContext().getUri().getBaseUri(), realm.getName());

            var authResult = AuthenticationManager.authenticateIdentityCookie(session, realm, true);
            if (authResult == null) {
                log.warning("invite-decline: no active session, redirecting to login");
                return Response.seeOther(loginUri).build();
            }

            UserModel user = authResult.getUser();
            span.tag("user.id", user.getId());

            if (tenantId == null || tenantId.isBlank()) {
                log.warning("invite-decline: tenantId param missing for user: " + user.getId());
                return calmErrorResponse(loginUri);
            }
            span.tag("tenant.id", tenantId);

            Optional<TenantMembershipModel> membershipOpt = findMembership(realm, user, tenantId);
            if (membershipOpt.isEmpty()) {
                // Not a member (or already declined). Don't show an alarming error — send them to login.
                log.warning("invite-decline: user " + user.getId() + " has no membership in tenant " + tenantId
                        + " — redirecting to login");
                return Response.seeOther(loginUri).build();
            }

            // Mint a one-time CSRF token bound to the user session.
            String csrf = KeycloakModelUtils.generateId();
            authResult.getSession().setNote(DECLINE_CSRF_NOTE, csrf);

            String tenantName = membershipOpt.get().getTenant().getName();
            return confirmPageResponse(tenantId, csrf, tenantName, loginUri);

        } catch (Exception ex) {
            traceError = ex;
            log.severe("invite-decline: unexpected error rendering form — " + ex.getMessage());
            return calmErrorResponse(loginUriQuietly());
        } finally {
            TracingHelper.finishSpan(span, traceError);
        }
    }

    /**
     * Step 2 (POST): perform the decline. Requires a valid CSRF token from the confirmation form,
     * which defeats cross-site request forgery (a third-party site cannot read the token).
     */
    @POST
    public Response decline(@FormParam("tenantId") String tenantId, @FormParam("csrf") String csrf) {
        Span span = TracingHelper.startServerSpan("invite-decline.decline");
        Throwable traceError = null;
        try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
            RealmModel realm = session.getContext().getRealm();
            URI loginUri = Urls.realmLoginPage(session.getContext().getUri().getBaseUri(), realm.getName());

            var authResult = AuthenticationManager.authenticateIdentityCookie(session, realm, true);
            if (authResult == null) {
                log.warning("invite-decline: no active session on POST, redirecting to login");
                return Response.seeOther(loginUri).build();
            }

            UserModel user = authResult.getUser();
            span.tag("user.id", user.getId());

            // CSRF validation
            UserSessionModel userSession = authResult.getSession();
            String expected = userSession.getNote(DECLINE_CSRF_NOTE);
            userSession.removeNote(DECLINE_CSRF_NOTE); // single use
            if (expected == null || csrf == null || !expected.equals(csrf)) {
                log.warning("invite-decline: CSRF token mismatch for user: " + user.getId());
                return calmErrorResponse(loginUri);
            }

            if (tenantId == null || tenantId.isBlank()) {
                log.warning("invite-decline: tenantId param missing on POST for user: " + user.getId());
                return calmErrorResponse(loginUri);
            }
            span.tag("tenant.id", tenantId);

            Optional<TenantMembershipModel> membershipOpt = findMembership(realm, user, tenantId);
            if (membershipOpt.isEmpty()) {
                // Idempotent: already declined / not a member. Treat as success, go to login.
                log.warning("invite-decline: user " + user.getId() + " has no membership in tenant " + tenantId
                        + " on POST — redirecting to login");
                return Response.seeOther(loginUri).build();
            }

            TenantMembershipModel membership = membershipOpt.get();
            membership.getTenant().revokeMembership(membership.getId());
            log.info("invite-decline: revoked membership " + membership.getId() + " for user " + user.getId());

            // Invalidate all user sessions. Materialize the stream first — backchannelLogout mutates
            // the underlying session store, so iterating the live stream risks CME / partial logout.
            List<UserSessionModel> userSessions = session.sessions()
                    .getUserSessionsStream(realm, user)
                    .toList();
            for (UserSessionModel us : userSessions) {
                AuthenticationManager.backchannelLogout(session, us, true);
            }
            log.info("invite-decline: logged out " + userSessions.size() + " session(s) for user " + user.getId());

            return Response.seeOther(loginUri).build();

        } catch (Exception ex) {
            traceError = ex;
            log.severe("invite-decline: unexpected error — " + ex.getMessage());
            return calmErrorResponse(loginUriQuietly());
        } finally {
            TracingHelper.finishSpan(span, traceError);
        }
    }

    private Optional<TenantMembershipModel> findMembership(RealmModel realm, UserModel user, String tenantId) {
        TenantProvider provider = session.getProvider(TenantProvider.class);
        return provider.getTenantMembershipsStream(realm, user)
                .filter(m -> m.getTenant() != null && tenantId.equals(m.getTenant().getId()))
                .findFirst();
    }

    private URI loginUriQuietly() {
        try {
            return Urls.realmLoginPage(
                    session.getContext().getUri().getBaseUri(),
                    session.getContext().getRealm().getName());
        } catch (Exception e) {
            return null;
        }
    }

    // Confirmation page with a CSRF-protected POST form — no state change happens here.
    private static Response confirmPageResponse(String tenantId, String csrf, String tenantName, URI loginUri) {
        String account = (tenantName != null && !tenantName.isBlank()) ? htmlEscape(tenantName) : "this account";
        String html = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>Decline invitation</title></head>"
                + "<body style=\"font-family:system-ui,sans-serif;max-width:480px;margin:4rem auto;padding:1rem\">"
                + "<h1 style=\"font-size:1.25rem\">Decline invitation?</h1>"
                + "<p>This will remove you from <strong>" + account + "</strong> and sign you out of all sessions.</p>"
                + "<form method=\"POST\" style=\"display:flex;gap:.75rem;align-items:center\">"
                + "<input type=\"hidden\" name=\"tenantId\" value=\"" + htmlEscape(tenantId) + "\">"
                + "<input type=\"hidden\" name=\"csrf\" value=\"" + htmlEscape(csrf) + "\">"
                + "<button type=\"submit\" style=\"padding:.5rem 1rem\">Yes, decline</button>"
                + "<a href=\"" + htmlEscape(loginUri != null ? loginUri.toString() : "#") + "\">Cancel</a>"
                + "</form></body></html>";
        return Response.ok(html, MediaType.TEXT_HTML)
                .header("Cache-Control", "no-store, no-cache")
                .build();
    }

    // Task 5.10: calm HTML error page — no blank screens
    private static Response calmErrorResponse(URI loginUri) {
        String loginUrl = htmlEscape(loginUri != null ? loginUri.toString() : "#");
        String html = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">"
                + "<title>Error</title></head><body style=\"font-family:sans-serif;padding:2rem\">"
                + "<h1>Unable to process decline</h1>"
                + "<p>Your invitation may have already been revoked or you are not a member of this account.</p>"
                + "<p>Return to <a href=\"" + loginUrl + "\">login</a>.</p></body></html>";
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.TEXT_HTML)
                .entity(html)
                .build();
    }

    private static String htmlEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
