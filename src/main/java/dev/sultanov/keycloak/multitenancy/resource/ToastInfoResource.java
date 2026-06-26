package dev.sultanov.keycloak.multitenancy.resource;

import brave.Span;
import dev.sultanov.keycloak.multitenancy.tracing.TracingHelper;
import dev.sultanov.keycloak.multitenancy.util.Constants;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.managers.AuthenticationManager;

/**
 * Returns and clears the one-time toast session notes set by the auto-accept flow.
 * Cookie-authenticated (same pattern as InviteDeclineResource).
 * Called by the account theme JavaScript on page load to show the toast.
 */
public class ToastInfoResource {

    private static final Logger log = Logger.getLogger(ToastInfoResource.class.getName());

    private final KeycloakSession session;

    public ToastInfoResource(KeycloakSession session) {
        this.session = session;
    }

    @GET
    public Response getToastInfo() {
        Span span = TracingHelper.startServerSpan("toast-info.get");
        Throwable traceError = null;
        try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
            RealmModel realm = session.getContext().getRealm();

            var authResult = AuthenticationManager.authenticateIdentityCookie(session, realm, true);
            if (authResult == null) {
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }

            var userSession = authResult.getSession();
            span.tag("user.id", authResult.getUser().getId());

            String inviterName = userSession.getNote(Constants.TOAST_INVITER_NAME_NOTE);
            String tenantName  = userSession.getNote(Constants.TOAST_TENANT_NAME_NOTE);
            String tenantId    = userSession.getNote(Constants.TOAST_TENANT_ID_NOTE);

            if (inviterName == null && tenantName == null && tenantId == null) {
                // No toast pending — return empty (no-store, consistent with the populated branch)
                return Response.ok("{}", MediaType.APPLICATION_JSON)
                        .header("Cache-Control", "no-store, no-cache")
                        .build();
            }

            // Clear notes so the toast shows only once
            userSession.removeNote(Constants.TOAST_INVITER_NAME_NOTE);
            userSession.removeNote(Constants.TOAST_TENANT_NAME_NOTE);
            userSession.removeNote(Constants.TOAST_TENANT_ID_NOTE);
            log.fine("toast-info: returned and cleared toast notes for user " + authResult.getUser().getId());

            String json = "{"
                    + "\"inviterName\":" + jsonString(inviterName) + ","
                    + "\"tenantName\":" + jsonString(tenantName) + ","
                    + "\"tenantId\":" + jsonString(tenantId)
                    + "}";

            return Response.ok(json, MediaType.APPLICATION_JSON)
                    .header("Cache-Control", "no-store, no-cache")
                    .build();

        } catch (Exception ex) {
            traceError = ex;
            log.severe("toast-info: unexpected error — " + ex.getMessage());
            return Response.serverError().build();
        } finally {
            TracingHelper.finishSpan(span, traceError);
        }
    }

    private static String jsonString(String value) {
        if (value == null) return "null";
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
