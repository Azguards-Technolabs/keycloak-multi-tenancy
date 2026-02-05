package com.example.gateway.security;

import com.example.gateway.config.PolicyEnforcerConfig;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class KeycloakPolicyEnforcementManager implements ReactiveAuthorizationManager<AuthorizationContext> {

    private final WebClient webClient;
    private final PolicyEnforcerConfig config;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public KeycloakPolicyEnforcementManager(WebClient.Builder webClientBuilder, PolicyEnforcerConfig config) {
        this.webClient = webClientBuilder.build();
        this.config = config;
    }

    @Override
    public Mono<AuthorizationDecision> check(Mono<Authentication> authentication, AuthorizationContext context) {
        return authentication
                .filter(a -> a instanceof JwtAuthenticationToken)
                .flatMap(auth -> {
                    JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) auth;
                    String token = jwtAuth.getToken().getTokenValue();
                    String path = context.getExchange().getRequest().getURI().getPath();

                    String resource = resolveResource(path);

                    if (resource == null) {
                        // For paths not mapped to a Keycloak resource, we default to granting access
                        // (relying on standard authentication).
                        // Adjust logic here if "Deny by default" is required for unmapped resources.
                        return Mono.just(new AuthorizationDecision(true));
                    }

                    return checkPermissions(token, resource);
                })
                .defaultIfEmpty(new AuthorizationDecision(false));
    }

    private String resolveResource(String path) {
        if (config.getPaths() == null) return null;

        // Find best match.
        // Note: Simple iteration. Specificity logic (longest match) might be needed for complex cases.
        for (Map.Entry<String, String> entry : config.getPaths().entrySet()) {
            if (pathMatcher.match(entry.getKey(), path)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Mono<AuthorizationDecision> checkPermissions(String accessToken, String resource) {
        String url = String.format("%s/realms/%s/protocol/openid-connect/token",
                config.getAuthServerUrl(), config.getRealm());

        // Call Keycloak Authorization Endpoint
        return webClient.post()
                .uri(url)
                .headers(h -> h.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "urn:ietf:params:oauth:grant-type:uma-ticket")
                        .with("permission", resource))
                .retrieve()
                .toBodilessEntity()
                .map(response -> new AuthorizationDecision(response.getStatusCode().is2xxSuccessful()))
                .onErrorResume(WebClientResponseException.class, e -> {
                    // 403 means Policy Denied
                    if (e.getStatusCode().value() == 403) {
                        return Mono.just(new AuthorizationDecision(false));
                    }
                    // Other errors (401, 500) -> Deny and log?
                    return Mono.just(new AuthorizationDecision(false));
                })
                .onErrorReturn(new AuthorizationDecision(false));
    }
}
