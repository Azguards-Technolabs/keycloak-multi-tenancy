---
stepsCompleted: [1, 2, 3]
inputDocuments:
  - _bmad-output/planning-artifacts/prds/prd-keycloak-multi-tenancy-2026-06-11/prd.md
  - _bmad-output/planning-artifacts/prds/prd-keycloak-multi-tenancy-2026-06-11/addendum.md
  - _bmad-output/planning-artifacts/architecture.md
  - _bmad-output/planning-artifacts/ux-designs/ux-keycloak-multi-tenancy-2026-06-11/DESIGN.md
  - _bmad-output/planning-artifacts/ux-designs/ux-keycloak-multi-tenancy-2026-06-11/EXPERIENCE.md
project_name: 'WhataTalk — Login & Onboarding UX Redesign'
user_name: 'Asif'
date: '2026-06-12'
---

# WhataTalk — Login & Onboarding UX Redesign - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for the **WhataTalk Login & Onboarding UX Redesign**, decomposing the requirements from the PRD, Technical Addendum, Architecture, and UX Design specs (DESIGN.md + EXPERIENCE.md) into implementable stories.

This is an **evolution of the `anarsultanov/keycloak-multi-tenancy` Keycloak SPI extension**, not a new service. Scope is login-screen-forward; the Create New Business / registration flow is explicitly out of scope. Vocabulary: **Account** (= backend `TenantModel`), **Agent** (user), roles **ADMIN / AGENT**.

## Two-Repo Development Setup

This project spans **two repositories**. Every developer working on a story must open the correct repo as their working directory. Do not implement theme/FTL work in the extension repo or vice versa.

| Repository | Local Path | What lives here |
|---|---|---|
| `keycloak-multi-tenancy` | `~/WorkSpace/azguards-whatsapp/keycloak-multi-tenancy` | Java SPI extension — authenticators, required actions, endpoints, SPI providers, Zipkin tracing, JUnit5/Testcontainers tests |
| `azguards-keycloak-custom-theme` | `~/WorkSpace/azguards-whatsapp/azguards-keycloak-custom-theme` | Keycloak custom theme — FreeMarker `.ftl` templates, CSS/JS, i18n `messages_*.properties` bundles |

Each story below includes a **Working Repository** callout. Stories that touch both repos list both — complete the SPI/Java work first so the FTL has the correct Keycloak context data available.

## Requirements Inventory

### Functional Requirements

**Login Flow (FR-L)**

- **FR-L-1:** The login screen presents a username field and a password field as the primary credential inputs.
- **FR-L-2:** The username field uses autocomplete token `username webauthn`; the password field uses `current-password`.
- **FR-L-3:** If the Agent has a registered passkey, the system offers a "Use your passkey" affordance as the primary option, displayed above the password field.
- **FR-L-4:** If the device does not support passkeys, or no passkey is registered, the passkey affordance is silently absent — no error state or dead-end.
- **FR-L-5:** The login screen provides a "Sign in with SSO" secondary link; clicking it presents the dynamic alias entry (existing approach, restyled). The alias input is not exposed on the primary login screen.
- **FR-L-6:** The login screen provides an "Email me a sign-in link" option, displayed only after a username-submit check confirms that username maps to an account with `emailVerified = true`. [ASSUMPTION: lightweight lookup, no perceptible latency]
- **FR-L-7:** The login screen provides a "Forgot Password" link.
- **FR-L-8:** The login screen provides a "Create New Business" secondary link routing to the existing (out-of-scope) registration flow.
- **FR-L-9:** Authentication errors are displayed inline, below the form, with copy "That username or password doesn't match." No modal dialogs for errors.
- **FR-L-10:** On lockout/rate-limiting, the system displays a calm explanatory message with a recovery path. [ASSUMPTION copy: "Too many attempts. Your account is temporarily locked — check your email for reset instructions or try again in 15 minutes."]
- **FR-L-11:** All login screen interactive elements meet a minimum touch/click target size of 44×44 px.
- **FR-L-12:** The login screen title reads "Login to Agent Account."

**Account Selection (FR-AS)**

- **FR-AS-1:** If the Agent belongs to exactly one Account, the selection screen is skipped and the Agent routes directly to the product.
- **FR-AS-2:** If the Agent belongs to two or more Accounts, the Account picker is presented before routing.
- **FR-AS-3:** Each Account row displays: Account logo or initials, Account name, and the Agent's role (ADMIN or AGENT).
- **FR-AS-4:** The most recently used Account is pinned at the top with a "Last used" label.
- **FR-AS-5:** A search field is displayed when the Agent belongs to more than four Accounts. Search matches name only, case-insensitive, prefix-first. No-match state: "No accounts match '{query}'."
- **FR-AS-6:** While Account data is loading, the picker displays skeleton placeholder rows.
- **FR-AS-7:** If the Agent belongs to zero Accounts, the system routes them to the Create New Business flow.
- **FR-AS-8:** The Account picker screen title reads "Select Account."

**Invitation Acceptance (FR-INV)**

- **FR-INV-1:** Clicking an invitation link in email sets `emailVerified = true` on the Agent's Keycloak account.
- **FR-INV-2:** After authentication, if the Agent has exactly one pending invitation, the system auto-accepts it without an accept/reject list.
- **FR-INV-3:** After auto-acceptance, a toast is shown: "{Inviter's display name} invited you to join {Account}" with a "Not you? Decline" secondary action.
- **FR-INV-4:** If the Agent has more than one pending invitation, the system presents an invite picker (row = Account name + Inviter); each invitation can be accepted or declined independently.
- **FR-INV-5:** The User-Service call that completes invite acceptance is non-blocking and asynchronous; it does not gate the Agent's entry into the product.
- **FR-INV-6:** If the User-Service call fails, the Agent is admitted and the system retries in the background. [ASSUMPTION: up to 3 retries, 30-second backoff.] On total failure, a quiet banner: "We had trouble syncing your account — tap to retry." No blocking error screen.
- **FR-INV-7:** The system does not present a blank screen at any point during invite acceptance or business provisioning.
- **FR-INV-8:** Tapping "Not you? Decline" revokes acceptance, removes the Agent from the Account, and logs the Agent out, returning to login.

**Passkeys (FR-PK)**

- **FR-PK-1:** The system supports passkey registration tied to the Agent's username credential.
- **FR-PK-2:** Passkey authentication is offered as a first-class option on the login screen when a passkey is registered for the current username.
- **FR-PK-3:** After a successful password login, the system presents an optional prompt: "Sign in faster next time — set up a passkey." Dismissible.
- **FR-PK-4:** Passkey support requires Keycloak 26.4+ as the runtime platform.
- **FR-PK-5:** If a passkey attempt fails or is cancelled, the system falls back to the password flow without showing an error for the passkey step.

### NonFunctional Requirements

**Accessibility (NFR-A)**

- **NFR-A-1:** All in-scope screens must meet WCAG 2.1 Level AA. (Hard launch gate: zero blocking violations.)
- **NFR-A-2:** Focus rings must be visible: 2 px solid outline, 2 px offset, brand focus-ring color.
- **NFR-A-3:** Inline error messages must use `aria-live="polite"` and `aria-describedby`.
- **NFR-A-4:** Color must not be the sole error signal — combine icon, danger-text color, and `aria-invalid`.
- **NFR-A-5:** All form fields must have visible labels; placeholder text must not substitute for a label.
- **NFR-A-6:** All animations/transitions must respect `prefers-reduced-motion`.

**Performance (NFR-P)**

- **NFR-P-1:** The login screen must be interactive within 2 s on standard broadband. [ASSUMPTION]
- **NFR-P-2:** Account picker data must render (or show skeleton) within 500 ms of screen display. [ASSUMPTION]

**Theming & Visual (NFR-T)**

- **NFR-T-1:** All in-scope FreeMarker templates must use semantic CSS design tokens; hardcoded hex (`#2c2f33`, `#00b4d8`, equivalents) must be replaced.
- **NFR-T-2:** Primary brand color is teal (`#0F766E`); auth card centered, max-width 400 px, soft mint background, minimal shadow.
- **NFR-T-3:** Both light and dark mode supported via CSS token switching.
- **NFR-T-4:** System font stack only; no custom web fonts on auth screens.

**Browser & Device (NFR-B)**

- **NFR-B-1:** Auth screens function on current + previous major versions of Chrome, Firefox, Safari, Edge. [ASSUMPTION]
- **NFR-B-2:** Auth screens usable on mobile viewport widths (≥ 320 px).
- **NFR-B-3:** Passkey support degrades gracefully where the WebAuthn API is unsupported.

**Security & Reliability (NFR-S)**

- **NFR-S-1:** No debugging statements (e.g., `console.log`) in production JS on auth screens.
- **NFR-S-2:** Duplicate i18n keys must be resolved in all in-scope message bundles.
- **NFR-S-3:** The User-Service dependency must not be on the critical path for Agent login.

### Additional Requirements

_From Architecture + Technical Addendum — technical/infrastructure requirements that shape implementation and sequencing._

- **AR-1 (ENABLING PREREQUISITE — must land first):** Upgrade Keycloak runtime 26.0.7 → 26.4.x (latest 26.4.6) for native passkeys SPI. Full replacement (single-node auth model), maintenance window, documented rollback. *(No greenfield starter template applies — brownfield fork.)*
- **AR-2:** Recompile the `anarsultanov/keycloak-multi-tenancy` extension fork against KC 26.4.x APIs; verify `AuthenticatorFactory`, `RequiredActionProvider`, `ProtocolMapper`, and FreeMarker theme-resolution order for breaking changes. Validate in staging with production-equivalent data volume.
- **AR-3:** Confirm extension ↔ Keycloak version compatibility before production by running full login + invite flows in staging.
- **AR-4:** Integrate the WebAuthn SPI (`webauthn-register` / `webauthn-authenticate` required actions, `WebAuthnAuthenticator`) bound to the username credential.
- **AR-5:** Add a new optional `MagicLinkAuthenticator` that gates on `emailVerified`, exposed as a conditional step in the browser flow.
- **AR-6:** Add an invite-link verification endpoint that sets `emailVerified = true` at link-click time (not post-login).
- **AR-7:** Modify `review-tenant-invitations` required action for the auto-accept-single-invite path (render toast confirmation, not the list).
- **AR-8:** Refactor the synchronous User-Service HTTP call in the invite-acceptance SPI to fire-and-forget async (thread-pool executor); store a retry flag on failure; KC proceeds regardless of response.
- **AR-9:** Expose a session note / user attribute that the FTL template can read to conditionally render the quiet retry banner.
- **AR-10:** Build a shared CSS design-token theme file loaded by the Keycloak theme (light + dark), consumed by all in-scope FTL templates.
- **AR-11:** Preserve existing token/session contracts: `active_tenant` attribute, `active-tenant-id` session note, `oidc-active-tenant` / `oidc-all-tenants` mappers; tenant switch re-mints tokens — keep unchanged.
- **AR-12:** Extend existing Zipkin/Brave tracing (TracingHelper) over the new auth/invite paths.
- **AR-13:** Extend the existing test harness (JUnit5 + Testcontainers + Playwright + Mailhog) to cover new flows.
- **AR-OOS (do not modify):** `register.ftl` and registration templates, `login-oauth-grant.ftl`, email templates (`email/`), admin tenant switcher, username generation scheme.

### UX Design Requirements

_From DESIGN.md (visual spine) + EXPERIENCE.md (behavioral spine). The UX spine wins on conflict with any mock. These are first-class, story-generating requirements._

- **UX-DR1 (Design-token system):** Implement semantic, theme-aware CSS custom properties per DESIGN.md (brand/surface/text/border/interactive/layout tokens), light-first with dark overrides via `prefers-color-scheme` / `data-theme="dark"`. Per-tenant theming may later override `primary` + `logo` ONLY, contrast-gated (4.5:1-with-white at config time). Eliminate hardcoded hex (`#2c2f33`, `#00b4d8`).
- **UX-DR2 (Button component):** Full-width, height 46 px, radius 10 px, label 14/500. `primary` and `ghost` variants; 2 px focus-ring outline + 2 px offset; disabled only while submitting (inline spinner).
- **UX-DR3 (Text field component):** Height 46 px, radius 10 px, 1 px border; focus raises border to `primary` + ring; label above (14/500); password show/hide eye; error state = `danger` border + `danger-bg` fill + inline message with `danger` icon.
- **UX-DR4 (Auth card component):** `surface`, radius 16 px, hairline border + single soft shadow, max-width 400 px, brand mark; mint `bg`; responsive (16 px gutters on mobile, centered on larger viewports).
- **UX-DR5 (Account card component):** Full-row click target rendered as a real button; avatar/logo 40 px with initials fallback, Account name (subtitle), "Roles: ADMIN/AGENT" (caption); `border-strong` boundary, `primary` border on hover/focus.
- **UX-DR6 (Search field component):** Text-field variant with leading search glyph; appears above the Account list only when count > 4; type-to-filter by name.
- **UX-DR7 (Inline-error component):** `danger-text` + `danger` icon directly beneath the field; never modal/toast for field validation.
- **UX-DR8 (Method-row component):** Secondary auth options ("Use your passkey", "Use password instead", "Email me a sign-in link", "Sign in with SSO") as ghost/text rows beneath the primary action, `muted` until hover; remember the Agent's last successful method.
- **UX-DR9 (Toast component):** Transient confirmation ("{Inviter} invited you to join {Account}" / "You've joined {Account}") with `success` accent, auto-dismiss + manual close, `aria-live="polite"`, and an embedded "Not you? Decline" action.
- **UX-DR10 (Banner component):** Persistent inline notice within the card (background-sync retry); `surface` with `border-strong` left rule; never blocks the primary action.
- **UX-DR11 (Skeleton-row component):** Loading placeholder for Account cards; `border`-tinted blocks with shimmer; `aria-hidden="true"` on rows + `aria-busy="true"` on region while loading (static under `prefers-reduced-motion`).
- **UX-DR12 (Interstitial component):** Full-card transient state ("Getting your Account ready…") — centered spinner + caption; paired text for reduced-motion users; used for SSO redirect.
- **UX-DR13 (Typography & layout system):** System font stack, five type roles (Title/Subtitle/Body/Label/Caption); 4 px base unit, scale [4,8,12,16,24,32,48]; field height 46 px; vertical rhythm 16 px between groups, 24 px above primary action.
- **UX-DR14 (Focus management):** On each step transition move focus to the new step's `<h1>` (or first field); on submission error move focus to first invalid field / error summary; announce passkey silent-fallback via `aria-live`; restore focus to primary action on return from email.
- **UX-DR15 (Interaction primitives):** Enter submits the focused single-field step; autocomplete tokens (`username webauthn`, `current-password`, `one-time-code`); remember last-used auth method and last-used Account across sessions; all actions keyboard-reachable; full-row cards are real buttons.
- **UX-DR16 (State patterns — full coverage):** Each in-scope surface implements empty/loading/error/success/edge states per EXPERIENCE.md — including passkey unavailable→password fallback, magic-link "Check your email" + throttled resend + expired-link resend, SSO redirect interstitial + "Try another way" on IdP failure, and the Account picker no-match state.

### FR Coverage Map

**Functional Requirements**

- FR-L-1: Epic 2 — Username + password primary inputs
- FR-L-2: Epic 2 — Autocomplete tokens (`username webauthn` / `current-password`)
- FR-L-3: Epic 2 / Epic 3 — Passkey-first affordance above password (rendered in login.ftl by Epic 3's WebAuthn integration)
- FR-L-4: Epic 2 / Epic 3 — Silent absence of passkey affordance when unavailable
- FR-L-5: Epic 2 — "Sign in with SSO" secondary link (restyled dynamic alias)
- FR-L-6: Epic 2 — Conditional "Email me a sign-in link" (gated on emailVerified)
- FR-L-7: Epic 2 — "Forgot Password" link
- FR-L-8: Epic 2 — "Create New Business" link to existing flow
- FR-L-9: Epic 2 — Inline auth errors
- FR-L-10: Epic 2 — Calm lockout/rate-limit message + recovery
- FR-L-11: Epic 2 — 44×44 px touch targets (per Epic 1 component sizing)
- FR-L-12: Epic 2 — "Login to Agent Account" title
- FR-AS-1: Epic 4 — Skip picker when exactly one Account
- FR-AS-2: Epic 4 — Show picker when ≥ 2 Accounts
- FR-AS-3: Epic 4 — Row = logo/initials + name + role
- FR-AS-4: Epic 4 — Last-used Account pinned with label
- FR-AS-5: Epic 4 — Conditional search (> 4) + no-match state
- FR-AS-6: Epic 4 — Skeleton placeholder rows while loading
- FR-AS-7: Epic 4 — Zero Accounts → Create New Business
- FR-AS-8: Epic 4 — "Select Account" title
- FR-INV-1: Epic 5 — Invite-link click sets emailVerified
- FR-INV-2: Epic 5 — Auto-accept single pending invitation
- FR-INV-3: Epic 5 — Auto-accept toast + "Not you? Decline"
- FR-INV-4: Epic 5 — Multi-invite picker (accept/decline per Account)
- FR-INV-5: Epic 5 — Non-blocking async User-Service call
- FR-INV-6: Epic 5 — Background retry + quiet retry banner
- FR-INV-7: Epic 5 — No blank screens during acceptance/provisioning
- FR-INV-8: Epic 5 — Decline revokes membership + logs out
- FR-PK-1: Epic 3 — Username-bound passkey registration
- FR-PK-2: Epic 3 — Passkey auth as first-class login option
- FR-PK-3: Epic 3 — Optional post-login enrollment prompt
- FR-PK-4: Epic 1 — Requires Keycloak 26.4+ (platform)
- FR-PK-5: Epic 3 — Graceful fallback to password on passkey failure

**Non-Functional Requirements**

- NFR-A-1: Epic 1 (baseline gate) — WCAG 2.1 AA; verified per-screen in Epics 2/3/4/5
- NFR-A-2: Epic 1 — Visible focus rings (applied all screens)
- NFR-A-3: Epic 1 — aria-live / aria-describedby error pattern (applied all screens)
- NFR-A-4: Epic 1 — Non-color error signal (applied all screens)
- NFR-A-5: Epic 1 — Visible labels (applied all screens)
- NFR-A-6: Epic 1 — prefers-reduced-motion (applied all screens)
- NFR-P-1: Epic 2 — Login interactive < 2 s
- NFR-P-2: Epic 4 — Picker render/skeleton < 500 ms
- NFR-T-1: Epic 1 — Semantic tokens, no hardcoded hex
- NFR-T-2: Epic 1 — Teal brand, centered card, mint bg
- NFR-T-3: Epic 1 — Light + dark mode token switching
- NFR-T-4: Epic 1 — System font stack only
- NFR-B-1: Epic 1 — Cross-browser support (verified per-screen)
- NFR-B-2: Epic 1 — Mobile ≥ 320 px (applied all screens)
- NFR-B-3: Epic 1 / Epic 3 — WebAuthn graceful degradation
- NFR-S-1: Epic 1 — No console.* in production JS
- NFR-S-2: Epic 1 — Duplicate i18n keys resolved
- NFR-S-3: Epic 5 — User-Service off the critical login path

**Additional Requirements**

- AR-1: Epic 1 — KC 26.0.7 → 26.4.x upgrade (enabling prerequisite)
- AR-2: Epic 1 — Extension recompile against 26.4.x APIs + staging validation
- AR-3: Epic 1 — Extension ↔ KC compatibility confirmed in staging
- AR-4: Epic 3 — WebAuthn SPI integration (register + authenticate)
- AR-5: Epic 2 — MagicLinkAuthenticator (conditional browser-flow step)
- AR-6: Epic 5 — Invite-link verification endpoint (emailVerified on click)
- AR-7: Epic 5 — Auto-accept logic in review-tenant-invitations
- AR-8: Epic 5 — Async fire-and-forget User-Service refactor + retry flag
- AR-9: Epic 5 — Retry-banner session-note/attribute signal for FTL
- AR-10: Epic 1 — Shared CSS design-token theme file (light + dark)
- AR-11: Epic 4 — Preserve token/session contracts (active_tenant, mappers, re-mint)
- AR-12: Epic 1 — Extend Zipkin/Brave tracing over new paths (applied per epic)
- AR-13: Epic 1 — Extend test harness (JUnit5/Testcontainers/Playwright/Mailhog)
- AR-OOS: ALL — Do not modify register.ftl, login-oauth-grant.ftl, email templates, admin switcher, username generation

**UX Design Requirements**

- UX-DR1 (design tokens): Epic 1
- UX-DR2 (button): Epic 1
- UX-DR3 (text field): Epic 1
- UX-DR4 (auth card): Epic 1
- UX-DR5 (account card): Epic 4
- UX-DR6 (search field): Epic 4
- UX-DR7 (inline error): Epic 1 (component) → Epic 2 (applied)
- UX-DR8 (method row): Epic 1 (component) → Epic 2 (applied)
- UX-DR9 (toast): Epic 1 (component) → Epic 5 (applied)
- UX-DR10 (banner): Epic 1 (component) → Epic 5 (applied)
- UX-DR11 (skeleton row): Epic 1 (component) → Epic 4 (applied)
- UX-DR12 (interstitial): Epic 1 (component) → Epic 2 (SSO redirect)
- UX-DR13 (typography & layout): Epic 1
- UX-DR14 (focus management): Epic 1 (utilities) → applied all screens
- UX-DR15 (interaction primitives): Epic 2 (login) → applied all screens
- UX-DR16 (state patterns): Epic 2 (login/SSO/magic), Epic 3 (passkey), Epic 4 (picker), Epic 5 (invitation)

## Epic List

### Epic 1: Platform Upgrade & Design System Foundation
Establish a validated Keycloak 26.4+ runtime with the recompiled `anarsultanov/keycloak-multi-tenancy` extension, plus the shared WhataTalk visual identity — semantic CSS design tokens (light + dark), reusable component styles, typography/layout system, accessibility utilities, and production-hygiene cleanup — that every downstream auth screen consumes. **Enabling prerequisite: must land first.**
**FRs covered:** FR-PK-4, NFR-T-1..4, NFR-A-1..6 (baseline), NFR-B-1..3, NFR-S-1, NFR-S-2, AR-1, AR-2, AR-3, AR-10, AR-12, AR-13, UX-DR1, UX-DR2, UX-DR3, UX-DR4, UX-DR13, UX-DR14, and component styles for UX-DR7/8/9/10/11/12.

### Epic 2: Streamlined Agent Login
Returning Agents log in fast through a fully refactored login screen: username + password, restyled "Sign in with SSO" secondary link, conditional "Email me a sign-in link", inline errors, calm lockout copy, "Create New Business" link, and the "Login to Agent Account" title.
**FRs covered:** FR-L-1..12, AR-5, NFR-P-1, UX-DR7, UX-DR8, UX-DR12, UX-DR15, UX-DR16 (login/SSO/magic-link states).

### Epic 3: Passwordless Passkey Sign-In & Enrollment
Agents sign in without a password and set up a passkey for next time. Delivers the complete WebAuthn capability end-to-end: passkey-first affordance on login, username-bound registration, optional post-login enrollment prompt, and graceful fallback to password.
**FRs covered:** FR-PK-1, FR-PK-2, FR-PK-3, FR-PK-5, AR-4, NFR-B-3, UX-DR16 (passkey states).

### Epic 4: Multi-Account Selection
Multi-Account Agents pick the right Account quickly. Delivers the "Select Account" picker: skip when exactly one Account, picker when more, rows with logo/name/role, last-used pin, conditional search (> 4), skeleton loading, and zero-Accounts routing to Create New Business — preserving the active-tenant token/session contracts.
**FRs covered:** FR-AS-1..8, AR-11, NFR-P-2, UX-DR5, UX-DR6, UX-DR11, UX-DR16 (picker states).

### Epic 5: Invited Agent Onboarding
Invited Agents go from invite email to inside their Account with zero decision screens. Delivers invite-link email verification, auto-accept of a single invitation with toast + "Not you? Decline", a multi-invite picker, and the non-blocking async User-Service refactor with background retry and quiet banner — no blank screens.
**FRs covered:** FR-INV-1..8, AR-6, AR-7, AR-8, AR-9, NFR-S-3, UX-DR9, UX-DR10, UX-DR16 (invitation states).

## Epic 1: Platform Upgrade & Design System Foundation

Establish a validated Keycloak 26.4+ runtime with the recompiled extension, plus the shared WhataTalk visual identity and accessibility foundation every downstream auth screen consumes. **Enabling prerequisite: must land first.**

### Story 1.1: Upgrade Keycloak to 26.4.x & validate the extension

As a platform engineer,
I want the Keycloak runtime upgraded to 26.4.x with the extension recompiled and validated in staging,
So that the native WebAuthn/passkeys SPI is available and all existing flows still work.

> **Working Repository:** `keycloak-multi-tenancy` — update `pom.xml` KC version, recompile extension JAR against KC 26.4.x APIs, validate staging deployment end-to-end, and write the rollback runbook.

**Acceptance Criteria:**

**Given** the current runtime is Keycloak 26.0.7 with extension fork 26.0.16
**When** the runtime is upgraded to Keycloak 26.4.x and the extension is recompiled against the 26.4.x APIs
**Then** the extension JAR builds (`mvn package`) and loads via `kc.sh build` with no errors
**And** `AuthenticatorFactory`, `RequiredActionProvider`, `ProtocolMapper`, and the FreeMarker theme-resolution order are confirmed against any breaking API changes

**Given** a staging environment with production-equivalent data volume
**When** the full login and invite flows are run end-to-end
**Then** all existing flows (login, account selection, invitation, tenant switch) pass with no regression
**And** the native `webauthn-register` / `webauthn-authenticate` providers are confirmed present and loadable

**Given** the production deployment plan
**When** the upgrade is scheduled
**Then** a maintenance window with a documented rollback procedure exists (full replacement, single-node auth model)
**And** extension ↔ KC 26.4.x version compatibility is recorded

### Story 1.2: Shared CSS design-token theme (light + dark)

As a front-end engineer,
I want a single shared CSS file of semantic design tokens loaded by the Keycloak theme,
So that every in-scope screen renders the WhataTalk teal identity consistently in light and dark mode without hardcoded values.

> **Working Repository:** `azguards-keycloak-custom-theme` (`~/WorkSpace/azguards-whatsapp/azguards-keycloak-custom-theme`) — create the shared CSS design-token file loaded by the Keycloak theme and consumed by all in-scope FreeMarker templates.

**Acceptance Criteria:**

**Given** the Keycloak theme
**When** the shared token CSS is loaded
**Then** it defines semantic custom properties for brand/surface/text/border/interactive/layout per DESIGN.md (primary teal `#0F766E`, mint `bg`, card max-width 400px, radii, focus-ring width/offset)
**And** the system font stack is used with no web-font load (NFR-T-4)

**Given** a user with `prefers-color-scheme: dark` (or `data-theme="dark"`)
**When** any in-scope screen renders
**Then** the same token names resolve to the dark palette values (NFR-T-3)

**Given** the token system exists
**When** the codebase is audited
**Then** no in-scope template references hardcoded hex (`#2c2f33`, `#00b4d8`, equivalents) — all map to tokens (NFR-T-1)

**Given** the forward-looking per-tenant theming note
**When** a tenant overrides `primary`/`logo`
**Then** only those tokens are overridable and a 4.5:1-with-white contrast gate is enforced at config time (documented; not yet wired)

### Story 1.3: Core form component styles

As a front-end engineer,
I want reusable button, text-field, and auth-card styles built on the tokens,
So that every auth screen shares one accessible, on-brand component vocabulary.

> **Working Repository:** `azguards-keycloak-custom-theme` — add button, text-field, and auth-card CSS component styles built on top of the design tokens delivered in Story 1.2.

**Acceptance Criteria:**

**Given** the token CSS
**When** the button component is styled
**Then** it is full-width, 46px tall, radius 10px, with `primary` and `ghost` variants, a 2px focus-ring + 2px offset, and a submitting/disabled state with inline spinner (UX-DR2, NFR-A-2)

**Given** the text-field component
**When** rendered
**Then** it is 46px tall, radius 10px, 1px border, label-above, with a focus state (border→primary + ring) and an error state (`danger` border, `danger-bg` fill, inline message slot with icon) (UX-DR3)

**Given** the auth-card component
**When** rendered
**Then** it is `surface`, radius 16px, hairline border + single soft shadow, max-width 400px, centered on `bg`, responsive with 16px gutters at ≥320px (UX-DR4, NFR-B-2)

**Given** any interactive element
**When** measured
**Then** its hit target is ≥ 44×44px (FR-L-11)

### Story 1.4: Feedback & list component styles

As a front-end engineer,
I want the inline-error, method-row, toast, banner, skeleton-row, and interstitial components styled on the tokens,
So that feature epics can compose them without re-inventing styles.

> **Working Repository:** `azguards-keycloak-custom-theme` — add inline-error, method-row, toast, banner, skeleton-row, and interstitial CSS component styles. These are theme-only components; no Java SPI changes required.

**Acceptance Criteria:**

**Given** the inline-error component
**When** rendered
**Then** it shows `danger-text` + a `danger` icon directly beneath the field and is never a modal/toast (UX-DR7)

**Given** the method-row component
**When** rendered beneath a primary action
**Then** it is a ghost/text row, `muted` until hover (UX-DR8)

**Given** the toast component
**When** rendered
**Then** it uses a `success` accent, supports auto-dismiss + manual close, carries `aria-live="polite"`, and exposes a secondary-action slot (UX-DR9)

**Given** the banner component
**When** rendered inside the card
**Then** it is `surface` with a `border-strong` left rule and never blocks the primary action (UX-DR10)

**Given** the skeleton-row component
**When** loading
**Then** rows carry `aria-hidden="true"`, the region carries `aria-busy="true"`, and the shimmer is static under `prefers-reduced-motion` (UX-DR11)

**Given** the interstitial component
**When** shown
**Then** it centers a spinner + caption and pairs text for reduced-motion users (UX-DR12)

### Story 1.5: Accessibility utilities & focus management

As an Agent using assistive technology,
I want consistent focus management and error announcement primitives,
So that every auth screen meets WCAG 2.1 AA.

> **Working Repository:** `azguards-keycloak-custom-theme` — add shared JS focus-management helpers, ARIA annotation patterns, and `prefers-reduced-motion` utilities. These are consumed by all in-scope FTL templates; no Java SPI changes required.

**Acceptance Criteria:**

**Given** a screen transition
**When** the new screen renders
**Then** focus moves to its `<h1>` (or first field), and on a submission error focus moves to the first invalid field / error summary (UX-DR14)

**Given** an inline error
**When** it appears
**Then** it uses `aria-live="polite"` and is linked to its field via `aria-describedby`, with `aria-invalid="true"` set (NFR-A-3, NFR-A-4)

**Given** any form field
**When** rendered
**Then** it has a visible label (never placeholder-only) (NFR-A-5)

**Given** `prefers-reduced-motion: reduce`
**When** any animation/transition would run
**Then** it is reduced to near-zero duration (NFR-A-6)

**Given** the foundation is complete
**When** an axe-core scan runs on a token/component reference page
**Then** zero critical/serious violations are reported (NFR-A-1 baseline)

### Story 1.6: Production hygiene, observability & test harness baseline

As an engineer,
I want debug noise removed, i18n bundles de-duplicated, tracing extended, and the test harness ready,
So that the foundation ships clean and downstream epics can be tested.

> **Working Repositories:** Two repos touched in this story — complete in order:
> 1. `azguards-keycloak-custom-theme` — remove all `console.*` statements from in-scope JS/FTL; de-duplicate `messages_*.properties` i18n bundles (NFR-S-1, NFR-S-2).
> 2. `keycloak-multi-tenancy` — extend Zipkin/Brave `TracingHelper` hooks over new auth/invite code paths (AR-12); validate the JUnit5 + Testcontainers + Playwright + Mailhog harness runs cleanly against KC 26.4.x (AR-13).

**Acceptance Criteria:**

**Given** the in-scope JS/FTL
**When** audited
**Then** no `console.log`/`console.debug`/`console.error` statements remain (NFR-S-1)

**Given** the in-scope `messages_*.properties` bundles
**When** audited
**Then** all duplicate keys are resolved (NFR-S-2)

**Given** the existing Zipkin/Brave `TracingHelper`
**When** the new auth/invite code paths are added later
**Then** tracing hooks are in place to extend over them (AR-12)

**Given** the existing JUnit5 + Testcontainers + Playwright + Mailhog harness
**When** the foundation lands
**Then** the harness runs against KC 26.4.x and is ready to cover new flows (AR-13)

## Epic 2: Streamlined Agent Login

Returning Agents log in fast through a fully refactored login screen. Owns `login.ftl`, the SSO restyle, and the new `MagicLinkAuthenticator`. The visible passkey affordance (FR-L-3/FR-L-4) is delivered by Epic 3; this epic sets the `username webauthn` autocomplete token to enable it.

### Story 2.1: Refactored login screen

As a returning Agent,
I want a clean login screen with my username and password and clear secondary options,
So that I can sign in quickly on my daily visit.

> **Working Repository:** `azguards-keycloak-custom-theme` — edit `login.ftl` and its associated CSS/JS using the Epic 1 tokens and components. No new Java SPI changes required for this story.

**Acceptance Criteria:**

**Given** the login screen loads
**When** it renders
**Then** the title reads "Login to Agent Account" (FR-L-12), a username field and a password field are the primary inputs (FR-L-1), and a "Login" primary button is shown

**Given** the credential fields
**When** rendered
**Then** the username field uses `autocomplete="username webauthn"` and the password field uses `autocomplete="current-password"` (FR-L-2)
**And** the password field has a show/hide toggle

**Given** the login screen
**When** rendered
**Then** it shows a "Forgot Password" link (FR-L-7) and a "Create New Business" secondary link that routes to the existing (unchanged) registration flow (FR-L-8)

**Given** the focused single-field/step
**When** the Agent presses Enter
**Then** the step submits (UX-DR15)
**And** the last-used auth method is remembered across sessions

**Given** a standard broadband connection
**When** the login screen is requested
**Then** it is interactive within 2 seconds (NFR-P-1)

### Story 2.2: Inline authentication errors & calm lockout messaging

As an Agent who mistypes credentials,
I want clear, non-blaming error feedback with a recovery path,
So that I can correct course without confusion.

> **Working Repository:** `azguards-keycloak-custom-theme` — update `login.ftl` error-state markup and lockout copy. Keycloak already surfaces error/lockout flags to the template context; no new Java SPI changes required.

**Acceptance Criteria:**

**Given** invalid credentials are submitted
**When** authentication fails
**Then** an inline error "That username or password doesn't match" appears below the form (no modal) (FR-L-9), rendered via the inline-error component (UX-DR7)

**Given** the account is locked or rate-limited
**When** the Agent attempts login
**Then** a calm explanatory message with a recovery path is shown (e.g., "Too many attempts. Your account is temporarily locked — check your email for reset instructions or try again in 15 minutes.") — not a generic error (FR-L-10)

**Given** an error is displayed
**When** it appears
**Then** focus moves to the first invalid field / error summary and the message is announced via `aria-live` (per Epic 1 a11y utilities)

### Story 2.3: "Sign in with SSO" secondary link

As an Agent whose org uses an IdP,
I want a single "Sign in with SSO" link rather than a raw alias box on the main screen,
So that the primary login stays uncluttered.

> **Working Repository:** `azguards-keycloak-custom-theme` — restyle the SSO alias-entry flow in `login.ftl` and any related FTL templates. The existing dynamic alias approach is reused; no new Java SPI code required.

**Acceptance Criteria:**

**Given** the login screen
**When** it renders
**Then** the alias input is NOT exposed on the primary screen; instead a "Sign in with SSO" secondary link is shown (FR-L-5)

**Given** the Agent clicks "Sign in with SSO"
**When** the action fires
**Then** the existing dynamic alias-entry approach is presented, restyled to the new tokens/components

**Given** the IdP redirect is in progress
**When** the Agent waits
**Then** an interstitial ("Redirecting…") is shown (UX-DR12)
**And** on IdP-redirect failure / access-denied, a calm error with "Try another way" returns to login (never a dead-end)

### Story 2.4: Conditional "Email me a sign-in link" (magic link)

As an Agent with a verified email on file,
I want the option to receive a sign-in link by email,
So that I can log in without my password when convenient.

> **Working Repositories:** Two repos touched in this story — complete in order:
> 1. `keycloak-multi-tenancy` — implement the `MagicLinkAuthenticator` Java SPI and wire it as a conditional step in the browser flow (AR-5). It must expose `emailVerified` check and link-send logic to the FTL context.
> 2. `azguards-keycloak-custom-theme` — add the "Email me a sign-in link" method-row to `login.ftl`, plus "Check your email", throttled-resend, and expired-link FTL states.

**Acceptance Criteria:**

**Given** a `MagicLinkAuthenticator` added as a conditional step in the browser flow (AR-5)
**When** the username is submitted
**Then** the system checks whether that username maps to an account with `emailVerified = true` without adding perceptible latency (FR-L-6)

**Given** the check passes
**When** the login methods render
**Then** "Email me a sign-in link" is shown as a method row
**And** when the check fails, the option is hidden entirely

**Given** the Agent requests a magic link
**When** it is sent
**Then** a "Check your email" state is shown with throttled resend and a "use another method" path
**And** an expired link auto-offers a resend ("That link has expired — we'll send a new one.")

## Epic 3: Passwordless Passkey Sign-In & Enrollment

The complete WebAuthn capability, end-to-end. Owns the WebAuthn SPI integration, `webauthn-authenticate.ftl`, and `webauthn-register.ftl`. Depends on Epic 1 (26.4+ platform) and Epic 2 (login screen hosts the affordance).

### Story 3.1: Username-bound passkey registration

As an Agent,
I want to register a passkey tied to my username credential,
So that I have a passwordless credential for future logins.

> **Working Repositories:** Two repos touched in this story — complete in order:
> 1. `keycloak-multi-tenancy` — configure/extend the `webauthn-register` required action and bind it to the username credential (AR-4).
> 2. `azguards-keycloak-custom-theme` — update `webauthn-register.ftl` to use Epic 1 tokens and components.

**Acceptance Criteria:**

**Given** the WebAuthn registration provider (AR-4) and the `webauthn-register.ftl` template
**When** an Agent registers a passkey
**Then** the passkey credential is created and bound to the Agent's username credential (FR-PK-1)

**Given** a registered passkey
**When** the Agent next logs in
**Then** the passkey is available as the first-class option (feeds Story 3.2)

**Given** the registration template
**When** rendered
**Then** it uses Epic 1 tokens/components and meets the a11y floor

### Story 3.2: Passkey-first authentication on login

As a returning Agent with a registered passkey,
I want to sign in with my passkey as the primary option,
So that I authenticate in one tap without typing a password.

> **Working Repositories:** Two repos touched in this story — complete in order:
> 1. `keycloak-multi-tenancy` — wire `webauthn-authenticate` into the browser flow against the KC 26.4+ SPI (AR-4). Ensure `passkey-registered` flag is exposed to the FTL context.
> 2. `azguards-keycloak-custom-theme` — update `webauthn-authenticate.ftl` with Epic 1 tokens/components and the passkey-first affordance above the password field.

**Acceptance Criteria:**

**Given** the WebAuthn SPI is integrated against KC 26.4+ (AR-4) and a passkey is registered for the current username (Story 3.1)
**When** the login screen renders
**Then** "Use your passkey" is offered as the primary affordance, displayed above the password field (FR-PK-2, FR-L-3)

**Given** the passkey affordance
**When** the Agent activates it
**Then** the browser WebAuthn prompt appears (also triggerable via the `autocomplete="username webauthn"` token)
**And** on success the Agent is authenticated and proceeds to the next step

**Given** the `webauthn-authenticate.ftl` template
**When** rendered
**Then** it uses the Epic 1 tokens/components and meets the a11y floor (focus management, labels)

### Story 3.3: Graceful passkey fallback & degradation

As an Agent on a device without passkey support,
I want the password flow to remain seamless,
So that I never hit a dead-end.

> **Working Repository:** `azguards-keycloak-custom-theme` — update FTL templates to silently omit the passkey affordance when unavailable and announce the fallback via `aria-live`. No new Java SPI changes required; the KC WebAuthn SPI already signals unavailability to the template context.

**Acceptance Criteria:**

**Given** the device does not support WebAuthn, or no passkey is registered
**When** the login screen renders
**Then** the passkey affordance is silently absent — no error state or dead-end (FR-L-4, NFR-B-3)

**Given** a passkey attempt fails or is cancelled
**When** the Agent returns to login
**Then** the system falls back to the password flow without showing an error for the passkey step (FR-PK-5)
**And** the fallback is announced via `aria-live` ("Try again or use your password")

### Story 3.4: Optional post-login enrollment prompt

As an Agent who just logged in with a password,
I want an optional nudge to set up a passkey,
So that I can opt into faster sign-in next time.

> **Working Repositories:** Two repos touched in this story — complete in order:
> 1. `keycloak-multi-tenancy` — add a post-login conditional step (required action or custom authenticator) that presents the enrollment prompt and routes the Agent into the passkey registration flow on accept.
> 2. `azguards-keycloak-custom-theme` — implement the enrollment-prompt FTL with dismiss affordance using Epic 1 components.

**Acceptance Criteria:**

**Given** a successful password-based login
**When** the post-login step runs
**Then** an optional prompt "Sign in faster next time — set up a passkey." is presented (FR-PK-3)

**Given** the prompt
**When** the Agent dismisses it
**Then** the Agent proceeds into the product with no passkey created and is not blocked

**Given** the Agent accepts
**When** they proceed
**Then** they enter the registration flow (Story 3.1)

> **Open question (OQ-8):** show the enrollment prompt on every login until dismissed, or only once with a persisted "don't show again"? Current AC assumes dismissible-per-session — confirm before dev.

## Epic 4: Multi-Account Selection

Multi-Account Agents pick the right Account quickly. Owns `select-tenant.ftl`; preserves the active-tenant token/session contracts.

### Story 4.1: Conditional Account-selection routing

As an authenticated Agent,
I want the Account step to appear only when I actually need to choose,
So that single-Account logins aren't slowed by a needless screen.

> **Working Repository:** `keycloak-multi-tenancy` — modify `SelectActiveTenantAuthenticator` (or equivalent SPI) to skip the picker for single-Account Agents, route zero-Account Agents to Create New Business, and preserve the `active_tenant` / `active-tenant-id` / mapper token contracts (AR-11). No theme changes required for routing logic.

**Acceptance Criteria:**

**Given** the Agent belongs to exactly one Account
**When** authentication completes
**Then** the Account selection screen is skipped and the Agent routes directly to the product (FR-AS-1)

**Given** the Agent belongs to two or more Accounts
**When** authentication completes
**Then** the Account picker is presented before routing (FR-AS-2)

**Given** the Agent belongs to zero Accounts
**When** authentication completes
**Then** the Agent is routed to the Create New Business flow (FR-AS-7)

**Given** an Account is selected/resolved
**When** the Agent proceeds
**Then** the existing token/session contracts are preserved — `active_tenant` attribute, `active-tenant-id` session note, `oidc-active-tenant`/`oidc-all-tenants` mappers; tenant switch re-mints tokens (AR-11)

### Story 4.2: Account picker rows with role & last-used pin

As a multi-Account Agent,
I want clear rows showing each Account and my role, with my most recent one pinned,
So that I can recognize and pick the right context fast.

> **Working Repositories:** Two repos touched in this story — complete in order:
> 1. `keycloak-multi-tenancy` — extend the SPI to expose each Account's role (`ADMIN`/`AGENT`), logo URL or initials, and last-used Account identifier to the `select-tenant.ftl` FreeMarker context.
> 2. `azguards-keycloak-custom-theme` — update `select-tenant.ftl` with account-card component rows, "Last used" pin, and "Select Account" title using Epic 1 tokens and components.

**Acceptance Criteria:**

**Given** the Account picker
**When** it renders
**Then** the title reads "Select Account" (FR-AS-8) and each row shows the Account logo or initials, the Account name, and the Agent's role (ADMIN or AGENT) (FR-AS-3), using the account-card component as a full-row button (UX-DR5)

**Given** a most-recently-used Account exists
**When** the picker renders
**Then** that Account is pinned at the top with a "Last used" label (FR-AS-4)
**And** last-used is remembered across sessions

**Given** any row
**When** focused/hovered
**Then** the `border-strong` boundary shifts to `primary`, and the full row is keyboard-activatable

### Story 4.3: Conditional search & no-match state

As an Agent with many Accounts,
I want to filter by name,
So that I'm not scanning a long list.

> **Working Repository:** `azguards-keycloak-custom-theme` — add search field markup and client-side filter JS to `select-tenant.ftl`. The full Account list is already in the FTL context from Story 4.2; no additional SPI changes required.

**Acceptance Criteria:**

**Given** the Agent belongs to more than four Accounts
**When** the picker renders
**Then** a search field appears above the list (UX-DR6); when four or fewer, no search field is shown (FR-AS-5)

**Given** the search field
**When** the Agent types
**Then** matching is by Account name only, case-insensitive, prefix-first (FR-AS-5)

**Given** a query with no matches
**When** filtering completes
**Then** "No accounts match '{query}'." is shown (FR-AS-5)

### Story 4.4: Skeleton loading state

As an Agent opening the picker,
I want an immediate visual placeholder,
So that the screen never feels blank or broken while data loads.

> **Working Repository:** `azguards-keycloak-custom-theme` — add skeleton-row placeholder markup and JS show/hide logic to `select-tenant.ftl`. Data-load timing is determined by the SPI context already delivered by Story 4.2; no new Java changes required.

**Acceptance Criteria:**

**Given** Account data is still loading
**When** the picker displays
**Then** skeleton placeholder rows are shown (FR-AS-6) via the skeleton-row component, with `aria-busy="true"` on the region (UX-DR11)

**Given** the screen is displayed
**When** data is fetched
**Then** the picker renders (or shows the skeleton state) within 500 ms (NFR-P-2)

**Given** data arrives
**When** rows render
**Then** the skeleton `aria-hidden`/`aria-busy` attributes are removed and real rows replace placeholders

## Epic 5: Invited Agent Onboarding

Invited Agents go from invite email to inside their Account with zero decision screens. Owns the invite-link verification endpoint and `review-tenant-invitations.ftl`.

### Story 5.1: Invite-link email verification

As an invited Agent,
I want clicking my email invite to verify my address automatically,
So that I can proceed straight into the login/onboarding flow.

> **Working Repository:** `keycloak-multi-tenancy` — implement the invite-link verification endpoint that sets `emailVerified = true` at link-click time (AR-6). Add a calm expired/invalid-link error response. No theme changes required; this is a pure SPI/endpoint story.

**Acceptance Criteria:**

**Given** an emailed invitation link
**When** the Agent clicks it
**Then** an invite-link verification endpoint sets `emailVerified = true` on the Agent's Keycloak account at link-click time — not post-login (FR-INV-1, AR-6)

**Given** verification succeeds
**When** the Agent continues
**Then** they are redirected to the login screen (and the backend `review-tenant-invitations` `emailVerified` gate is satisfied)

**Given** an expired or invalid invite link
**When** clicked
**Then** a calm message is shown with a recovery path (no blank screen / dead-end)

### Story 5.2: Auto-accept single invitation with toast & decline

As an invited Agent with one pending invitation,
I want it accepted automatically with a clear confirmation,
So that I land in my Account without managing a list.

> **Working Repositories:** Two repos touched in this story — complete in order:
> 1. `keycloak-multi-tenancy` — modify `review-tenant-invitations` required action: implement the auto-accept single-invite path, expose inviter display name and Account name (and a decline action flag) via session note / FTL context (AR-7).
> 2. `azguards-keycloak-custom-theme` — update `review-tenant-invitations.ftl` (or equivalent) with the toast component ("Not you? Decline") using Epic 1 components.

**Acceptance Criteria:**

**Given** the Agent has exactly one pending invitation after authentication
**When** the `review-tenant-invitations` required action runs
**Then** it auto-accepts the invitation without rendering the accept/reject list (FR-INV-2, AR-7)

**Given** auto-acceptance succeeds
**When** the Agent lands in the Account
**Then** a toast is shown — "{Inviter's display name} invited you to join {Account}" (fallback "Someone…" when display name is unavailable) — with a "Not you? Decline" secondary action (FR-INV-3, UX-DR9)

**Given** the Agent taps "Not you? Decline"
**When** the action fires
**Then** the acceptance is revoked, the Agent is removed from the Account, and the Agent is logged out and returned to the login screen (FR-INV-8)

**Given** the auto-accept flow
**When** any step runs
**Then** no blank screen is presented at any point (FR-INV-7)

### Story 5.3: Multi-invite picker

As an invited Agent with more than one pending invitation,
I want to choose which to accept or decline,
So that I control which Accounts I join.

> **Working Repositories:** Two repos touched in this story — complete in order:
> 1. `keycloak-multi-tenancy` — extend `review-tenant-invitations` required action to handle multiple invitations with independent accept/decline per row; expose each invitation's Account name and Inviter to the FTL context.
> 2. `azguards-keycloak-custom-theme` — implement the multi-invite picker FTL using Epic 1 components (account-card rows with accept/decline actions).

**Acceptance Criteria:**

**Given** the Agent has more than one pending invitation after authentication
**When** the required action runs
**Then** an invite picker is presented with one row per invitation (FR-INV-4)

**Given** each picker row
**When** rendered
**Then** it shows the Account name and the Inviter, with independent accept and decline actions (FR-INV-4)

**Given** the Agent acts on one invitation
**When** they accept or decline it
**Then** the others remain actionable and the Agent proceeds once they're done

### Story 5.4: Non-blocking async User-Service sync & retry banner

As an invited Agent,
I want to enter the product even if the backend sync is slow or failing,
So that a downstream outage never blocks me at the door.

> **Working Repository:** `keycloak-multi-tenancy` — refactor the synchronous User-Service HTTP call to fire-and-forget async (thread-pool executor), add retry flag storage, and expose the retry status via session note / user attribute for the FTL banner to read (AR-8, AR-9). No theme changes required — the banner component was delivered in Epic 1 and `review-tenant-invitations.ftl` reads the session note.

**Acceptance Criteria:**

**Given** invite acceptance completes
**When** the User-Service call is made
**Then** it is fire-and-forget asynchronous (e.g., thread-pool executor) and does NOT gate the Agent's entry — the required action completes and KC proceeds regardless of response (FR-INV-5, NFR-S-3, AR-8)

**Given** the User-Service call fails
**When** the Agent is admitted
**Then** the system retries in the background (assumption: up to 3 retries, 30-second backoff) and stores a retry flag on failure (FR-INV-6, AR-8)

**Given** all retries fail
**When** the Agent is in the product
**Then** a quiet banner is shown — "We had trouble syncing your account — tap to retry." — read from a session note / user attribute by the FTL (FR-INV-6, AR-9, UX-DR10); no blocking error screen (FR-INV-7)

**Given** the banner's retry action
**When** the Agent taps it
**Then** the sync is re-attempted in the background without blocking

> **Open question (OQ-3):** the User-Service SLA informing retry-backoff timing is unresolved. The 3×/30s values are assumptions — confirm before implementing this story.

---

## Epic 6: Auth Screen Mockup Parity

Align the three shipped auth screens with the June 2026 HTML mockups (`screen-login.html`, `screen-select-account.html`, `screen-invite-accepted.html`) while keeping the Epic 1 token/component CSS architecture (`tokens.css`, `components.css`, screen-specific CSS).

### Story 6.1: Login, Select Account & Invite Accepted mockup parity

As an Agent signing in or onboarding,
I want the login, account picker, and invite-accepted screens to match the approved mockups,
So that the auth experience feels consistent with the WhataTalk product design.

> **Working Repositories:** Two repos — complete in order:
> 1. `keycloak-multi-tenancy` — present `invite-accepted.ftl` challenge after single-invite auto-accept; handle `proceed` / `decline` in `ReviewTenantInvitations.processAction`.
> 2. `azguards-keycloak-custom-theme` — align `login.ftl`, `select-tenant.ftl`, and new `invite-accepted.ftl` with mockup layout, typography, spacing, and components.

**Acceptance Criteria:**

**Given** the login screen
**When** it renders
**Then** it matches `screen-login.html`: mint background, CSS wordmark, 400px wrap, card padding 30/28/26px, title + subtitle, dashed passkey row above password, inline credential error, Login + outline Create New Business + Forgot Password inside the card (UX mockup 2026-06-11)

**Given** the select-account screen
**When** it renders
**Then** it matches `screen-select-account.html`: brand wordmark, subtitle, always-visible search, 42px avatar rows with chevron, inline "Last used" tag, 2px pinned border on last-used row, `Roles:` prefix

**Given** a single pending invitation auto-accepts
**When** `ReviewTenantInvitations` completes membership grant
**Then** `invite-accepted.ftl` is shown matching `screen-invite-accepted.html` (toast + success card, account pill, Go to account, Not you? Decline) — no blank screen (FR-INV-7)

**Given** the invite-accepted screen
**When** the Agent taps Go to account or Not you? Decline
**Then** proceed continues login; decline revokes membership, logs out, and returns to login (FR-INV-8)

