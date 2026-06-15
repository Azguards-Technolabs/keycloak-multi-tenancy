package dev.sultanov.keycloak.multitenancy.support.browser;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import lombok.experimental.UtilityClass;

@UtilityClass
class PageResolver {

    static AbstractPage resolve(Page page) {
        page.waitForSelector("h1");
        if (page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Create tenant")).first().isVisible()) {
            return new CreateTenantPage(page);
        } else if (page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Review Your Tenant Invitations")).first().isVisible()) {
            return new ReviewInvitationsPage(page);
        } else if (page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Select tenant")).first().isVisible()) {
            return new SelectTenantPage(page);
        } else if (page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Personal info")).first().isVisible()) {
            return new AccountPage(page);
        } else if (page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("We are sorry...")).first().isVisible()) {
            return new ErrorPage(page);
        } else {
            throw new IllegalStateException("Unexpected page");
        }
    }
}

