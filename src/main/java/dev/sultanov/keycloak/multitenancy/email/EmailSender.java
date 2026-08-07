package dev.sultanov.keycloak.multitenancy.email;

import jakarta.ws.rs.core.UriBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.jboss.logging.Logger;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.keycloak.services.ServicesLogger;

@UtilityClass
public class EmailSender {

    private static final Logger log = Logger.getLogger(EmailSender.class);

    // Kill switch: azguards-application already sends its own invitation emails out-of-band
    // (Kafka/SQS), making this library's emails redundant, and the shared SMTP relay has
    // repeatedly failed (rotated/revoked credentials). Off by default; set
    // KEYCLOAK_MULTITENANCY_EMAIL_ENABLED=true to restore delivery.
    private static final boolean ENABLED =
            Boolean.parseBoolean(System.getenv().getOrDefault("KEYCLOAK_MULTITENANCY_EMAIL_ENABLED", "false"));

    public static void sendInvitationEmail(KeycloakSession session, UserModel invitee, String tenantName, String invitationId) {
        var baseUri = session.getContext().getUri().getBaseUri();
        var realmName = session.getContext().getRealm().getName();
        var inviteVerifyUrl = UriBuilder.fromUri(baseUri)
                .path("realms").path(realmName).path("tenant").path("invite-verify")
                .queryParam("token", invitationId)
                .build()
                .toString();
        var bodyAttributes = new HashMap<String, Object>();
        bodyAttributes.put("tenantName", tenantName);
        bodyAttributes.put("inviteVerifyUrl", inviteVerifyUrl);
        sendEmail(session, invitee, "invitationEmailSubject", List.of(tenantName), "invitation-email.ftl", bodyAttributes);
    }

    public static void sendInvitationAcceptedEmail(KeycloakSession session, UserModel inviter, String inviteeEmail, String tenantName) {
        var bodyAttributes = new HashMap<String, Object>();
        bodyAttributes.put("inviteeEmail", inviteeEmail);
        bodyAttributes.put("tenantName", tenantName);
        sendEmail(session, inviter, "invitationAcceptedEmailSubject", List.of(), "invitation-accepted-email.ftl", bodyAttributes);
    }

    public static void sendMagicLinkEmail(KeycloakSession session, UserModel recipient, String magicLinkUrl) throws EmailException {
        if (!ENABLED) {
            // Magic link sign-in must surface delivery failures to the caller so the UI does not
            // claim "Check your email" (and start the resend cooldown) when nothing was sent —
            // unlike the other email types below, this one cannot silently no-op.
            throw new EmailException("Email sending is disabled (KEYCLOAK_MULTITENANCY_EMAIL_ENABLED=false)");
        }
        var bodyAttributes = new HashMap<String, Object>();
        bodyAttributes.put("magicLinkUrl", magicLinkUrl);
        doSend(session, recipient, "magicLinkEmailSubject", List.of(), "magic-link-email.ftl", bodyAttributes);
    }

    public static void sendInvitationDeclinedEmail(KeycloakSession session, UserModel inviter, String inviteeEmail, String tenantName) {
        var bodyAttributes = new HashMap<String, Object>();
        bodyAttributes.put("inviteeEmail", inviteeEmail);
        bodyAttributes.put("tenantName", tenantName);
        sendEmail(session, inviter, "invitationDeclinedEmailSubject", List.of(), "invitation-declined-email.ftl", bodyAttributes);
    }

    private static void sendEmail(KeycloakSession session, UserModel recipient, String subject, List<Object> subjectAttributes, String template,
            Map<String, Object> bodyAttributes) {
        if (!ENABLED) {
            log.debugf("Email sending disabled (KEYCLOAK_MULTITENANCY_EMAIL_ENABLED=false) — skipping '%s' to %s",
                    template, recipient.getEmail());
            return;
        }
        try {
            doSend(session, recipient, subject, subjectAttributes, template, bodyAttributes);
        } catch (EmailException e) {
            ServicesLogger.LOGGER.failedToSendEmail(e);
        }
    }

    private static void doSend(KeycloakSession session, UserModel recipient, String subject, List<Object> subjectAttributes, String template,
            Map<String, Object> bodyAttributes) throws EmailException {
        session.getProvider(EmailTemplateProvider.class)
                .setRealm(session.getContext().getRealm())
                .setUser(recipient)
                .send(subject, subjectAttributes, template, bodyAttributes);
    }
}