package dev.sultanov.keycloak.multitenancy.resource;

import org.keycloak.http.HttpRequest;
import org.keycloak.models.KeycloakSession;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@Path("/")
public class MultitenancyRootResource {

	private final KeycloakSession session;

	public MultitenancyRootResource(KeycloakSession session) {
		this.session = session;
	}

	@Path("{any:.*}")
	public Object handleAll() {
		HttpRequest request = session.getContext().getHttpRequest();

		if (request != null && "OPTIONS".equalsIgnoreCase(request.getHttpMethod())) {
			return new CorsResource();
		}

		// Manually route specific endpoints
		String path = session.getContext().getUri().getPath();

		if (path.endsWith("/switch")) {
			return new SwitchActiveTenant(session);
		} else if (path.endsWith("/user-tenants")) {
			return new GetUserTenants(session);
		}

		return Response.status(Response.Status.NOT_FOUND).build();
	}

	// You can keep expanding this with other tenant sub-resources
}
