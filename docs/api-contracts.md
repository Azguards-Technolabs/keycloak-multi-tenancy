# API Contracts

> REST endpoints exposed by the `keycloak-multi-tenancy` extension via a Keycloak `RealmResourceProvider`. Generated 2026-06-11 (deep scan, flow-focused). See also `docs/openapi.yaml` / `docs/openapi.json` for the admin API schema.

## Two surfaces

The extension exposes endpoints under a realm resource provider, split by auth model:

1. **Admin API** — extends `AbstractAdminResource<TenantAdminAuth>`; requires admin auth (e.g. `manage-tenants` / tenant-admin roles). Annotated with MicroProfile OpenAPI; reflected in `docs/openapi.*`.
2. **Token-verified user API** — manually routed by `MultitenancyRootResource.handleAll()` for paths ending in `/switch` and `/user-tenants`; verifies a bearer token via `TokenVerificationUtils`; CORS enabled (`allowAllOrigins`).

`MultitenancyRootResource` also short-circuits `OPTIONS` requests to a `CorsResource` for preflight.

## Admin API

### Tenants (`TenantsResource`)
| Method | Path | Operation | Notes |
|---|---|---|---|
| POST | `/tenants` | `createTenant` | Body `TenantRepresentation`; **requires** name, mobileNumber, countryCode; validates attributes; 201 with created tenant |
| GET | `/tenants` | `listTenants` | Optional `mobileNumber`, `countryCode` query filters (exact match) |
| GET | `/tenants/users/{userId}` | `listMembershipsByUserId` | Memberships for a user |
| DELETE | `/tenants/{tenantId}/memberships/users/{userId}` | `revokeMembershipByUserId` | 204 / 404 |

### Single tenant (`TenantResource`, mounted at `/tenants/{tenantId}`)
| Method | Path | Operation |
|---|---|---|
| GET | `/tenants/{tenantId}` | `getTenant` |
| PUT | `/tenants/{tenantId}` | `updateTenant` (name, mobileNumber, countryCode, status, attributes; diff-removes missing attrs) |
| DELETE | `/tenants/{tenantId}` | `deleteTenant` |

### Invitations (`TenantInvitationsResource`, at `/tenants/{tenantId}/invitations`)
| Method | Path | Operation | Notes |
|---|---|---|---|
| POST | `.../invitations` | `createInvitation` | Validates email; 409 if invite exists or already a member; **sends invitation email**; admin event |
| GET | `.../invitations` | `listInvitations` | `search` (email contains), `first`, `max` (default 100) |
| DELETE | `.../invitations/{invitationId}` | `revokeInvitation` | 204 / 404 |

### Memberships (`TenantMembershipsResource`, at `/tenants/{tenantId}/memberships`)
Sub-resource for membership management (mounted from `TenantResource.memberships()`).

## Token-verified user API (manually routed)
| Method | Path (suffix) | Class | Purpose |
|---|---|---|---|
| PUT | `…/switch` | `SwitchActiveTenant` | Switch active tenant: validates membership, sets `active_tenant` user attr + `active-tenant-id` session note, **re-mints tokens**, returns them (CORS). Fallback response asks client to refresh tokens if mint fails |
| GET | `…/user-tenants` | `GetUserTenants` | Returns the caller's tenants as `TenantDto[]` (id, name, realm, mobileNumber, countryCode, status, attributes) |

## Observability
All admin tenant operations wrap logic in Zipkin/Brave spans via `TracingHelper` (`tenant.create`, `tenant.list`, `tenant.get`, `tenant.update`, `tenant.delete`, `tenant.switch`, `tenant.memberships.byUser`, `tenant.membership.revoke`).

## Notes / risks
- CORS is `allowAllOrigins()` on the user-facing endpoints — tighten for production.
- `createInvitation` sends email synchronously inside the request.
- The admin `createTenant` requires mobileNumber + countryCode, but the **UI `create-tenant` required action** submits them empty — divergent contracts between the two tenant-creation paths.
