package dev.sultanov.keycloak.multitenancy.resource;

import dev.sultanov.keycloak.multitenancy.model.TenantModel;
import dev.sultanov.keycloak.multitenancy.model.TenantProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class TenantsResourceTest {

    @Test
    void listTenants_ShouldCallGetUserTenantsStream_WhenUserIsRegularUser() {
        TenantProvider provider = createMockTenantProvider();
        KeycloakSession session = createMockSession(provider);
        MockTenantAdminAuth auth = new MockTenantAdminAuth(false); // Not manager

        TestTenantsResource resource = new TestTenantsResource(session, provider, auth);

        resource.listTenants(null, null);

        MockTenantProvider mockProvider = (MockTenantProvider) Proxy.getInvocationHandler(provider);
        Assertions.assertTrue(mockProvider.getUserTenantsStreamCalled, "Should call getUserTenantsStream for regular user");
        Assertions.assertFalse(mockProvider.getTenantsStreamCalled, "Should NOT call getTenantsStream for regular user");
    }

    @Test
    void listTenants_ShouldCallGetTenantsStream_WhenUserIsManager() {
        TenantProvider provider = createMockTenantProvider();
        KeycloakSession session = createMockSession(provider);
        MockTenantAdminAuth auth = new MockTenantAdminAuth(true); // Manager

        TestTenantsResource resource = new TestTenantsResource(session, provider, auth);

        resource.listTenants(null, null);

        MockTenantProvider mockProvider = (MockTenantProvider) Proxy.getInvocationHandler(provider);
        Assertions.assertFalse(mockProvider.getUserTenantsStreamCalled, "Should NOT call getUserTenantsStream for manager");
        Assertions.assertTrue(mockProvider.getTenantsStreamCalled, "Should call getTenantsStream for manager");
    }

    static class TestTenantsResource extends TenantsResource {
        public TestTenantsResource(KeycloakSession session, TenantProvider provider, TenantAdminAuth auth) {
            super(session);
            this.tenantProvider = provider;
            this.auth = auth;
            this.session = session;
        }

        @Override
        protected void setup() {
            // Do nothing
        }
    }

    static class MockTenantAdminAuth extends TenantAdminAuth {
        private final boolean isManager;

        public MockTenantAdminAuth(boolean isManager) {
            super(null, null, null, null);
            this.isManager = isManager;
        }

        @Override
        boolean isTenantsManager() {
            return isManager;
        }

        @Override
        boolean isTenantMember(TenantModel tenantModel) {
            return true;
        }

        @Override
        public UserModel getUser() {
            return createMockUser();
        }
    }

    static TenantProvider createMockTenantProvider() {
        return (TenantProvider) Proxy.newProxyInstance(
            TenantsResourceTest.class.getClassLoader(),
            new Class[]{TenantProvider.class},
            new MockTenantProvider()
        );
    }

    static class MockTenantProvider implements java.lang.reflect.InvocationHandler {
        boolean getTenantsStreamCalled = false;
        boolean getUserTenantsStreamCalled = false;

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
            if (method.getName().equals("getTenantsStream")) {
                getTenantsStreamCalled = true;
                return Stream.empty();
            }
            if (method.getName().equals("getUserTenantsStream")) {
                getUserTenantsStreamCalled = true;
                return Stream.empty();
            }
            // For other methods returning primitives/objects, handle defaults
            if (method.getReturnType().equals(boolean.class)) return false;
            if (method.getReturnType().equals(Stream.class)) return Stream.empty();
            if (method.getReturnType().equals(Optional.class)) return Optional.empty();
            if (method.getReturnType().equals(List.class)) return Collections.emptyList();
            return null;
        }
    }

    static KeycloakSession createMockSession(TenantProvider provider) {
        KeycloakContext context = createMockContext();
        return (KeycloakSession) Proxy.newProxyInstance(
            TenantsResourceTest.class.getClassLoader(),
            new Class[]{KeycloakSession.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getContext")) return context;
                if (method.getName().equals("getProvider") && args.length > 0 && args[0].equals(TenantProvider.class)) {
                    return provider;
                }
                return null;
            }
        );
    }

    static KeycloakContext createMockContext() {
         RealmModel realm = createMockRealm();
         return (KeycloakContext) Proxy.newProxyInstance(
            TenantsResourceTest.class.getClassLoader(),
            new Class[]{KeycloakContext.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getRealm")) return realm;
                return null;
            }
        );
    }

    static RealmModel createMockRealm() {
        return (RealmModel) Proxy.newProxyInstance(
            TenantsResourceTest.class.getClassLoader(),
            new Class[]{RealmModel.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getId")) return "realm-id";
                if (method.getName().equals("getName")) return "realm-name";
                return null;
            }
        );
    }

    static UserModel createMockUser() {
        return (UserModel) Proxy.newProxyInstance(
            TenantsResourceTest.class.getClassLoader(),
            new Class[]{UserModel.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getId")) return "user-id";
                if (method.getName().equals("getEmail")) return "user@example.com";
                return null;
            }
        );
    }
}
