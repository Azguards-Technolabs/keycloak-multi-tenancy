package dev.sultanov.keycloak.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sultanov.keycloak.multitenancy.resource.representation.TenantInvitationRepresentation;
import dev.sultanov.keycloak.multitenancy.support.BaseIntegrationTest;
import dev.sultanov.keycloak.multitenancy.support.IntegrationTestContextHolder;
import dev.sultanov.keycloak.multitenancy.support.actor.KeycloakAdminCli;
import dev.sultanov.keycloak.multitenancy.support.data.UserData;
import dev.sultanov.keycloak.multitenancy.support.mail.MailhogClient;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;

public class InviteLinkVerificationTest extends BaseIntegrationTest {

    private static final String REALM_NAME = "multi-tenant";

    // Matches the token query param from an invite-verify URL
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("/tenant/invite-verify\\?token=([0-9a-f\\-]+)", Pattern.CASE_INSENSITIVE);

    private KeycloakAdminCli keycloakAdminCli;
    private MailhogClient mailhogClient;

    @BeforeEach
    void setUp() {
        keycloakAdminCli = KeycloakAdminCli.forMainRealm();
        mailhogClient = MailhogClient.create();
        mailhogClient.deleteAll();
    }

    @Test
    void validToken_shouldSetEmailVerifiedAndRedirect() {
        // given – a tenant and an invitee user whose email is NOT yet verified
        var tenantResource = keycloakAdminCli.createVerifiedUser().createTenant();
        var inviteeData = UserData.random();
        var inviteeKcUser = keycloakAdminCli.createVerifiedUser(inviteeData);

        var inviteeAdminResource = keycloakAdminCli.getRealmResource().users().get(inviteeKcUser.getUserId());
        var inviteeRepresentation = inviteeAdminResource.toRepresentation();
        inviteeRepresentation.setEmailVerified(false);
        inviteeAdminResource.update(inviteeRepresentation);

        // when – admin sends invitation
        var invitation = new TenantInvitationRepresentation();
        invitation.setEmail(inviteeData.getEmail());
        try (var response = tenantResource.invitations().createInvitation(invitation)) {
            assertThat(CreatedResponseUtil.getCreatedId(response)).isNotNull();
        }

        // extract token from the invitation email delivered to Mailhog
        var emails = mailhogClient.findAllForRecipient(inviteeData.getEmail());
        assertThat(emails).as("invitation email should be delivered").hasSize(1);
        String token = extractToken(emails.get(0).body());
        assertThat(token).as("email body should contain invite-verify URL with token").isNotNull();

        // build the externally-accessible invite-verify URL
        String externalBase = IntegrationTestContextHolder.getContext().keycloakUrl();
        String inviteVerifyUrl = externalBase + "/realms/" + REALM_NAME + "/tenant/invite-verify?token=" + token;

        // Use an isolated client so KC session cookies don't leak into the shared client
        try (Client isolatedClient = ClientBuilder.newClient()) {
            try (var httpResponse = isolatedClient.target(inviteVerifyUrl).request().get()) {
                // AC-1 / Task 6.1: successful verification returns a 303 redirect to the realm account/login page
                assertThat(httpResponse.getStatus()).isEqualTo(303);
                assertThat(httpResponse.getLocation())
                        .as("303 should carry a Location header to the realm account/login page")
                        .isNotNull();
                assertThat(httpResponse.getLocation().toString()).contains("/realms/" + REALM_NAME);
            }
        }

        // then – the user's emailVerified flag is true
        var updatedRepresentation = inviteeAdminResource.toRepresentation();
        assertThat(updatedRepresentation.isEmailVerified())
                .as("emailVerified should be set to true after invite-link click")
                .isTrue();
    }

    @Test
    void garbageToken_shouldReturnCalmErrorPage() {
        String externalBase = IntegrationTestContextHolder.getContext().keycloakUrl();
        String inviteVerifyUrl = externalBase + "/realms/" + REALM_NAME + "/tenant/invite-verify?token=not-a-real-uuid";

        try (Client isolatedClient = ClientBuilder.newClient()) {
            try (var httpResponse = isolatedClient.target(inviteVerifyUrl).request().get()) {
                // must not be a 500 or blank screen
                assertThat(httpResponse.getStatus()).isNotEqualTo(500);
                String body = httpResponse.readEntity(String.class);
                assertThat(body).as("error page body should not be empty").isNotBlank();
                assertThat(body).as("error page should explain the link is invalid")
                        .containsIgnoringCase("invite");
            }
        }
    }

    @Test
    void revokedToken_shouldReturnCalmErrorPage() {
        // A revoked (deleted) invitation leaves no DB record — indistinguishable from a UUID that
        // never matched any invitation at the endpoint level. We use a freshly generated UUID that
        // guarantees there is no matching invitation in the DB.
        String nonExistentInvitationId = UUID.randomUUID().toString();

        String externalBase = IntegrationTestContextHolder.getContext().keycloakUrl();
        String inviteVerifyUrl = externalBase + "/realms/" + REALM_NAME + "/tenant/invite-verify?token=" + nonExistentInvitationId;

        // Use an isolated client so KC session cookies don't leak into the shared client
        try (Client isolatedClient = ClientBuilder.newClient()) {
            try (var httpResponse = isolatedClient.target(inviteVerifyUrl).request().get()) {
                // then – calm HTML error page, no stack trace
                assertThat(httpResponse.getStatus()).isNotEqualTo(500);
                String body = httpResponse.readEntity(String.class);
                assertThat(body).as("error page body should not be empty").isNotBlank();
                assertThat(body).as("error page should explain the link is invalid")
                        .containsIgnoringCase("invite");
            }
        }
    }

    private static String extractToken(String emailBody) {
        Matcher matcher = TOKEN_PATTERN.matcher(emailBody);
        return matcher.find() ? matcher.group(1) : null;
    }
}
