package dev.sultanov.keycloak.multitenancy.authentication.authenticators;

import org.keycloak.authentication.actiontoken.DefaultActionToken;

public class MagicLinkActionToken extends DefaultActionToken {

    public static final String TOKEN_TYPE = "magic-link";

    /**
     * @param compoundAuthSessionId the encoded {@link org.keycloak.sessions.AuthenticationSessionCompoundId}
     *                              used by the action-token framework to reconnect to the originating auth session.
     */
    public MagicLinkActionToken(String userId, int absoluteExpirationSeconds, String compoundAuthSessionId) {
        super(userId, TOKEN_TYPE, absoluteExpirationSeconds, null, compoundAuthSessionId);
    }

    public MagicLinkActionToken() {
        super();
    }
}
