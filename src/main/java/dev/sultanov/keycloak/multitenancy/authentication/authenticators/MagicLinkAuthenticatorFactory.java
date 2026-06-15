package dev.sultanov.keycloak.multitenancy.authentication.authenticators;

import java.util.List;
import org.keycloak.Config.Scope;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel.Requirement;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

public class MagicLinkAuthenticatorFactory implements AuthenticatorFactory {

    private static final String ID = "magic-link";
    public static final MagicLinkAuthenticator SINGLETON = new MagicLinkAuthenticator();

    @Override
    public Authenticator create(KeycloakSession session) {
        return SINGLETON;
    }

    @Override
    public void init(Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayType() {
        return "Magic Link (Email Sign-in)";
    }

    @Override
    public String getReferenceCategory() {
        return null;
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public Requirement[] getRequirementChoices() {
        return new Requirement[]{ Requirement.ALTERNATIVE, Requirement.DISABLED };
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Sends a one-time sign-in link to the agent's verified email address.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return null;
    }
}
