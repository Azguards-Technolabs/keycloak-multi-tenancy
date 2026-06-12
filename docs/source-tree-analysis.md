# Source Tree Analysis

> `keycloak-multi-tenancy` — Keycloak extension (Java SPI), Maven, Keycloak 26.0.7. Generated 2026-06-11 (deep scan, flow-focused).

```
keycloak-multi-tenancy/
├── pom.xml                         # Maven build; <version>26.0.16; keycloak.version 26.0.7; Playwright + Testcontainers tests
├── Jenkinsfile                     # CI
├── docs/                           # ← generated documentation + openapi.{yaml,json}
└── src/
    ├── main/
    │   ├── java/dev/sultanov/keycloak/multitenancy/
    │   │   ├── authentication/
    │   │   │   ├── authenticators/        # ★ LoginWithSso*, IdpTenantMembershipsCreating* (+ factories)
    │   │   │   ├── requiredactions/       # ★ CreateTenant, ReviewTenantInvitations, SelectActiveTenant
    │   │   │   ├── IdentityProviderTenantsConfig.java   # tenant-specific IdP config helper
    │   │   │   └── TenantsBean.java        # view-model for select/review FTL templates
    │   │   ├── resource/                   # ★ REST API
    │   │   │   ├── MultitenancyRootResource.java   # manual router (/switch, /user-tenants, OPTIONS→CORS)
    │   │   │   ├── TenantsResource / TenantResource
    │   │   │   ├── TenantInvitationsResource / TenantMembershipsResource
    │   │   │   ├── SwitchActiveTenant / GetUserTenants     # token-verified user endpoints
    │   │   │   ├── AbstractAdminResource / TenantAdminAuth # admin auth base
    │   │   │   ├── TokenManager / TokenVerificationUtils   # token mint/verify
    │   │   │   ├── UserServiceRestClient.java   # ← EXTERNAL "User Service" call (invitation acceptance)
    │   │   │   └── representation/          # TenantRepresentation, TenantInvitation/Membership, UserMembership
    │   │   ├── protocol/oidc/mappers/       # ★ ActiveTenant, AllTenants, HardcodedTenant, TenantAttribute (+ ClaimsFactory)
    │   │   ├── model/                       # TenantModel/Provider/Spi + entity/ + jpa/ (TenantEntity, Invitation, Membership, Attribute)
    │   │   ├── email/                       # EmailSender, EmailRecipient
    │   │   ├── dto/                         # TenantDto, InvitationRequest, BusinessStatus*
    │   │   ├── tracing/                     # TracingConfig, TracingHelper (Zipkin/Brave)
    │   │   └── util/                        # Constants, TokenVerificationUtils
    │   └── resources/
    │       ├── META-INF/services/          # ★ SPI registrations (RequiredActionFactory, AuthenticatorFactory, ProtocolMapper, Spi, JpaEntityProvider, RealmResourceProviderFactory, TenantProviderFactory)
    │       └── theme-resources/
    │           ├── templates/              # ★ create-tenant.ftl, select-tenant.ftl, review-invitations.ftl, login-with-sso.ftl + email html/text
    │           └── messages/               # messages_en.properties, messages_sv.properties
    └── test/java/.../multitenancy/
        ├── ApiIntegrationTest / BrowserIntegrationTest / IdentityProviderIntegrationTest / MailIntegrationTest
        ├── TenantAttributesTest / TenantCreationRbacIntegrationTest
        └── support/                        # Playwright page objects (SignInPage, SelectTenantPage, CreateTenantPage,
                                            #   ReviewInvitationsPage, SelectLoginMethodPage, …), Testcontainers, Mailhog client
```

★ = directly relevant to the login/onboarding flows being redesigned.

## Entry points
- **SPI registration:** files under `src/main/resources/META-INF/services/` — these wire required actions, authenticators, protocol mappers, the realm resource provider, JPA entities, and the custom Tenant SPI into Keycloak.
- **REST entry:** `RealmResourceProviderFactory` → `MultitenancyRootResource` (routes user endpoints) + JAX-RS admin resources.
- **Auth flow entry:** required actions + authenticators triggered during Keycloak's standard login/registration flow.

## Where to work for the UX redesign
- **User-facing screens:** `theme-resources/templates/*.ftl` + `messages_*.properties`
- **Flow logic / step ordering:** `authentication/requiredactions/*` and `authentication/authenticators/*`
- **Tenant data for the front-end:** `resource/GetUserTenants` + `SwitchActiveTenant` + OIDC mappers
