package com.example.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import com.example.gateway.security.KeycloakPolicyEnforcementManager;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http, KeycloakPolicyEnforcementManager policyEnforcementManager) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                // Health endpoints
                .pathMatchers("/actuator/**").permitAll()
                // Apply Keycloak Policy Enforcement
                .anyExchange().access(policyEnforcementManager)
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );

        return http.build();
    }

    @Bean
    public Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new TenantRoleConverter());
        return new ReactiveJwtAuthenticationConverterAdapter(jwtAuthenticationConverter);
    }

    /**
     * Extracts roles from 'active_tenant.roles' and 'realm_access.roles'
     */
    static class TenantRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Collection<GrantedAuthority> authorities = new ArrayList<>();

            // 1. Realm Roles
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            authorities.addAll(extractRoles(realmAccess, "ROLE_REALM_"));

            // 2. Active Tenant Roles
            Map<String, Object> activeTenant = jwt.getClaim("active_tenant");
            authorities.addAll(extractRoles(activeTenant, "ROLE_TENANT_"));

            return authorities;
        }

        @SuppressWarnings("unchecked")
        private Collection<GrantedAuthority> extractRoles(Map<String, Object> source, String prefix) {
            if (source == null || !source.containsKey("roles")) {
                return Collections.emptyList();
            }
            Object rolesObj = source.get("roles");
            if (rolesObj instanceof Collection<?>) {
                return ((Collection<String>) rolesObj).stream()
                        .map(role -> new SimpleGrantedAuthority(prefix + role.toUpperCase()))
                        .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }
    }
}
