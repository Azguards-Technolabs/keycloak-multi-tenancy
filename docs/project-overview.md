# Project Overview

> Generated 2026-06-11 (initial scan, deep / flow-focused).

## What this is
`keycloak-multi-tenancy` — a **Keycloak extension** (Java SPI, packaged as a JAR dropped into Keycloak's `providers/`) that adds **multi-tenant IAM for B2B SaaS within a single realm**. Based on the `anarsultanov/keycloak-multi-tenancy` project. Tenancy is modeled as a first-class `Tenant` SPI (entities, JPA, provider) rather than using Keycloak Organizations or realm-per-tenant.

## Tech stack
| Category | Technology | Version |
|---|---|---|
| Language | Java | (Keycloak 26 baseline) |
| Platform | Keycloak (server SPI) | 26.0.7 |
| Extension version | — | 26.0.16 |
| Build | Maven | — |
| Persistence | Keycloak JPA entity provider | — |
| Templating | FreeMarker (theme-resources) | — |
| Tracing | Zipkin / Brave (`TracingHelper`) | — |
| Tests | JUnit 5, Testcontainers (keycloak), Playwright (browser), Mailhog (mail) | — |

## Capabilities (from README + code)
- Tenant creation during registration (required action)
- Customizable tenant attributes (multi-value, searchable) via admin API
- Tenant invitations (admin API) + invitation review/accept/decline (required action) + invitation emails
- Active-tenant selection at login (required action) and post-login tenant switching (REST, re-mints tokens)
- Tenant-specific IdP config + auto-membership creation; "Login with SSO" by IdP alias
- OIDC token claims: active tenant, all tenants, hardcoded tenant, tenant attribute

## Architecture type
Monolith · single part · project type `extension` (Keycloak server-side SPI plugin). No separate front-end in this repo; consuming applications integrate via OIDC + the REST endpoints.

## Key integration points
- **External "User Service"** REST call during invitation acceptance (`UserServiceRestClient`).
- **Email** (Keycloak email + custom `EmailSender`) for invitation lifecycle.
- **OIDC** claims/attributes consumed by downstream apps (`active_tenant`, all-tenants claim).

## Documentation map
- **[Auth & Onboarding Flows](./auth-flows.md)** ← primary doc for the UX redesign
- [API Contracts](./api-contracts.md)
- [Source Tree Analysis](./source-tree-analysis.md)
- [Architecture](./architecture.md)
- Existing: `openapi.yaml` / `openapi.json` (admin API schema), `README.md`
