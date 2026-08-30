package com.example.gateway.security;

import com.example.gateway.config.PolicyEnforcerConfig;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KeycloakPolicyEnforcementManagerTest {

    private MockWebServer mockWebServer;
    private KeycloakPolicyEnforcementManager manager;

    @BeforeEach
    void setup() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        PolicyEnforcerConfig config = new PolicyEnforcerConfig();
        // The manager appends /realms/... so we just give the base url.
        // Note: The manager implementation: String.format("%s/realms/%s/...", config.getAuthServerUrl(), config.getRealm());
        // So I'll just set the root of mockserver as authServerUrl.
        config.setAuthServerUrl(mockWebServer.url("").toString().replaceAll("/$", "")); // strip trailing slash
        config.setRealm("test-realm");
        config.setPaths(Collections.singletonMap("/api/**", "api-resource"));

        manager = new KeycloakPolicyEnforcementManager(WebClient.builder(), config);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldGrantAccessWhenKeycloakApproves() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"rpt\": \"token\"}"));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AuthorizationContext context = new AuthorizationContext(exchange);

        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").claim("sub", "user").build();
        Authentication auth = new JwtAuthenticationToken(jwt);

        manager.check(Mono.just(auth), context)
                .as(StepVerifier::create)
                .expectNextMatches(decision -> decision.isGranted())
                .verifyComplete();
    }

    @Test
    void shouldDenyAccessWhenKeycloakForbids() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(403));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AuthorizationContext context = new AuthorizationContext(exchange);

        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").claim("sub", "user").build();
        Authentication auth = new JwtAuthenticationToken(jwt);

        manager.check(Mono.just(auth), context)
                .as(StepVerifier::create)
                .expectNextMatches(decision -> !decision.isGranted())
                .verifyComplete();
    }
}
