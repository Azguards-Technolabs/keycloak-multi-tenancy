package dev.sultanov.keycloak.multitenancy.support.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

public class ReviewInvitationsPage extends AbstractPage {

    ReviewInvitationsPage(Page page) {
        super(page);
    }

    public ReviewInvitationsPage acceptInvitation(String tenantName) {
        page.locator(".tenant-invitation-card", new Page.LocatorOptions().setHasText(tenantName))
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Accept"))
                .click();
        return this;
    }

    public ReviewInvitationsPage rejectInvitation(String tenantName) {
        page.locator(".tenant-invitation-card", new Page.LocatorOptions().setHasText(tenantName))
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Reject"))
                .click();
        return this;
    }

    public AbstractPage proceed() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Proceed")).click();
        return PageResolver.resolve(page);
    }


    @Deprecated
    public ReviewInvitationsPage uncheckInvitation(String tenantName) {
        return rejectInvitation(tenantName);
    }

    @Deprecated
    public AbstractPage accept() {
        return proceed();
    }
}

