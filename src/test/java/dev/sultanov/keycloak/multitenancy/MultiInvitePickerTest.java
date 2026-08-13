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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;

public class MultiInvitePickerTest extends BaseIntegrationTest {

    private static final String REALM_NAME = "multi-tenant";

    private KeycloakAdminCli keycloakAdminCli;

    @BeforeEach
    void setUp() {
        keycloakAdminCli = KeycloakAdminCli.forMainRealm();
    }

    /**
     * Test 1 (AC-1, AC-3): With 2 invitations, picker is shown (no auto-accept).
     * Accepting both grants 2 memberships.
     */
    @Test
    void multiInvite_acceptBoth_grantsTwoMemberships() {
        // given – two tenant owners each invite the same user
        var inviteeData = UserData.random();
        keycloakAdminCli.createVerifiedUser(inviteeData);

        var tenantAResource = keycloakAdminCli.createVerifiedUser().createTenant();
        var tenantBResource = keycloakAdminCli.createVerifiedUser().createTenant();
        String tenantAName = tenantAResource.toRepresentation().getName();
        String tenantBName = tenantBResource.toRepresentation().getName();

        sendInvitation(tenantAResource, inviteeData.getEmail());
        sendInvitation(tenantBResource, inviteeData.getEmail());

        // when – invitee signs in
        var nextPage = AccountPage.open()
                .signIn()
                .fillCredentials(inviteeData.getEmail(), inviteeData.getPassword())
                .signIn();

        // then – picker is rendered (not auto-accept, since count > 1)
        assertThat(nextPage).isInstanceOf(ReviewInvitationsPage.class);

        // Accept both and proceed
        ((ReviewInvitationsPage) nextPage)
                .acceptInvitation(tenantAName)
                .acceptInvitation(tenantBName)
                .proceed();

        // Assert invitee has membership in both tenants
        var membersA = tenantAResource.memberships().listMemberships(null, null, null);
        var membersB = tenantBResource.memberships().listMemberships(null, null, null);
        assertThat(membersA).as("invitee should be member of Tenant A")
                .anyMatch(m -> m.getUser() != null && inviteeData.getEmail().equalsIgnoreCase(m.getUser().getEmail()));
        assertThat(membersB).as("invitee should be member of Tenant B")
                .anyMatch(m -> m.getUser() != null && inviteeData.getEmail().equalsIgnoreCase(m.getUser().getEmail()));
    }

    /**
     * Test 2 (AC-3): Accept one invitation, decline the other.
     * Only the accepted tenant's membership is created.
     */
    @Test
    void multiInvite_acceptOneDeclineOne_grantsOneMembership() {
        // given
        var inviteeData = UserData.random();
        keycloakAdminCli.createVerifiedUser(inviteeData);

        var tenantAResource = keycloakAdminCli.createVerifiedUser().createTenant();
        var tenantBResource = keycloakAdminCli.createVerifiedUser().createTenant();
        String tenantAName = tenantAResource.toRepresentation().getName();
        String tenantBName = tenantBResource.toRepresentation().getName();

        sendInvitation(tenantAResource, inviteeData.getEmail());
        sendInvitation(tenantBResource, inviteeData.getEmail());

        // when – invitee signs in and accepts A, rejects B
        var nextPage = AccountPage.open()
                .signIn()
                .fillCredentials(inviteeData.getEmail(), inviteeData.getPassword())
                .signIn();

        assertThat(nextPage).isInstanceOf(ReviewInvitationsPage.class);

        ((ReviewInvitationsPage) nextPage)
                .acceptInvitation(tenantAName)
                .rejectInvitation(tenantBName)
                .proceed();

        // then – only Tenant A has the invitee as member; Tenant B does not
        var membersA = tenantAResource.memberships().listMemberships(null, null, null);
        var membersB = tenantBResource.memberships().listMemberships(null, null, null);
        assertThat(membersA).as("invitee should be member of Tenant A")
                .anyMatch(m -> m.getUser() != null && inviteeData.getEmail().equalsIgnoreCase(m.getUser().getEmail()));
        assertThat(membersB).as("invitee should NOT be member of Tenant B after decline")
                .noneMatch(m -> m.getUser() != null && inviteeData.getEmail().equalsIgnoreCase(m.getUser().getEmail()));
    }

    /**
     * Test 3 (AC-3, AC-6): Submitting with no actions taken (empty accepted + empty rejected)
     * while hasMemberships=false causes the picker to re-render with an error message.
     */
    @Test
    void multiInvite_submitEmptySelection_showsErrorAndReRenders() {
        // given
        var inviteeData = UserData.random();
        keycloakAdminCli.createVerifiedUser(inviteeData);

        var tenantAResource = keycloakAdminCli.createVerifiedUser().createTenant();
        var tenantBResource = keycloakAdminCli.createVerifiedUser().createTenant();

        sendInvitation(tenantAResource, inviteeData.getEmail());
        sendInvitation(tenantBResource, inviteeData.getEmail());

        // when – invitee signs in, picker is shown
        var context = IntegrationTestContextHolder.getContext();
        var browserContext = context.browser().newContext();
        var page = browserContext.newPage();
        try {
            page.navigate(context.keycloakUrl() + "/realms/" + REALM_NAME + "/account/#/");
            page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                    new com.microsoft.playwright.Page.GetByRoleOptions().setName("Sign in")).click();
            page.getByLabel("Email").fill(inviteeData.getEmail());
            page.getByLabel("Password", new com.microsoft.playwright.Page.GetByLabelOptions().setExact(true))
                    .fill(inviteeData.getPassword());
            page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                    new com.microsoft.playwright.Page.GetByRoleOptions().setName("Sign in")).click();

            // Wait for review invitations page heading
            page.waitForSelector("h1");

            // Submit form with no accepted or rejected tenants (bypasses the JS-disabled proceed button)
            page.evaluate("() => { " +
                    "document.getElementById('acceptedTenants').value = '';" +
                    "document.getElementById('rejectedTenants').value = '';" +
                    "document.getElementById('proceed-invitations-form').submit();" +
                    "}");

            // then – page re-renders with error message
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            String content = page.content();
            assertThat(content)
                    .as("error should mention accepting at least one invitation")
                    .contains("accept at least one");

            // Invitee still has no memberships in either tenant
            var membersA = tenantAResource.memberships().listMemberships(null, null, null);
            var membersB = tenantBResource.memberships().listMemberships(null, null, null);
            assertThat(membersA).as("no membership in A after error")
                    .noneMatch(m -> m.getUser() != null && inviteeData.getEmail().equalsIgnoreCase(m.getUser().getEmail()));
            assertThat(membersB).as("no membership in B after error")
                    .noneMatch(m -> m.getUser() != null && inviteeData.getEmail().equalsIgnoreCase(m.getUser().getEmail()));
        } finally {
            page.close();
            browserContext.close();
        }
    }

    /**
     * Test 4 (story 5.5 AC-1, AC-2, AC-3): An agent with exactly one pending invitation sees the
     * unified review screen — not auto-accepted — and can accept it via processAction() to gain
     * membership, same as the 2+ path.
     */
    @Test
    void singleInvite_reviewScreenShown_acceptGrantsMembership() {
        // given – a single tenant owner invites the user
        var inviteeData = UserData.random();
        keycloakAdminCli.createVerifiedUser(inviteeData);

        var tenantResource = keycloakAdminCli.createVerifiedUser().createTenant();
        String tenantName = tenantResource.toRepresentation().getName();

        sendInvitation(tenantResource, inviteeData.getEmail());

        // when – invitee signs in
        var nextPage = AccountPage.open()
                .signIn()
                .fillCredentials(inviteeData.getEmail(), inviteeData.getPassword())
                .signIn();

        // then – the review screen is rendered for exactly one invitation, not auto-accepted (AC-1)
        assertThat(nextPage).isInstanceOf(ReviewInvitationsPage.class);

        // Accept the single invitation and proceed
        ((ReviewInvitationsPage) nextPage)
                .acceptInvitation(tenantName)
                .proceed();

        // Assert invitee has gained membership
        var members = tenantResource.memberships().listMemberships(null, null, null);
        assertThat(members).as("invitee should be member of the tenant after accepting")
                .anyMatch(m -> m.getUser() != null && inviteeData.getEmail().equalsIgnoreCase(m.getUser().getEmail()));
    }

    /**
     * Test 5 (story 5.5 AC-1, AC-3): An agent with exactly one pending invitation who declines it
     * via processAction() has the invitation revoked and gains no membership — there is nothing to
     * force a logout from, unlike the retired InviteDeclineResource behavior.
     */
    @Test
    void singleInvite_reviewScreenShown_declineRevokesWithNoMembership() {
        // given – a single tenant owner invites the user
        var inviteeData = UserData.random();
        keycloakAdminCli.createVerifiedUser(inviteeData);

        var tenantResource = keycloakAdminCli.createVerifiedUser().createTenant();
        String tenantName = tenantResource.toRepresentation().getName();

        sendInvitation(tenantResource, inviteeData.getEmail());

        // when – invitee signs in and declines the single invitation
        var nextPage = AccountPage.open()
                .signIn()
                .fillCredentials(inviteeData.getEmail(), inviteeData.getPassword())
                .signIn();

        assertThat(nextPage).isInstanceOf(ReviewInvitationsPage.class);

        ((ReviewInvitationsPage) nextPage)
                .rejectInvitation(tenantName)
                .proceed();

        // then – the invitation was actually revoked (not merely left un-accepted). Without this
        // assertion the test is tautological: no membership is ever granted pre-accept, so the
        // no-membership check below holds whether decline revokes the invitation or silently no-ops.
        var remainingInvitations = tenantResource.invitations().listInvitations(null, null, null);
        assertThat(remainingInvitations).as("declining should revoke the invitation for the tenant")
                .noneMatch(i -> inviteeData.getEmail().equalsIgnoreCase(i.getEmail()));

        // and – no membership was granted for the declined tenant
        var members = tenantResource.memberships().listMemberships(null, null, null);
        assertThat(members).as("invitee should NOT be member of the tenant after declining")
                .noneMatch(m -> m.getUser() != null && inviteeData.getEmail().equalsIgnoreCase(m.getUser().getEmail()));
    }

    private void sendInvitation(TenantResource tenantResource, String email) {
        var invitation = new TenantInvitationRepresentation();
        invitation.setEmail(email);
        try (var response = tenantResource.invitations().createInvitation(invitation)) {
            assertThat(CreatedResponseUtil.getCreatedId(response)).isNotNull();
        }
    }
}
