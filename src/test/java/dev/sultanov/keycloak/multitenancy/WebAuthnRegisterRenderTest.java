package dev.sultanov.keycloak.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Page;
import dev.sultanov.keycloak.multitenancy.support.BaseIntegrationTest;
import dev.sultanov.keycloak.multitenancy.support.IntegrationTestContextHolder;
import dev.sultanov.keycloak.multitenancy.support.actor.KeycloakAdminCli;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Render test for the custom webauthn-register.ftl template (Story 3.1).
 * Verifies the FTL renders without FreeMarker errors and expected elements are present
 * when a user with the webauthn-register required action logs in.
 */
public class WebAuthnRegisterRenderTest extends BaseIntegrationTest {

    private KeycloakAdminCli keycloakAdminClient;
    private String previousLoginTheme;

    @BeforeEach
    void setUp() {
        keycloakAdminClient = KeycloakAdminCli.forMainRealm();
        var realmResource = keycloakAdminClient.getRealmResource();
        var realmRep = realmResource.toRepresentation();
        previousLoginTheme = realmRep.getLoginTheme();
        realmRep.setLoginTheme("azguards-whatsapp");
        realmResource.update(realmRep);
    }

    @AfterEach
    void tearDown() {
        var realmResource = keycloakAdminClient.getRealmResource();
        var realmRep = realmResource.toRepresentation();
        realmRep.setLoginTheme(previousLoginTheme);
        realmResource.update(realmRep);
    }

    @Test
    void webAuthnRegisterPage_shouldRenderWithCustomTheme_whenUserHasRequiredAction() {
        var user = keycloakAdminClient.createVerifiedUser();

        // Assign webauthn-register required action to the test user
        var realmResource = keycloakAdminClient.getRealmResource();
        var userResource = realmResource.users().get(user.getUserId());
        var userRep = userResource.toRepresentation();
        userRep.setRequiredActions(List.of("webauthn-register"));
        userResource.update(userRep);

        var integrationTestContext = IntegrationTestContextHolder.getContext();
        var browserContext = integrationTestContext.browser().newContext();
        var page = browserContext.newPage();

        try {
            // Navigate to the Keycloak account console — KC immediately redirects to the
            // OIDC login page (custom azguards-whatsapp theme).
            page.navigate(integrationTestContext.keycloakUrl() + "/realms/multi-tenant/account/#/");

            // Custom azguards-whatsapp login.ftl uses #username / #password / #submitBtn
            page.waitForSelector("#username");
            page.locator("#username").fill(user.getUserData().getEmail());
            page.locator("#password").fill(user.getUserData().getPassword());
            page.locator("#submitBtn").click();

            // KC redirects to webauthn-register required action page
            page.waitForSelector("h1");

            // Verify no FreeMarker template error
            var pageContent = page.content();
            assertThat(pageContent)
                    .as("Page must not contain a FreeMarker template error")
                    .doesNotContain("Template processing error", "FreeMarker template error");

            // Verify expected elements from webauthn-register.ftl
            assertThat(page.locator("h1").textContent().trim())
                    .as("h1 must show the passkey registration title")
                    .isEqualTo("Set up a passkey");
            assertThat(page.locator("#passkey-register-btn").isVisible())
                    .as("Register button must be visible")
                    .isTrue();
            assertThat(page.locator("#kc-webauthn-register-form").count())
                    .as("Hidden WebAuthn submission form must be present")
                    .isEqualTo(1);
            assertThat(page.locator("#passkey-status").count())
                    .as("Polite ARIA live region for ceremony status must be present")
                    .isEqualTo(1);
        } finally {
            browserContext.close();
        }
    }
}
