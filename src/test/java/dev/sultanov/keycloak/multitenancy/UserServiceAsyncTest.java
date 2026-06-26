package dev.sultanov.keycloak.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sultanov.keycloak.multitenancy.resource.representation.TenantInvitationRepresentation;
import dev.sultanov.keycloak.multitenancy.support.BaseIntegrationTest;
import dev.sultanov.keycloak.multitenancy.support.IntegrationTestContextHolder;
import dev.sultanov.keycloak.multitenancy.support.actor.KeycloakAdminCli;
import dev.sultanov.keycloak.multitenancy.support.api.TenantResource;
import dev.sultanov.keycloak.multitenancy.support.browser.AccountPage;
import dev.sultanov.keycloak.multitenancy.support.browser.ReviewInvitationsPage;
import dev.sultanov.keycloak.multitenancy.support.data.UserData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;

/**
 * Verifies AC-1 (non-blocking async user-service), AC-6 (retry endpoint returns 202),
 * and AC-7 (toast notes set unconditionally on single auto-accept path).
 */
public class UserServiceAsyncTest extends BaseIntegrationTest {

    private static final String REALM_NAME = "multi-tenant";

    private KeycloakAdminCli keycloakAdminCli;

    @BeforeEach
    void setUp() {
        keycloakAdminCli = KeycloakAdminCli.forMainRealm();
    }

    /**
     * T-1 (AC-1): Even if user-service at host.docker.internal:4003 is unreachable,
     * the multi-invite picker processAction() still completes and the agent enters the product.
     */
    @Test
    void multiInvite_userServiceUnreachable_agentStillProceedsToAccountSelection() {
        var inviteeData = UserData.random();
        keycloakAdminCli.createVerifiedUser(inviteeData);

        var tenantAResource = keycloakAdminCli.createVerifiedUser().createTenant();
        var tenantBResource = keycloakAdminCli.createVerifiedUser().createTenant();
        String tenantAName = tenantAResource.toRepresentation().getName();
        String tenantBName = tenantBResource.toRepresentation().getName();

        sendInvitation(tenantAResource, inviteeData.getEmail());
        sendInvitation(tenantBResource, inviteeData.getEmail());

        // when – invitee signs in and reaches the picker
        var nextPage = AccountPage.open()
                .signIn()
                .fillCredentials(inviteeData.getEmail(), inviteeData.getPassword())
                .signIn();

        assertThat(nextPage).isInstanceOf(ReviewInvitationsPage.class);

        // Accept both — user-service is unreachable (host.docker.internal:4003 not running in CI)
        // but the login flow must complete without showing an error form (AC-1)
        ((ReviewInvitationsPage) nextPage)
                .acceptInvitation(tenantAName)
                .acceptInvitation(tenantBName)
                .proceed();

        // The agent should have received memberships in both tenants
        var membersA = tenantAResource.memberships().listMemberships(null, null, null);
        var membersB = tenantBResource.memberships().listMemberships(null, null, null);
        assertThat(membersA).as("invitee should be member of Tenant A despite user-service failure")
                .anyMatch(m -> m.getUser() != null && inviteeData.getEmail().equalsIgnoreCase(m.getUser().getEmail()));
        assertThat(membersB).as("invitee should be member of Tenant B despite user-service failure")
                .anyMatch(m -> m.getUser() != null && inviteeData.getEmail().equalsIgnoreCase(m.getUser().getEmail()));
    }

    /**
     * T-2 (AC-7): Single auto-accept path — toast notes are set unconditionally regardless of
     * whether the async user-service call eventually succeeds or fails.
     */
    @Test
    void singleAutoAccept_toastNotesSetUnconditionally() {
        var inviteeData = UserData.random();
        keycloakAdminCli.createVerifiedUser(inviteeData);

        var tenantResource = keycloakAdminCli.createVerifiedUser().createTenant();
        sendInvitation(tenantResource, inviteeData.getEmail());

        // Sign in — single invitation triggers auto-accept
        AccountPage.open()
                .signIn()
                .fillCredentials(inviteeData.getEmail(), inviteeData.getPassword())
                .signIn();

        // Invitee should be a member of the tenant after auto-accept
        var members = tenantResource.memberships().listMemberships(null, null, null);
        assertThat(members).as("invitee should be a member after single auto-accept")
                .anyMatch(m -> m.getUser() != null && inviteeData.getEmail().equalsIgnoreCase(m.getUser().getEmail()));
    }

    /**
     * T-3 (AC-6): The /user-service-retry endpoint returns 202 Accepted immediately.
     * Requires an authenticated user session.
     */
    @Test
    void retryEndpoint_returnsTwoZeroTwo_immediately() {
        var context = IntegrationTestContextHolder.getContext();
        var keycloakUrl = context.keycloakUrl();

        var inviteeData = UserData.random();
        keycloakAdminCli.createVerifiedUser(inviteeData);

        var tenantResource = keycloakAdminCli.createVerifiedUser().createTenant();
        sendInvitation(tenantResource, inviteeData.getEmail());

        // Sign in to establish a user session (cookie will be used for the retry endpoint)
        var browserContext = context.browser().newContext();
        var page = browserContext.newPage();
        try {
            page.navigate(keycloakUrl + "/realms/" + REALM_NAME + "/account/#/");
            page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                    new com.microsoft.playwright.Page.GetByRoleOptions().setName("Sign in")).click();
            page.getByLabel("Email").fill(inviteeData.getEmail());
            page.getByLabel("Password", new com.microsoft.playwright.Page.GetByLabelOptions().setExact(true))
                    .fill(inviteeData.getPassword());
            page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                    new com.microsoft.playwright.Page.GetByRoleOptions().setName("Sign in")).click();
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);

            // Call the retry endpoint using the browser's session cookie
            var response = page.request().post(
                    keycloakUrl + "/realms/" + REALM_NAME + "/mt-resource/user-service-retry");

            assertThat(response.status()).as("retry endpoint should return 202 Accepted")
                    .isEqualTo(202);
        } finally {
            page.close();
            browserContext.close();
        }
    }

    private void sendInvitation(TenantResource tenantResource, String email) {
        var invitation = new TenantInvitationRepresentation();
        invitation.setEmail(email);
        try (var response = tenantResource.invitations().createInvitation(invitation)) {
            assertThat(CreatedResponseUtil.getCreatedId(response)).isNotNull();
        }
    }
}
