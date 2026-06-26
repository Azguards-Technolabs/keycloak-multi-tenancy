package dev.sultanov.keycloak.multitenancy.authentication.authenticators;

import jakarta.ws.rs.core.MultivaluedMap;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationProcessor;
import org.keycloak.authentication.Authenticator;
import org.keycloak.broker.provider.AbstractIdentityProvider;
import org.keycloak.broker.provider.AuthenticationRequest;
import org.keycloak.broker.provider.IdentityProvider;
import org.keycloak.broker.provider.IdentityProviderFactory;
import org.keycloak.broker.provider.util.IdentityBrokerState;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.services.Urls;
import org.keycloak.services.managers.ClientSessionCode;
import org.keycloak.sessions.AuthenticationSessionModel;
import brave.Span;
import dev.sultanov.keycloak.multitenancy.tracing.TracingHelper;

public class LoginWithSsoAuthenticator implements Authenticator {

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        Span span = TracingHelper.startServerSpan("sso.authenticate");
        Throwable traceError = null;
        try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
            var challenge = context.form().createForm("login-with-sso.ftl");
            context.challenge(challenge);
        } catch (Exception ex) {
            traceError = ex;
            throw ex;
        } finally {
            TracingHelper.finishSpan(span, traceError);
        }
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        Span span = TracingHelper.startServerSpan("sso.action");
        Throwable traceError = null;
        try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
            MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
            var ssoId = formData.getFirst("sso-id");
            if (ssoId != null) {
                span.tag("sso.id", ssoId);
            }
            var identityProviderModel = context.getSession().identityProviders().getAllStream()
                    .filter(idp -> idp.getAlias().equals(ssoId))
                    .filter(IdentityProviderModel::isEnabled)
                    .filter(idp -> !Boolean.TRUE.equals(idp.isLinkOnly()))
                    .findFirst();
            if (identityProviderModel.isPresent()) {
                performLogin(context, identityProviderModel.get());
            } else {
                var response = context.form()
                        .addError(new FormMessage("sso-id", "ssoError"))
                        .createForm("login-with-sso.ftl");
                context.challenge(response);
            }
        } catch (Exception ex) {
            traceError = ex;
            throw ex;
        } finally {
            TracingHelper.finishSpan(span, traceError);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void performLogin(AuthenticationFlowContext context, IdentityProviderModel idp) {
        Span span = TracingHelper.startServerSpan("sso.performLogin");
        Throwable traceError = null;
        try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
            if (idp != null && idp.getAlias() != null) {
                span.tag("idp.alias", idp.getAlias());
            }
            String providerAlias = idp.getAlias();

            var keycloakSession = context.getSession();
            IdentityProviderFactory factory = (IdentityProviderFactory) keycloakSession
                    .getKeycloakSessionFactory()
                    .getProviderFactory(IdentityProvider.class, idp.getProviderId());
            var identityProvider = (AbstractIdentityProvider<?>) factory.create(keycloakSession, idp);
            var authenticationRequest = createAuthenticationRequest(context, providerAlias);
            var response = identityProvider.performLogin(authenticationRequest);
            context.forceChallenge(response);
        } catch (Exception ex) {
            traceError = ex;
            throw ex;
        } finally {
            TracingHelper.finishSpan(span, traceError);
        }
    }

    private AuthenticationRequest createAuthenticationRequest(AuthenticationFlowContext context, String providerId) {
        var realm = context.getRealm();
        var keycloakSession = context.getSession();
        var keycloakUriInfo = keycloakSession.getContext().getUri();
        var redirectUri = Urls.identityProviderAuthnResponse(keycloakUriInfo.getBaseUri(), providerId, realm.getName()).toString();

        var clientSessionCode = new ClientSessionCode<>(keycloakSession, context.getRealm(), context.getAuthenticationSession());
        clientSessionCode.setAction(AuthenticationSessionModel.Action.AUTHENTICATE.name());
        var authSession = clientSessionCode.getClientSession();
        var brokerState = IdentityBrokerState.decoded(
                clientSessionCode.getOrGenerateCode(),
                authSession.getClient().getId(),
                authSession.getClient().getClientId(),
                authSession.getTabId(),
                AuthenticationProcessor.getClientData(keycloakSession, authSession)
        );

        return new AuthenticationRequest(keycloakSession, realm, authSession, context.getHttpRequest(), keycloakUriInfo, brokerState, redirectUri);
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {

    }

    @Override
    public void close() {

    }
}
