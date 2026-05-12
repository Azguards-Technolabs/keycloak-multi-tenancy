package dev.sultanov.keycloak.multitenancy.resource;

import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import dev.sultanov.keycloak.multitenancy.resource.representation.TenantRepresentation;
import dev.sultanov.keycloak.multitenancy.resource.representation.UserMembershipRepresentation;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.models.KeycloakSession;
import org.apache.commons.lang3.ObjectUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import brave.Span;
import dev.sultanov.keycloak.multitenancy.tracing.TracingHelper;

@Slf4j
public class TenantsResource extends AbstractAdminResource<TenantAdminAuth> {

    public TenantsResource(KeycloakSession session) {
        super(session);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "createTenant", summary = "Create a tenant")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = TenantRepresentation.class))),
            @APIResponse(responseCode = "400", description = "Bad Request"),
            @APIResponse(responseCode = "401", description = "Unauthorized"),
            @APIResponse(responseCode = "403", description = "Forbidden"),
            @APIResponse(responseCode = "409", description = "Conflict"),
            @APIResponse(responseCode = "500", description = "Internal Server Error")
    })
    public Response createTenant(@jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers, @RequestBody(required = true) TenantRepresentation request) {
        Span span = TracingHelper.startServerSpan("tenant.create", headers);
        Throwable traceError = null;
        try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
            if (ObjectUtils.isEmpty(request)) {
                log.error("Tenant representation cannot be null");
                throw new BadRequestException("Tenant representation cannot be null");
            }
            if (isNullOrWhitespace(request.getName())) {
                log.error("Tenant name cannot be null or empty");
                throw new BadRequestException("Tenant name cannot be null or empty");
            }
            if (isNullOrWhitespace(request.getMobileNumber())) {
                log.error("Tenant mobile number cannot be null or empty");
                throw new BadRequestException("Tenant mobile number cannot be null or empty");
            }
            if (isNullOrWhitespace(request.getCountryCode())) {
                log.error("Tenant country code cannot be null or empty");
                throw new BadRequestException("Tenant country code cannot be null or empty");
            }

            validateAttributes(request.getAttributes());


            TenantModel tenant = tenantProvider.createTenant(realm, request.getName(), request.getMobileNumber(), request.getCountryCode(), request.getStatus(), auth.getUser());

            if (ObjectUtils.isNotEmpty(request.getAttributes())) {
                for (Map.Entry<String, List<String>> attr : request.getAttributes().entrySet()) {
                    tenant.setAttribute(attr.getKey(), attr.getValue());
                }
            }

            log.info("Tenant created successfully: {}", tenant.getName());
            return Response.status(Response.Status.CREATED).entity(ModelMapper.toRepresentation(tenant)).build();
        } catch (Exception e) {
            traceError = e;
            throw e;
        } finally {
            TracingHelper.finishSpan(span, traceError);
        }
    }

    @Path("{tenantId}")
    public TenantResource getTenantResource(@PathParam("tenantId") String tenantId) {
        TenantModel tenant = tenantProvider.getTenantById(realm, tenantId)
                .orElseThrow(() -> new NotFoundException("Tenant not found"));
        return new TenantResource(this, tenant);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "listTenants", summary = "List tenants")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TenantRepresentation.class))),
            @APIResponse(responseCode = "401", description = "Unauthorized"),
            @APIResponse(responseCode = "403", description = "Forbidden"),
            @APIResponse(responseCode = "500", description = "Internal Server Error")
    })
    public List<TenantRepresentation> listTenants(
            @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
            @Parameter(description = "Tenant mobile number (exact match)") @QueryParam("mobileNumber") String mobileNumber,
            @Parameter(description = "Tenant country code (exact match, e.g., 91)") @QueryParam("countryCode") String countryCode) {

        Span span = TracingHelper.startServerSpan("tenant.list", headers);
        Throwable traceError = null;
        try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
            log.debug("Listing tenants with mobileNumber: {}, countryCode: {}", mobileNumber, countryCode);

            // We MUST collect into a List here, otherwise the span finishes before the stream is consumed by JAX-RS
            return tenantProvider.getTenantsStream(realm, null, null, mobileNumber, countryCode)
                    .map(ModelMapper::toRepresentation)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            traceError = e;
            throw e;
        } finally {
            TracingHelper.finishSpan(span, traceError);
        }
    }

    @GET
    @Path("users/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "listMembershipsByUserId", summary = "List memberships by user ID")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = UserMembershipRepresentation.class))),
            @APIResponse(responseCode = "401", description = "Unauthorized"),
            @APIResponse(responseCode = "403", description = "Forbidden"),
            @APIResponse(responseCode = "500", description = "Internal Server Error")
    })
    public List<UserMembershipRepresentation> listMembershipsByUserId(
            @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
            @Parameter(description = "User ID to fetch associated memberships") @PathParam("userId") String userId) {
        Span span = TracingHelper.startServerSpan("tenant.memberships.byUser", headers);
        Throwable traceError = null;
        try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
            log.debug("Listing memberships for user ID: {}", userId);

            return tenantProvider.listMembershipsByUserId(realm, userId);
        } catch (Exception e) {
            traceError = e;
            throw e;
        } finally {
            TracingHelper.finishSpan(span, traceError);
        }
    }

    @DELETE
    @Path("{tenantId}/memberships/users/{userId}")
    @Operation(operationId = "revokeMembershipByUserId", summary = "Revoke membership by user ID")
    @APIResponses({
            @APIResponse(responseCode = "204", description = "No Content"),
            @APIResponse(responseCode = "401", description = "Unauthorized"),
            @APIResponse(responseCode = "403", description = "Forbidden"),
            @APIResponse(responseCode = "500", description = "Internal Server Error")
    })
    public Response revokeMembershipByUserId(
            @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
            @Parameter(description = "Tenant ID") @PathParam("tenantId") String tenantId,
            @Parameter(description = "User ID to revoke membership") @PathParam("userId") String userId) {
        Span span = TracingHelper.startServerSpan("tenant.membership.revoke", headers);
        Throwable traceError = null;
        try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
            log.debug("Attempting to revoke membership for tenant ID: {} and user ID: {}", tenantId, userId);
            
            if (tenantProvider.revokeMembership(realm, tenantId, userId)) {
                log.info("Membership revoked successfully for tenant ID: {} and user ID: {}", tenantId, userId);
                return Response.noContent().build();
            } else {
                log.warn("Membership not found for tenant ID: {} and user ID: {}", tenantId, userId);
                throw new NotFoundException("Membership not found");
            }
        } catch (Exception e) {
            traceError = e;
            throw e;
        } finally {
            TracingHelper.finishSpan(span, traceError);
        }
    }

    private boolean isNullOrWhitespace(String str) {
        return ObjectUtils.isEmpty(str) || str.trim().isEmpty();
    }

    private void validateAttributes(Map<String, List<String>> attributes) {
        if (ObjectUtils.isNotEmpty(attributes)) {
            attributes.forEach((key, values) -> {
                if (isNullOrWhitespace(key)) {
                    log.error("Attribute name cannot be null or empty");
                    throw new BadRequestException("Attribute name cannot be null or empty");
                }
            });
        }
    }
}
