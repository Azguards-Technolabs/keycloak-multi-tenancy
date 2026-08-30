package com.example.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class TenantContextWebFilter implements GlobalFilter, Ordered {

    private static final String X_TENANT_ID = "X-Tenant-Id";
    private static final String X_USER_ID = "X-User-Id";
    private static final String X_TENANT_ROLES = "X-Tenant-Roles";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .map(auth -> (JwtAuthenticationToken) auth)
                .map(jwtToken -> {
                    Map<String, Object> claims = jwtToken.getTokenAttributes();

                    // The claim is "active_tenant" which is a Map
                    @SuppressWarnings("unchecked")
                    Map<String, Object> activeTenant = (Map<String, Object>) claims.get("active_tenant");

                    if (activeTenant == null || !activeTenant.containsKey("tenant_id")) {
                        throw new TenantEnforcementException(HttpStatus.FORBIDDEN, "Missing tenant context in token");
                    }

                    String tokenTenantId = (String) activeTenant.get("tenant_id");
                    String tenantHint = resolveTenantHint(exchange);

                    if (tenantHint != null && !tenantHint.equals(tokenTenantId)) {
                        throw new TenantEnforcementException(HttpStatus.FORBIDDEN, "Tenant mismatch: Hint " + tenantHint + " != Token " + tokenTenantId);
                    }

                    // Prepare trusted headers
                    String userId = jwtToken.getName(); // 'sub' claim usually
                    List<String> roles = extractRoles(activeTenant);
                    String rolesStr = String.join(",", roles);

                    return exchange.mutate()
                            .request(r -> r.headers(headers -> {
                                // Strip inbound sensitive headers
                                headers.remove(X_TENANT_ID);
                                headers.remove(X_USER_ID);
                                headers.remove(X_TENANT_ROLES);

                                // Inject trusted headers
                                headers.set(X_TENANT_ID, tokenTenantId);
                                headers.set(X_USER_ID, userId);
                                headers.set(X_TENANT_ROLES, rolesStr);
                            }))
                            .build();
                })
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter)
                .onErrorResume(TenantEnforcementException.class, e -> {
                    exchange.getResponse().setStatusCode(e.getStatus());
                    return exchange.getResponse().setComplete();
                });
    }

    private String resolveTenantHint(ServerWebExchange exchange) {
        // 1. Header
        String header = exchange.getRequest().getHeaders().getFirst(X_TENANT_ID);
        if (header != null) return header;

        // 2. Path: /api/{tenant}/...
        String path = exchange.getRequest().getURI().getPath();
        if (path.startsWith("/api/")) {
             String[] parts = path.split("/");
             // parts: ["", "api", "tenant-id", "resource"...]
             if (parts.length > 2) {
                 return parts[2];
             }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Map<String, Object> activeTenant) {
        if (activeTenant.containsKey("roles")) {
            Object rolesObj = activeTenant.get("roles");
            if (rolesObj instanceof List) {
                return (List<String>) rolesObj;
            }
        }
        return Collections.emptyList();
    }

    @Override
    public int getOrder() {
        return 10; // Run after Security (Authentication)
    }

    static class TenantEnforcementException extends RuntimeException {
        private final HttpStatus status;

        public TenantEnforcementException(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }

        public HttpStatus getStatus() { return status; }
    }
}
