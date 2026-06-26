package dev.sultanov.keycloak.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.options.Cookie;
import dev.sultanov.keycloak.multitenancy.resource.representation.TenantInvitationRepresentation;
import dev.sultanov.keycloak.multitenancy.support.BaseIntegrationTest;
import dev.sultanov.keycloak.multitenancy.support.IntegrationTestContextHolder;
import dev.sultanov.keycloak.multitenancy.support.actor.KeycloakAdminCli;
import dev.sultanov.keycloak.multitenancy.support.browser.AccountPage;
import dev.sultanov.keycloak.multitenancy.support.browser.SignInPage;
import dev.sultanov.keycloak.multitenancy.support.data.UserData;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;

public class InviteDeclineTest extends BaseIntegrationTest {

    private static final String REALM_NAME = "multi-tenant";

    private KeycloakAdminCli keycloakAdminCli;

    @BeforeEach
    void setUp() {
        keycloakAdminCli = KeycloakAdminCli.forMainRealm();
    }

    /**
     * Test 1: Valid decline removes membership and redirects to login.
     *
     * Flow: create tenant → invite user → user logs in (auto-accept fires) →
     * user has membership → call decline with session cookie → membership revoked →
     * 303 redirect to login.
     */
    @Test
    void validDecline_shouldRevokeMembershipAndRedirectToLogin() {
        // given – tenant owner creates tenant and invites a new user
        var ownerResource = keycloakAdminCli.createVerifiedUser().createTenant();
        String tenantId = ownerResource.toRepresentation().getId();

        var inviteeData = UserData.random();
        keycloakAdminCli.createVerifiedUser(inviteeData);

        var invitation = new TenantInvitationRepresentation();
        invitation.setEmail(inviteeData.getEmail());
        try (var response = ownerResource.invitations().createInvitation(invitation)) {
            assertThat(CreatedResponseUtil.getCreatedId(response)).isNotNull();
        }

        // when – invitee logs in via browser (auto-accept fires for single invitation)
        var browserContext = IntegrationTestContextHolder.getContext().browser().newContext();
        var page = browserContext.newPage();
        try {
            page.navigate(IntegrationTestContextHolder.getContext().keycloakUrl()
                    + "/realms/" + REALM_NAME + "/account/#/");
            // Navigate through sign-in flow
            page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                    new com.microsoft.playwright.Page.GetByRoleOptions().setName("Sign in")).click();
            page.getByLabel("Email").fill(inviteeData.getEmail());
            page.getByLabel("Password", new com.microsoft.playwright.Page.GetByLabelOptions().setExact(true))
                    .fill(inviteeData.getPassword());
            page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                    new com.microsoft.playwright.Page.GetByRoleOptions().setName("Sign in")).click();
            // Wait for account page to load after auto-accept
            page.waitForURL("**/account/**");

            // then – invitee now has membership (auto-accept granted it)
            var memberships = ownerResource.memberships().listMemberships(null, null, null);
            assertThat(memberships).as("invitee should have membership after auto-accept").isNotEmpty();

            // extract KC session cookies to use in HTTP request
            List<Cookie> cookies = browserContext.cookies();
            String cookieHeader = cookies.stream()
                    .map(c -> c.name + "=" + c.value)
                    .collect(Collectors.joining("; "));
            assertThat(cookieHeader).as("browser should have KC session cookies").isNotBlank();

            // call the decline endpoint with the session cookie.
            // The decline flow is CSRF-protected: GET renders a confirmation page with a one-time
            // token, and the actual revocation happens on POST with that token.
            String externalBase = IntegrationTestContextHolder.getContext().keycloakUrl();
            String declineUrl = externalBase + "/realms/" + REALM_NAME
                    + "/tenant/invite-decline?tenantId=" + tenantId;

            try (Client isolatedClient = ClientBuilder.newClient()) {
                // Step 1: GET the confirmation page (side-effect free) and extract the CSRF token
                String csrf;
                try (var formResponse = isolatedClient.target(declineUrl)
                        .request()
                        .header("Cookie", cookieHeader)
                        .get()) {
                    assertThat(formResponse.getStatus())
                            .as("decline confirmation page should render")
                            .isEqualTo(200);
                    String formBody = formResponse.readEntity(String.class);
                    java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("name=\"csrf\" value=\"([^\"]+)\"")
                            .matcher(formBody);
                    assertThat(m.find()).as("confirmation page must contain a CSRF token").isTrue();
                    csrf = m.group(1);
                }

                // Step 2: POST the confirmation form — this performs the decline
                String postUrl = externalBase + "/realms/" + REALM_NAME + "/tenant/invite-decline";
                jakarta.ws.rs.core.Form form = new jakarta.ws.rs.core.Form()
                        .param("tenantId", tenantId)
                        .param("csrf", csrf);
                try (var httpResponse = isolatedClient.target(postUrl)
                        .request()
                        .header("Cookie", cookieHeader)
                        .post(jakarta.ws.rs.client.Entity.form(form))) {
                    // AC-3: 303 redirect to login page
                    assertThat(httpResponse.getStatus()).isEqualTo(303);
                    assertThat(httpResponse.getLocation())
                            .as("decline should redirect to login page")
                            .isNotNull();
                    assertThat(httpResponse.getLocation().toString())
                            .contains("/realms/" + REALM_NAME);
                }
            }

            // and – invitee's membership is revoked (owner's membership remains)
            var membershipsAfter = ownerResource.memberships().listMemberships(null, null, null);
            assertThat(membershipsAfter)
                    .as("invitee's membership should be revoked after decline")
                    .noneMatch(m -> inviteeData.getEmail().equalsIgnoreCase(
                            m.getUser() != null ? m.getUser().getEmail() : null));

        } finally {
            page.close();
            browserContext.close();
        }
    }

    /**
     * Test 2: Decline without authenticated session redirects safely (no 500).
     */
    @Test
    void declineWithoutSession_shouldRedirectSafelyWithoutError() {
        String externalBase = IntegrationTestContextHolder.getContext().keycloakUrl();
        // use a non-existent tenantId — if no session, the auth check fails first anyway
        String declineUrl = externalBase + "/realms/" + REALM_NAME
                + "/tenant/invite-decline?tenantId=" + UUID.randomUUID();

        try (Client isolatedClient = ClientBuilder.newClient()) {
            try (var httpResponse = isolatedClient.target(declineUrl).request().get()) {
                // AC-4: must not be 500 or blank
                assertThat(httpResponse.getStatus())
                        .as("unauthenticated decline must not return 500")
                        .isNotEqualTo(500);
                // Should redirect to login (303) since no session
                assertThat(httpResponse.getStatus())
                        .as("unauthenticated decline should redirect to login")
                        .isEqualTo(303);
            }
        }
    }

    /**
     * Test 3: Decline with unknown/wrong tenantId returns a calm response (no 500/blank).
     *
     * The user is not a member of the (bogus) tenant, so the confirmation GET treats it as
     * "nothing to decline" and benignly redirects to login (303) — never a 500 or blank page (AC-4).
     */
    @Test
    void declineWithUnknownTenantId_shouldReturnCalmResponse() {
        // Create a user WITH a tenant so CreateTenant required action won't block the account page on next login.
        var user = keycloakAdminCli.createVerifiedUser();
        user.createTenant(); // handles browser flow including CreateTenant form
        var userData = user.getUserData();

        var browserContext = IntegrationTestContextHolder.getContext().browser().newContext();
        var page = browserContext.newPage();
        try {
            page.navigate(IntegrationTestContextHolder.getContext().keycloakUrl()
                    + "/realms/" + REALM_NAME + "/account/#/");
            page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                    new com.microsoft.playwright.Page.GetByRoleOptions().setName("Sign in")).click();
            page.getByLabel("Email").fill(userData.getEmail());
            page.getByLabel("Password", new com.microsoft.playwright.Page.GetByLabelOptions().setExact(true))
                    .fill(userData.getPassword());
            page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                    new com.microsoft.playwright.Page.GetByRoleOptions().setName("Sign in")).click();
            page.waitForURL("**/account/**");

            List<Cookie> cookies = browserContext.cookies();
            String cookieHeader = cookies.stream()
                    .map(c -> c.name + "=" + c.value)
                    .collect(Collectors.joining("; "));

            String externalBase = IntegrationTestContextHolder.getContext().keycloakUrl();
            String declineUrl = externalBase + "/realms/" + REALM_NAME
                    + "/tenant/invite-decline?tenantId=not-a-real-uuid";

            try (Client isolatedClient = ClientBuilder.newClient()) {
                try (var httpResponse = isolatedClient.target(declineUrl)
                        .request()
                        .header("Cookie", cookieHeader)
                        .get()) {
                    // AC-4: must not be 500 or blank
                    assertThat(httpResponse.getStatus())
                            .as("decline with invalid tenant must not return 500")
                            .isNotEqualTo(500);
                    // Not a member of the bogus tenant → benign redirect to login (no alarming error)
                    assertThat(httpResponse.getStatus())
                            .as("decline with invalid tenant should redirect to login")
                            .isEqualTo(303);
                    assertThat(httpResponse.getLocation())
                            .as("redirect target should be present")
                            .isNotNull();
                    assertThat(httpResponse.getLocation().toString())
                            .contains("/realms/" + REALM_NAME);
                }
            }

        } finally {
            page.close();
            browserContext.close();
        }
    }
}
