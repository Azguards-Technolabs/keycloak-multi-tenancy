package com.example.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class TenantContextWebFilterTest {

    private final TenantContextWebFilter filter = new TenantContextWebFilter();
    private final GatewayFilterChain chain = mock(GatewayFilterChain.class);

    @Test
    void shouldAllowWhenTenantMatches() {
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/tenant-1/resource")
                .header("X-Tenant-Id", "tenant-1")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Jwt jwt = createJwt("tenant-1");
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);

        filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(new SecurityContextImpl(auth))))
                .as(StepVerifier::create)
                .verifyComplete();

        verify(chain).filter(argThat(ex -> {
             return "tenant-1".equals(ex.getRequest().getHeaders().getFirst("X-Tenant-Id"));
        }));
    }

    @Test
    void shouldRejectWhenTenantMismatch() {
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/tenant-1/resource")
                .header("X-Tenant-Id", "tenant-2") // Mismatch
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Jwt jwt = createJwt("tenant-1");
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);

        filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(new SecurityContextImpl(auth))))
                .as(StepVerifier::create)
                .verifyComplete();

        assert exchange.getResponse().getStatusCode() == HttpStatus.FORBIDDEN;
    }

    private Jwt createJwt(String tenantId) {
        Map<String, Object> claims = new HashMap<>();
        Map<String, Object> activeTenant = new HashMap<>();
        activeTenant.put("tenant_id", tenantId);
        activeTenant.put("roles", Collections.singletonList("ADMIN"));
        claims.put("active_tenant", activeTenant);
        claims.put("sub", "user-123");

        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claims(c -> c.putAll(claims))
                .build();
    }
}
