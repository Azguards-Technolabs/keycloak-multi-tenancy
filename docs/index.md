# Project Documentation Index

> Generated 2026-06-11 · initial scan · deep depth (focused on login/onboarding flows). Primary entry point for AI-assisted development.

## Project Overview
- **Type:** Monolith (single part) — Keycloak extension (Java SPI plugin)
- **Primary Language:** Java · **Platform:** Keycloak 26.0.7 · **Extension version:** 26.0.16
- **Architecture:** Plugin/provider architecture on Keycloak's SPI model
- **Purpose:** Single-realm multi-tenant IAM for B2B SaaS (custom Tenant SPI — *not* Keycloak Organizations, *not* realm-per-tenant)

## Quick Reference
- **Tech Stack:** Java · Keycloak SPI · JPA · FreeMarker · Zipkin/Brave · Maven · JUnit5/Testcontainers/Playwright
- **Auth flow entry:** required actions (`review-tenant-invitations` → `create-tenant` → `select-active-tenant`) + authenticators (`login-with-sso`, IdP membership creation)
- **REST entry:** `RealmResourceProviderFactory` → `MultitenancyRootResource`

## Generated Documentation
- [Project Overview](./project-overview.md)
- [Architecture](./architecture.md)
- **[Auth & Onboarding Flows](./auth-flows.md)** ⭐ — the flow map for the UX redesign
- [API Contracts](./api-contracts.md)
- [Source Tree Analysis](./source-tree-analysis.md)

## Existing Documentation
- [README](../README.md) — features, compatibility, installation, configuration
- [OpenAPI spec](./openapi.yaml) (`openapi.json`) — admin REST API schema

## Related Planning Artifacts
- [Market research: Login & Onboarding UX](../_bmad-output/planning-artifacts/research/market-login-onboarding-ux-for-keycloak-based-b2b-saas-research-2026-06-11.md) — the research driving this redesign

## Getting Started (for the UX redesign)
1. Read **auth-flows.md** — it maps every login/onboarding screen, the required-action order, triggers, and per-screen redesign notes.
2. User-facing screens to evolve: `src/main/resources/theme-resources/templates/*.ftl` + `messages_*.properties`.
3. Flow/step logic: `src/main/java/.../authentication/{requiredactions,authenticators}/`.
4. Front-end tenant data: `GetUserTenants` + `SwitchActiveTenant` + OIDC mappers.
5. When ready to plan, point the PRD/UX workflow at this `index.md`.
