# Architecture

> Generated 2026-06-11 (deep scan, flow-focused). Single-part Keycloak extension.

## Executive summary
The project is a Keycloak server-side **extension (SPI plugin)** providing single-realm multi-tenancy. It plugs into Keycloak's extension points — a custom **Tenant SPI**, **required actions**, **authenticators**, **OIDC protocol mappers**, a **JPA entity provider**, and a **realm REST resource** — so that a single Keycloak realm hosts many tenants, each user has one identity, and tokens carry tenant context.

## Architecture pattern
**Plugin / provider architecture** layered on Keycloak's SPI model. Components are discovered via `META-INF/services` factory registrations and invoked by the Keycloak server at well-defined lifecycle points (authentication flow, token issuance, admin REST, persistence).

## Layers / components
| Layer | Components | Responsibility |
|---|---|---|
| Domain SPI | `TenantSpi`, `TenantProvider(Factory)`, `TenantModel`, `TenantMembershipModel`, `TenantInvitationModel` | Tenant aggregate + provider contract |
| Persistence | `model/entity/*`, `model/jpa/*` (`JpaTenantProvider`, adapters), `JpaEntityProviderFactory` | JPA-backed tenant/membership/invitation/attribute storage |
| Authentication | `requiredactions/*`, `authenticators/*`, `TenantsBean`, `IdentityProviderTenantsConfig` | Tenant creation, invitation review, active-tenant selection, SSO-by-alias, IdP membership creation |
| Token | `protocol/oidc/mappers/*`, `ClaimsFactory`, `TokenManager`, `TokenVerificationUtils` | Tenant claims; token mint/verify on switch |
| REST API | `resource/*` (admin + token-verified), `MultitenancyRootResource`, `Cors*` | Tenant/invitation/membership CRUD; active-tenant switch; user-tenants |
| Cross-cutting | `email/*`, `tracing/*`, `util/Constants`, `UserServiceRestClient` | Email, Zipkin tracing, constants, external user-service integration |
| Presentation | `theme-resources/templates/*.ftl`, `messages/*` | FreeMarker login/onboarding screens + i18n |

## Data architecture
Tenant, TenantMembership, TenantInvitation, TenantAttribute entities persisted via Keycloak's JPA entity provider (same datastore as Keycloak). Membership links Keycloak `UserModel` ↔ tenant with roles (`tenant-admin`, `tenant-user`). Active tenant tracked as user attribute `active_tenant` + user-session note `active-tenant-id`.

## Authentication architecture (see auth-flows.md for detail)
Required actions run in mandated order: review invitations → create tenant → select active tenant. Authenticators add SSO-by-alias login and post-broker IdP membership creation. Tenant context flows into tokens via OIDC mappers.

## Deployment
Built with Maven (`mvn package`) → JAR copied to Keycloak `providers/` → `kc.sh build`. Versioned to track the Keycloak major version (currently 26.0.x). CI via Jenkinsfile.

## Testing strategy
Integration-heavy: Testcontainers spins up Keycloak; Playwright drives browser flows (page objects for sign-in, select-tenant, create-tenant, review-invitations, SSO); Mailhog asserts invitation emails; RBAC and attribute tests.

## Key constraints for the UX redesign
- **Keycloak 26.0.7** → no native passkeys (needs 26.4+).
- Login/onboarding UI is **FreeMarker with hardcoded inline CSS**, not theme-variable driven → limits per-tenant branding until refactored.
- Onboarding depends on an **external User Service** call during invitation acceptance.
- See **[auth-flows.md](./auth-flows.md)** for the full flow map and per-screen redesign notes.
