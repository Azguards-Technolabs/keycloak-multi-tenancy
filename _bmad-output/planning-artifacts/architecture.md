---
stepsCompleted: [1, 2, 3]
inputDocuments:
  - _bmad-output/planning-artifacts/prds/prd-keycloak-multi-tenancy-2026-06-11/prd.md
  - _bmad-output/planning-artifacts/prds/prd-keycloak-multi-tenancy-2026-06-11/addendum.md
  - _bmad-output/planning-artifacts/ux-designs/ux-keycloak-multi-tenancy-2026-06-11/DESIGN.md
  - _bmad-output/planning-artifacts/ux-designs/ux-keycloak-multi-tenancy-2026-06-11/EXPERIENCE.md
  - _bmad-output/planning-artifacts/research/market-login-onboarding-ux-for-keycloak-based-b2b-saas-research-2026-06-11.md
  - docs/architecture.md
  - docs/auth-flows.md
  - docs/source-tree-analysis.md
  - docs/project-overview.md
  - docs/api-contracts.md
workflowType: 'architecture'
project_name: 'keycloak-multi-tenancy'
user_name: 'Asif'
date: '2026-06-11'
---

# Architecture Decision Document

_This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together._

## Project Context Analysis

### Requirements Overview

**Functional Requirements:** 33 FRs across 4 capability areas.
- **Login (FR-L, 12):** username+password primary; passkey-first affordance when registered;
  secondary SSO link (dynamic alias, restyled); conditional magic-link option (email-on-file);
  inline errors; calm lockout copy; Create New Business link to the existing (out-of-scope) flow.
- **Account Selection (FR-AS, 8):** skip when 1 Account, picker when >1; row = logo/name/role;
  last-used pinned; search when >4 Accounts; skeleton loading; 0 Accounts → Create New Business.
- **Invitation Acceptance (FR-INV, 8):** emailVerified on invite-link click; auto-accept single
  invite with toast + decline; multi-invite picker; **non-blocking async User-Service call** with
  background retry + quiet banner; no blank screens.
- **Passkeys (FR-PK, 5):** username-credential-bound passkey registration/auth; passkey-first on
  login; optional post-login enrollment prompt; graceful fallback; **requires Keycloak 26.4+**.

Architecturally, every FR maps to an existing Keycloak extension point — required actions,
authenticators, OIDC mappers, JPA provider, or FreeMarker theme templates. This is an evolution
of the `anarsultanov/keycloak-multi-tenancy` SPI extension, not a new service.

**Non-Functional Requirements:** 18 NFRs.
- **Accessibility (6):** WCAG 2.1 AA, visible focus rings, aria-live errors, non-color error
  signals, visible labels, prefers-reduced-motion. Hard launch gate (zero blocking violations).
- **Performance (2):** login interactive <2s; account picker render/skeleton <500ms.
- **Theming/Visual (4):** semantic CSS tokens (no hardcoded hex), teal #0F766E brand, light+dark,
  system font only.
- **Browser/Device (3):** current+previous Chrome/Firefox/Safari/Edge; ≥320px mobile; WebAuthn
  graceful degradation.
- **Security/Reliability (3):** no console.* in prod; no duplicate i18n keys; User-Service off the
  critical login path.

### Scale & Complexity

- Primary domain: **Keycloak server-side extension (Java SPI) + FreeMarker/CSS presentation layer.**
- Complexity level: **Medium** — scope is bounded (login-screen-forward; Create New Business
  out of scope), but elevated by (a) a coupled Keycloak platform upgrade, (b) an async reliability
  refactor, and (c) native WebAuthn integration.
- Estimated architectural touch-points: ~7 FTL templates, 3 required actions, 2 authenticators
  (+1 new magic-link), the WebAuthn SPI, the User-Service client, a shared CSS token theme, i18n
  bundles, and a new invite-link verification endpoint.

### Technical Constraints & Dependencies

- **Platform upgrade is a prerequisite:** Keycloak 26.0.7 → 26.4+ for the native passkeys SPI;
  full replacement (single-node auth model), maintenance window, documented rollback; extension
  recompiled against 26.4+ APIs and validated in staging (AuthenticatorFactory,
  RequiredActionProvider, FTL theme-resolution order).
- **Extension ↔ Keycloak version coupling** must be matched and verified before production.
- **External User-Service dependency** must be moved off the critical path (async + retry).
- **Existing token/session contracts preserved:** active_tenant attribute, active-tenant-id session
  note, oidc-active-tenant/all-tenants mappers; tenant switch re-mints tokens — keep.
- **Out of scope (do not modify):** Create New Business / registration, register.ftl,
  login-oauth-grant.ftl, email templates, admin tenant switcher, username generation scheme.
- **FreeMarker is the only presentation technology** (no external UI framework on the auth path);
  system font stack only — no web fonts.

### Cross-Cutting Concerns Identified

- **Design-token theming system** — shared CSS tokens (light+dark) consumed by all in-scope FTL;
  forward-looking per-tenant override of primary/logo only (contrast-gated).
- **Accessibility (WCAG 2.1 AA)** — spans every template; focus management, aria-live, reduced-motion.
- **Internationalization** — messages_en/sv bundles; dedupe; all new microcopy keys.
- **Resilience/async** — fire-and-forget User-Service call, background retry, FTL-readable retry signal.
- **Observability** — existing Zipkin/Brave tracing (TracingHelper) to extend over new paths.
- **Production hygiene** — no console.*, no hardcoded hex, no duplicate keys (launch checklist).

## Starter Template Evaluation

### Primary Technology Domain

**Brownfield Keycloak server-side extension (Java SPI), not a greenfield application.**
No starter template applies — the foundation is the existing codebase plus the upstream
`anarsultanov/keycloak-multi-tenancy` project it is forked from. Technology choices are
fixed by the platform: Java + Keycloak SPI, Maven, FreeMarker theme-resources, Keycloak
JPA entity provider, Zipkin/Brave tracing, JUnit5 + Testcontainers + Playwright for tests.
No `project-context.md` existed, so there are no separately documented technical preferences;
the stack is determined entirely by the brownfield reality.

### Foundation Options Considered

1. **Evolve the existing fork (SELECTED).** Keep the current single-realm custom-tenant model;
   modify the in-scope required actions, authenticators, and FTL templates. Lowest risk, preserves
   all token/session contracts, matches PRD/research guidance ("keep the extension, do NOT migrate
   to Organizations").
2. **Migrate to Keycloak Organizations** (GA since KC 26). Rejected — full re-architecture of a
   working tenant model for no in-scope benefit; out of scope by PRD decision and research.
3. **Custom front-end on standard OIDC** (replace FreeMarker for rich UX). Rejected for this scope —
   FreeMarker is sufficient for the in-scope screens and avoids a new deployable; revisit only if
   future UX outgrows theme templates.

### Selected Foundation: existing fork, upgraded to Keycloak 26.4.x

**Rationale for Selection:**
The PRD, UX spine, and research all converge on evolving the existing extension. The only foundational
change is the platform upgrade required to unlock native passkeys.

**Platform versions (verified 2026-06):**
- Current: Keycloak **26.0.7**, extension fork version **26.0.16**.
- Target: Keycloak **26.4.x** (latest 26.4.6) — first line with officially supported native passkeys.
- Upstream extension latest is **26.1.1** (targets KC 26.x generally); **no 26.4.x-validated build exists**,
  so the fork must be recompiled against 26.4.x APIs and validated in staging.

**Architectural Decisions Provided by the Foundation:**
- **Language & Runtime:** Java, Keycloak 26.4.x server runtime, JVM as shipped by Keycloak.
- **Packaging/Build:** Maven `mvn package` → JAR into Keycloak `providers/` → `kc.sh build`. Version
  tracks the Keycloak major/minor line.
- **Persistence:** Keycloak JPA entity provider (Tenant/Membership/Invitation/Attribute) — unchanged.
- **Presentation:** FreeMarker theme-resources + messages bundles; new shared CSS design-token file.
- **Extension points in play:** RequiredActionProvider, AuthenticatorFactory, ProtocolMapper, JPA
  entity provider, RealmResourceProvider, WebAuthn SPI (new, 26.4+).
- **Observability:** existing Zipkin/Brave tracing via TracingHelper.
- **Testing:** JUnit5 + Testcontainers (Keycloak) + Playwright + Mailhog — existing harness extended.

**Note:** The first implementation story is the **Keycloak 26.4.x upgrade + extension recompile +
staging validation** — it is the enabling prerequisite for the passkey work and must land first.
