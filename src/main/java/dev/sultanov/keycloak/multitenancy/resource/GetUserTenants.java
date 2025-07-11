package dev.sultanov.keycloak.multitenancy.resource;

import dev.sultanov.keycloak.multitenancy.dto.TenantDto;
import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import dev.sultanov.keycloak.multitenancy.util.TokenVerificationUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.*;
import java.util.stream.Collectors;

public class GetUserTenants {

    private static final Logger log = Logger.getLogger(GetUserTenants.class);
    private final KeycloakSession session;

    public GetUserTenants(KeycloakSession session) {
        this.session = session;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMyTenants(@Context HttpHeaders headers) {
        RealmModel realm = session.getContext().getRealm();

        // Verify token using generic utility
        TokenVerificationUtils.TokenVerificationResult verificationResult = 
                TokenVerificationUtils.verifyToken(session, headers);
        if (!verificationResult.isSuccess()) {
            return verificationResult.getErrorResponse();
        }

        UserModel user = verificationResult.getUser();
        String userId = user.getId();

        log.debug("User ID from token: " + userId);
        log.debug("Realm: " + realm.getName());

        TenantProvider tenantProvider = session.getProvider(TenantProvider.class);
        if (ObjectUtils.isEmpty(tenantProvider)) {
            log.error("TenantProvider not available");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Tenant provider not available")
                    .build();
        }

        List<TenantDto> tenants = tenantProvider.getUserTenantsStream(realm, user)
                .map(tenant -> {
                    Map<String, List<String>> attributes = new HashMap<>();
                    tenant.getAttributes().forEach((k, v) -> attributes.put(k, new ArrayList<>(v)));

                    TenantDto tenantDto = new TenantDto(
                            tenant.getId(),
                            tenant.getName(),
                            tenant.getRealm().getName(),
                            tenant.getMobileNumber(),
                            tenant.getCountryCode(), // Include country code
                            tenant.getStatus(),      // Include status
                            attributes
                    );

                    log.debugf("Found tenant: %s (%s) for user %s", tenant.getName(), tenant.getId(), userId);
                    return tenantDto;
                })
                .collect(Collectors.toList());

        log.debugf("Returning %d tenants for user %s", tenants.size(), userId);
        return Response.ok(tenants).build();
    }
}