package dev.sultanov.keycloak.multitenancy.support.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.List;

public class SelectTenantPage extends AbstractPage {

    SelectTenantPage(Page page) {
        super(page);
    }

    public List<String> availableOptions() {
        return page.locator(".tenant-selection-card .tenant-info p strong").allTextContents();
    }

    public SelectTenantPage select(String tenantName) {
        page.locator(".tenant-selection-card")
                .filter(new Locator.FilterOptions().setHasText(tenantName))
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Select"))
                .click();
        return this;
    }

    // The new tenant UI submits directly when a card's "Select" button is clicked (see select()),
    // so there is no separate "Sign in" button to press here — this resolves the page that the
    // selection navigated to.
    public AbstractPage signIn() {
        return PageResolver.resolve(page);
    }
}
