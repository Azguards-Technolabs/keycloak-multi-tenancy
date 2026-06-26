# Story (DRAFT STUB): Tenant Search, RBAC, Pagination & Name-Uniqueness API

Status: backlog

> ⚠️ **Provenance:** Split out from the code review of `1-6-production-hygiene-observability-test-harness-baseline` on 2026-06-13.
> Story 1.6 was scoped to production hygiene / observability / test-harness, but its working tree
> bundled this untested feature work. This stub captures the feature so it can be reviewed and
> shipped on its own. **PM action required:** assign this to the correct epic (it does not fit
> Epic 1's "Platform Upgrade & Design System" theme — it is a tenant-management API feature).

## Story

As a tenant administrator / API consumer,
I want to search and paginate tenants, filter by attributes, enforce a creation role (RBAC), and reject duplicate tenant names,
So that tenant management scales and stays consistent.

## Source Files to Move Out of the 1.6 Change

**Main code — move wholesale:**
- `src/main/java/dev/sultanov/keycloak/multitenancy/model/jpa/JpaTenantProvider.java` — `getTenantsStream` `nameOrIdQuery`/attribute filtering; `setStatus("ACTIVE")` default
- `src/main/java/dev/sultanov/keycloak/multitenancy/resource/TenantsResource.java` — `search`/`q`/`first`/`max` params, RBAC `requiredRoleForTenantCreation` check, name-uniqueness CONFLICT, `Response.created(location)`
- `src/main/java/dev/sultanov/keycloak/multitenancy/resource/TenantResource.java` — name-uniqueness CONFLICT on update; `attributes != null` handling
- `src/main/java/dev/sultanov/keycloak/multitenancy/resource/representation/TenantRepresentation.java` — `attributes` default (`new HashMap<>()` → `null`)
- `docs/openapi.json`, `docs/openapi.yaml` — regenerated for the new params (re-generate, don't hand-merge)

**Main code — HUNK-LEVEL split (file is shared with 1.6 tracing):**
- `src/main/java/dev/sultanov/keycloak/multitenancy/authentication/requiredactions/CreateTenant.java`
  - **Stays in 1.6:** the `TracingHelper` span wrapping of `evaluateTriggers`/`requiredActionChallenge`/`processAction`.
  - **Moves here:** the `processAction` body change — deriving `mobileNumber` from `user.getFirstAttribute("mobileNumber")` with the `System.currentTimeMillis()+random` fallback, `countryCode` default `"91"`, and `status = "ACTIVE"`. In 1.6 this method should keep tracing + the **original** body (`mobileNumber=""`, `countryCode=""`, `status=""`).

**Tests — move with the feature:**
- `src/test/java/dev/sultanov/keycloak/multitenancy/TenantAttributesTest.java` — exercises attribute search + `listTenants(...)` pagination signature
- `src/test/java/dev/sultanov/keycloak/multitenancy/TenantCreationRbacIntegrationTest.java` — sets `mobileNumber`/`countryCode`/`status` on requests (feature-coupled)

**Stays in 1.6 (UI-adaptation / harness — do NOT move):** `BrowserIntegrationTest`, `ApiIntegrationTest`, `IdentityProviderIntegrationTest`, `MailIntegrationTest`, `ReviewInvitationsPage`, `SelectTenantPage`, `SingleSignOnPage`, `PageResolver`, `BaseIntegrationTest`, `pom.xml`/`dependency-reduced-pom.xml` (KC 26.6.3 + tracing shade), `messages_en.properties`, `TracingHelper`, `ReviewTenantInvitations`, `SelectActiveTenant`, `LoginWithSsoAuthenticator`, `IdpTenantMembershipsCreatingAuthenticator`.

## Acceptance Criteria

1. Tenants can be searched by name/ID substring and filtered by attribute key:value via `GET /tenants?search=&q=&first=&max=`.
2. Tenant creation is gated by `requiredRoleForTenantCreation` (403 Forbidden otherwise).
3. Creating/updating a tenant with a name already in use returns `409 Conflict`.
4. Pagination semantics are well-defined and bounded (see bug #4 below).
5. All new behavior is covered by integration tests; `mvn verify` passes.

## Must-Fix Bugs Carried From the 1.6 Review (do not re-introduce)

- [ ] **[HIGH] Dead duplicate-name check** — `CreateTenant.processAction` passes `mobileNumber` as the `nameOrIdQuery` arg of `getTenantsStream(realm, mobileNumber, null, mobileNumber, countryCode)`. The `nameOrIdQuery` filter then requires the tenant name/ID to *contain* the mobile number, so an existing same-named tenant is filtered out and the "tenant already exists" guard never fires. **Fix: pass `null` as `nameOrIdQuery`.** Also reconsider generating a random `mobileNumber` and forcing `countryCode="91"` — that persists synthetic data.
- [ ] **[MED] `q` query parser** — `TenantsResource.listTenants` splits `q` on spaces before `:`, so attribute values containing spaces are truncated and pairs without `:` are silently dropped; `:value` yields an empty-string key. Validate and parse robustly (or return 400 on malformed `q`).
- [ ] **[MED] Empty attributes map wipes all attributes** — `TenantResource` uses `if (request.getAttributes() != null)` then `removeAll`, so `attributes:{}` deletes every existing attribute. **Decision from 1.6 review: revert to no-op on empty map** (restore the `isNotEmpty` guard); keep `TenantRepresentation.attributes` non-null-safe for readers.
- [ ] **[LOW] `getName()` NPE risk** — name filters/uniqueness checks (`JpaTenantProvider`, `TenantResource`, `TenantsResource`) dereference `t.getName()` without a null guard.
- [ ] **[LOW] Pagination bounds** — `max=0`/negative are silently ignored (returns full list); no upper cap allows full-table materialization. Define & enforce bounds; push search/attribute predicates down to SQL instead of in-memory stream filtering.

## Notes

- The in-memory filtering in `getTenantsStream` (filter applied after `getResultStream()` materializes the whole table) should be reworked into JPA `CriteriaBuilder` predicates for scalability.
- Regenerate `docs/openapi.*` from the finalized endpoints rather than carrying the bundled regen.
