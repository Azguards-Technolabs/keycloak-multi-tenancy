package dev.sultanov.keycloak.multitenancy.resource;

import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.resource.representation.TenantRepresentation;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import brave.Span;
import dev.sultanov.keycloak.multitenancy.tracing.TracingHelper;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.ObjectUtils;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.keycloak.events.admin.OperationType;

public class TenantResource extends AbstractAdminResource<TenantAdminAuth> {

    private final TenantModel tenant;

    public TenantResource(AbstractAdminResource<TenantAdminAuth> parent, TenantModel tenant) {
        super(parent);
        this.tenant = tenant;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getTenant", summary = "Get tenant")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(type = SchemaType.OBJECT, implementation = TenantRepresentation.class))),
            @APIResponse(responseCode = "401", description = "Unauthorized"),
            @APIResponse(responseCode = "403", description = "Forbidden"),
            @APIResponse(responseCode = "404", description = "Not Found")
    })
    public TenantRepresentation getTenant(@jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers) {
        Span span = TracingHelper.startServerSpan("tenant.get", headers);
        Throwable traceError = null;
        try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
            span.tag("tenant.id", tenant.getId());
            return ModelMapper.toRepresentation(tenant);
        } catch (Exception e) {
            traceError = e;
            throw e;
        } finally {
            TracingHelper.finishSpan(span, traceError);
        }
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(value = MediaType.APPLICATION_JSON)
    @Operation(operationId = "updateTenant", summary = "Update tenant")
    @APIResponses({
            @APIResponse(responseCode = "204", description = "No Content"),
            @APIResponse(responseCode = "401", description = "Unauthorized"),
            @APIResponse(responseCode = "403", description = "Forbidden"),
            @APIResponse(responseCode = "404", description = "Not Found")
    })
    public Response updateTenant(@jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers, TenantRepresentation request) {
        Span span = TracingHelper.startServerSpan("tenant.update", headers);
        Throwable traceError = null;
        try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
            span.tag("tenant.id", tenant.getId());
            if (ObjectUtils.isNotEmpty(request.getName())) {
                boolean nameExists = tenantProvider.getTenantsStream(realm, null, null, null, null)
                        .anyMatch(t -> t.getName().equalsIgnoreCase(request.getName()) && !t.getId().equals(tenant.getId()));
                if (nameExists) {
                    return Response.status(Response.Status.CONFLICT).entity("A tenant with this name already exists.").build();
                }
                tenant.setName(request.getName());
            }
            if (ObjectUtils.isNotEmpty(request.getMobileNumber())) {
                tenant.setMobileNumber(request.getMobileNumber());
            }
            if (ObjectUtils.isNotEmpty(request.getCountryCode())) {
                tenant.setCountryCode(request.getCountryCode());
            }
            if (ObjectUtils.isNotEmpty(request.getStatus())) {
                tenant.setStatus(request.getStatus());
            }

            if (request.getAttributes() != null) {
                Set<String> attrsToRemove = new HashSet<>(tenant.getAttributes().keySet());
                attrsToRemove.removeAll(request.getAttributes().keySet());

                for (Map.Entry<String, List<String>> attr : request.getAttributes().entrySet()) {
                    tenant.setAttribute(attr.getKey(), attr.getValue());
                }
                for (String attr : attrsToRemove) {
                    tenant.removeAttribute(attr);
                }
            }

            adminEvent.operation(OperationType.UPDATE)
                    .resourcePath(session.getContext().getUri())
                    .representation(ModelMapper.toRepresentation(tenant))
                    .success();

            return Response.noContent().build();
        } catch (Exception e) {
            traceError = e;
            throw e;
        } finally {
            TracingHelper.finishSpan(span, traceError);
        }
    }


    @DELETE
    @Operation(operationId = "deleteTenant", summary = "Delete tenant")
    @APIResponses({
            @APIResponse(responseCode = "204", description = "No Content"),
            @APIResponse(responseCode = "401", description = "Unauthorized"),
            @APIResponse(responseCode = "403", description = "Forbidden")
    })
    public void deleteTenant(@jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers) {
        Span span = TracingHelper.startServerSpan("tenant.delete", headers);
        Throwable traceError = null;
        try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
            span.tag("tenant.id", tenant.getId());
            if (tenantProvider.deleteTenant(realm, tenant.getId())) {
                adminEvent.operation(OperationType.DELETE)
                        .resourcePath(session.getContext().getUri())
                        .success();
            }
        } catch (Exception e) {
            traceError = e;
            throw e;
        } finally {
            TracingHelper.finishSpan(span, traceError);
        }
    }

    @Path("invitations")
    public TenantInvitationsResource invitations() {
        return new TenantInvitationsResource(this, tenant);
    }

    @Path("memberships")
    public TenantMembershipsResource memberships() {
        return new TenantMembershipsResource(this, tenant);
    }
}