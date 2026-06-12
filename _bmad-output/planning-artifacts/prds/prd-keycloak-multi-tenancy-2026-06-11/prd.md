---
title: "WhataTalk — Login & Onboarding UX Redesign"
status: final
created: 2026-06-11
updated: 2026-06-11
product: WhataTalk
scope: Login, Account Selection, Invitation Acceptance, Passkeys
out-of-scope: Registration / Create New Business, social login, WhatsApp auth, email templates, admin tenant switcher
---

# WhataTalk — Login & Onboarding UX Redesign

## 1. Problem & Background

WhataTalk is a B2B SaaS multi-tenant platform. Each subscribing business is an **Account**. The people who use the product are called **Agents**. Agents authenticate through a custom Keycloak extension (`anarsultanov/keycloak-multi-tenancy`) running on Keycloak 26.0.7.

The current login and onboarding flow has compounding problems that directly harm activation and retention:

**Visual debt.** FreeMarker templates contain hardcoded dark-theme hex values (`#2c2f33`, `#00b4d8`) that clash with WhataTalk's teal brand. The UI looks unpolished and inconsistent across screens.

**Anti-UX patterns.** A single pending invite forces Agents through an explicit accept/reject list. The SSO entry point exposes a raw "type your SSO alias" input rather than a single link. These patterns add unnecessary friction on common paths.

**Reliability risk.** The User-Service call during invite acceptance is synchronous. A service timeout blocks the Agent from entering the product — an unacceptable failure mode in production.

**Passkeys gap.** Keycloak 26.0.7 does not provide the native WebAuthn/passkeys SPI needed for credential-tied passkey support. 2025 adoption data shows passkeys deliver a 93% login success rate versus 63% for traditional password auth. Agents currently have no passwordless option.

**Activation leakage.** B2B SaaS average activation rate is 37.5%. The invite-as-activation moment is the highest-leverage touchpoint for invited Agents. The current flow does not optimise for it: Agents land without context, face a list to manage, and experience no confirmation moment.

**Scope constraint.** The Create New Business (registration) flow is working and is explicitly out of scope. This PRD covers only flows that begin from the login screen forward.

---

## 2. Goals & Non-Goals

### Goals

| # | Goal |
|---|------|
| G-1 | Eliminate all visual bugs in the in-scope FreeMarker templates; establish a single coherent WhataTalk visual identity on the auth screens. |
| G-2 | Reduce login friction for returning Agents, including passkey support as a primary affordance when registered. |
| G-3 | Auto-accept single pending invitations so invited Agents land in the product without a manual approval step. |
| G-4 | Make the User-Service call during invite acceptance non-blocking so a backend failure never prevents an Agent from entering the product. |
| G-5 | Deliver WCAG 2.1 AA accessible auth screens. |
| G-6 | Support Keycloak upgrade to 26.4+ as the enabling platform change. |

### Non-Goals

- Registration / Create New Business flow — unchanged.
- Social login (Google, GitHub, etc.).
- WhatsApp-based authentication.
- Email template redesign.
- Admin-facing tenant switcher.
- Any changes to how usernames are assigned (system-generated IDs are kept).

---

## 3. Users & Segments

### 3.1 Returning Agent (Single Account)
An Agent who has previously onboarded and belongs to exactly one Account. Repeat login is their dominant use case. Friction is the primary risk. They represent the highest login volume.

### 3.2 Multi-Account Agent
An Agent who belongs to two or more Accounts (e.g., a consultant or admin managing multiple clients). They need to select which Account context to enter after authentication. Search and "last used" recall reduce their friction.

### 3.3 Invited Agent (New to Platform)
An Agent who has never logged in. They arrive via an emailed invite link. Their activation moment is critical: the first three days determine 90% of churn risk. The invite-click-to-product path must be as short as possible.

---

## 4. User Journeys

### 4.1 Priya — Returning Agent, Single Account

Priya is an Agent at a mid-size company. She logs in every weekday morning.

1. Priya opens the WhataTalk login URL.
2. She sees the login screen: username field, password field, "Login" button. Her username is pre-filled by her browser.
3. She has a passkey registered. The screen offers "Use your passkey" as the primary affordance above the password field.
4. She taps the passkey prompt. The browser biometric dialog appears. She authenticates.
5. She belongs to exactly one Account. Account selection is skipped.
6. She lands in the product.

**Passkey unavailable variant:** Priya is on a new device. The passkey affordance is absent. She types her password. Login completes normally.

**Error variant:** Priya misremembers her password. She sees "That username or password doesn't match" inline below the form. She uses "Forgot Password" to recover.

---

### 4.2 Sam — Multi-Account Agent

Sam consults for three companies, all WhataTalk customers.

1. Sam authenticates (password or passkey).
2. The Account picker appears showing three Accounts: Account logo/initials, Account name, and role (ADMIN or AGENT).
3. His most recently used Account is pinned at the top with a "Last used" tag.
4. Sam selects the Account he needs today.
5. He lands in the product under that Account's context.

---

### 4.3 Dana — Invited Agent

Dana has never used WhataTalk. Her manager sends her an invite.

1. Dana receives an email invite and clicks the link.
2. The click verifies her email address (`emailVerified = true` in Keycloak).
3. Dana is prompted to log in with her system-assigned username and set a password (existing required-action flow).
4. After authentication, she has exactly one pending invitation. It is auto-accepted. No list to manage.
5. A toast confirmation appears: "{Inviter} invited you to join {Account}" with a "Not you? Decline" action.
6. Dana lands in the product inside that Account.

**Multiple invites variant:** Dana was invited to two Accounts. After authentication, she sees an invite picker — one row per Account — and can accept or decline each independently.

**User-Service failure variant:** The User-Service call fails during invite acceptance. Dana is admitted to the product. A quiet banner appears only if a manual retry is needed. The retry runs in the background; Dana sees no blocking error.

---

## 5. Features & Functional Requirements

Requirements are written as capabilities — what the system allows or enforces, not how it is implemented. Technical decisions are deferred to the addendum.

### 5.1 Login Flow (FR-L)

| ID | Requirement |
|----|-------------|
| FR-L-1 | The login screen presents a username field and a password field as the primary credential inputs. |
| FR-L-2 | The username field uses autocomplete token `username webauthn`. The password field uses autocomplete token `current-password`. |
| FR-L-3 | If the authenticated Agent has a registered passkey, the system offers a "Use your passkey" affordance as the primary option, displayed above the password field. |
| FR-L-4 | If the Agent's device does not support passkeys, or no passkey is registered, the passkey affordance is silently absent. No error state or dead-end is shown. |
| FR-L-5 | The login screen provides a "Sign in with SSO" secondary link for IdP-based login. Clicking it presents the dynamic alias entry (existing approach, restyled) — the alias input is not exposed on the primary login screen. |
| FR-L-6 | The login screen provides an "Email me a sign-in link" option. After the username field is submitted, the system checks whether that username maps to a Keycloak account with `emailVerified = true`. The option is displayed only if that check passes. [ASSUMPTION: a lightweight lookup call is made after username entry; this must not add perceptible latency] |
| FR-L-7 | The login screen provides a "Forgot Password" link. |
| FR-L-8 | The login screen provides a "Create New Business" secondary link that routes to the existing registration flow (unchanged). |
| FR-L-9 | Authentication errors are displayed inline, below the form, using the copy "That username or password doesn't match." No modal dialogs are used for errors. |
| FR-L-10 | On account lockout or rate-limiting, the system displays a calm explanatory message with a recovery path — not a generic error. [ASSUMPTION] Lockout copy: "Too many attempts. Your account is temporarily locked — check your email for reset instructions or try again in 15 minutes." |
| FR-L-11 | All login screen interactive elements meet a minimum touch/click target size of 44×44 px. |
| FR-L-12 | The login screen title reads "Login to Agent Account." |

### 5.2 Account Selection (FR-AS)

| ID | Requirement |
|----|-------------|
| FR-AS-1 | If the authenticated Agent belongs to exactly one Account, the Account selection screen is skipped and the Agent is routed directly to the product. |
| FR-AS-2 | If the authenticated Agent belongs to two or more Accounts, the Account picker is presented before routing to the product. |
| FR-AS-3 | Each Account row in the picker displays: Account logo or initials, Account name, and the Agent's role in that Account (ADMIN or AGENT). |
| FR-AS-4 | The most recently used Account is pinned at the top of the picker with a "Last used" label. |
| FR-AS-5 | A search field is displayed when the Agent belongs to more than four Accounts. Search matches Account name only, case-insensitive, prefix-first. A no-match state displays "No accounts match '{query}'." |
| FR-AS-6 | While Account data is loading, the picker displays skeleton placeholder rows. |
| FR-AS-7 | If the Agent belongs to zero Accounts, the system routes them to the Create New Business flow. |
| FR-AS-8 | The Account picker screen title reads "Select Account." |

### 5.3 Invitation Acceptance (FR-INV)

| ID | Requirement |
|----|-------------|
| FR-INV-1 | Clicking an invitation link in email sets `emailVerified = true` on the Agent's Keycloak account. |
| FR-INV-2 | After authentication, if the Agent has exactly one pending invitation, the system auto-accepts it without requiring the Agent to interact with an accept/reject list. |
| FR-INV-3 | After auto-acceptance, a toast notification is displayed with the copy "{Inviter's display name} invited you to join {Account}" and a "Not you? Decline" secondary action. |
| FR-INV-4 | If the Agent has more than one pending invitation after authentication, the system presents an invite picker. Each row shows the Account name and Inviter. The Agent can accept or decline each invitation independently. |
| FR-INV-5 | The User-Service call that completes invite acceptance is non-blocking and asynchronous. It does not gate the Agent's entry into the product. |
| FR-INV-6 | If the User-Service call fails, the Agent is admitted to the product and the system retries in the background. [ASSUMPTION] Up to 3 retries with 30-second backoff. If all retries fail, a quiet banner is shown: "We had trouble syncing your account — tap to retry." No blocking error screen is presented. |
| FR-INV-7 | The system does not present a blank screen at any point during invite acceptance or business provisioning. |
| FR-INV-8 | When the Agent taps "Not you? Decline" on the auto-accept toast, the system revokes the invitation acceptance, removes the Agent from the Account, and logs the Agent out, returning them to the login screen. |

### 5.4 Passkeys (FR-PK)

| ID | Requirement |
|----|-------------|
| FR-PK-1 | The system supports passkey registration tied to the Agent's username credential. |
| FR-PK-2 | Passkey authentication is offered as a first-class option on the login screen when a passkey is registered for the current username. |
| FR-PK-3 | After a successful password-based login, the system presents an optional prompt: "Sign in faster next time — set up a passkey." The Agent may dismiss it. |
| FR-PK-4 | Passkey support requires Keycloak 26.4+ as the runtime platform. |
| FR-PK-5 | If a passkey authentication attempt fails or is cancelled by the Agent, the system falls back to the password flow without showing an error for the passkey step. |

---

## 6. Non-Functional Requirements

### 6.1 Accessibility

| ID | Requirement |
|----|-------------|
| NFR-A-1 | All in-scope screens must meet WCAG 2.1 Level AA. |
| NFR-A-2 | Focus rings must be visible: 2 px solid outline, 2 px offset, using the brand focus-ring color. |
| NFR-A-3 | Inline error messages must use `aria-live="polite"` and `aria-describedby` to associate the error with its field. |
| NFR-A-4 | Color must not be the sole signal for an error state. Each error must combine an icon, danger-text color, and `aria-invalid`. |
| NFR-A-5 | All form fields must have visible labels. Placeholder text must not substitute for a label. |
| NFR-A-6 | All animations and transitions must respect the `prefers-reduced-motion` media query. |

### 6.2 Performance

| ID | Requirement |
|----|-------------|
| NFR-P-1 | The login screen must be interactive within 2 seconds on a standard broadband connection. [ASSUMPTION] |
| NFR-P-2 | Account picker data must render (or show skeleton state) within 500 ms of screen display. [ASSUMPTION] |

### 6.3 Theming & Visual

| ID | Requirement |
|----|-------------|
| NFR-T-1 | All in-scope FreeMarker templates must use semantic CSS design tokens. Hardcoded hex color values (`#2c2f33`, `#00b4d8`, and equivalents) must be replaced. |
| NFR-T-2 | The primary brand color is teal (`#0F766E`). The auth card is centered, max-width 400 px, on a soft mint background with minimal shadow. |
| NFR-T-3 | The system must support both light mode and dark mode via CSS token switching. |
| NFR-T-4 | The system font stack is used. No custom web fonts are loaded on auth screens. |

### 6.4 Browser & Device Support

| ID | Requirement |
|----|-------------|
| NFR-B-1 | Auth screens must function correctly on the current and previous major versions of Chrome, Firefox, Safari, and Edge. [ASSUMPTION] |
| NFR-B-2 | Auth screens must be usable on mobile viewport widths (≥ 320 px). |
| NFR-B-3 | Passkey support degrades gracefully on browsers and devices that do not support the WebAuthn API. |

### 6.5 Security & Reliability

| ID | Requirement |
|----|-------------|
| NFR-S-1 | No debugging statements (e.g., `console.log`) may be present in production JavaScript on auth screens. |
| NFR-S-2 | Duplicate i18n keys must be resolved in all in-scope message bundles. |
| NFR-S-3 | The User-Service dependency must not be on the critical path for Agent login. A failure in that service must not prevent an Agent from accessing the product. |

---

## 7. Success Metrics

The following metrics define success for this initiative. Items marked `[ASSUMPTION]` are inferred from industry data and should be confirmed with stakeholders before the launch baseline is set.

| Metric | Baseline | Target | Source |
|--------|----------|--------|--------|
| Login success rate (password) | [ASSUMPTION: ~63% per industry avg] | ≥ 85% | FR-L-9, FR-L-10 |
| Login success rate (passkey, where available) | — (new capability) | ≥ 90% | FR-PK-1–5 |
| Invite-to-product conversion (invited Agents completing onboarding) | [ASSUMPTION: currently unmeasured] | ≥ 70% within 24 h of invite | FR-INV-1–4 |
| Time from invite click to product landing | [ASSUMPTION: current > 3 steps] | ≤ 2 steps after login | FR-INV-2, FR-INV-3 |
| Login-related support tickets (password resets, lockout confusion) | [ASSUMPTION: currently unmeasured] | Reduce by 40% within 60 days of launch | FR-L-9, FR-L-10 |
| WCAG 2.1 AA audit violations on in-scope screens | [ASSUMPTION: multiple current] | Zero blocking violations at launch | NFR-A-1–NFR-A-6 |
| User-Service-induced login failures | [ASSUMPTION: measurable today] | Zero (non-blocking path) | NFR-S-3, FR-INV-5, FR-INV-6 |

---

## 8. Open Questions

| # | Question | Owner | Due |
|---|----------|-------|-----|
| OQ-1 | What is the current measured login success rate? Without a baseline, the 85% target is an assumption. | Analytics / Engineering | Before sprint start |
| OQ-2 | What is the current invite-to-activation conversion rate? The auto-accept improvement cannot be measured without a baseline. | Product / Analytics | Before sprint start |
| OQ-3 | Is there a defined SLA for the User-Service that informs how long the background retry should wait before surfacing a manual-retry banner? | Backend Engineering | Before FR-INV-6 implementation |
| OQ-4 | Should the "Email me a sign-in link" option (magic link) be gated behind a feature flag for gradual rollout, or shipped fully enabled? | Product | Before implementation |
| OQ-5 | Does the Keycloak 26.0.7 → 26.4+ upgrade require a staged rollout plan (e.g., blue-green deployment, rollback window)? This affects the launch timeline. | DevOps / Engineering | Before sprint start |
| OQ-6 *(resolved)* | Fixed IdP list vs. dynamic alias for SSO? **Resolved:** dynamic alias approach preserved, restyled (see FR-L-5). | Product / Engineering | — |
| OQ-7 *(resolved)* | Who is the "Inviter" in the toast copy? **Resolved:** the inviting Agent's display name. [ASSUMPTION] Fallback copy: "Someone invited you to join {Account}" when display name is unavailable (see FR-INV-3). | Product | — |
| OQ-8 | Should passkey registration be offered on every login until dismissed, or only once? Is there a "don't show again" preference to persist? | Product / UX | Before FR-PK-3 implementation |

---

## 9. Glossary

| Term | Definition |
|------|-----------|
| **Account** | A business that subscribes to WhataTalk. Maps to a tenant (`TenantModel`) in the backend. Never referred to as "workspace" or "tenant" in the UI. |
| **Agent** | A user of WhataTalk. All human end-users are Agents regardless of role. |
| **Role** | An Agent's permission level within an Account: either **ADMIN** or **AGENT**. Displayed as "Roles: ADMIN" or "Roles: AGENT" in the Account picker. |
| **Inviter** | The Agent who sent the invitation. Displayed by their display name in the invite toast (see FR-INV-3). Fallback: "Someone" when display name is unavailable. |
| **Pending invitation** | An invitation sent to an Agent that has not yet been accepted or declined. |
| **User-Service** | The external service called during invite acceptance to complete Account membership. Not Keycloak — a separate downstream dependency. |
| **Passkey** | A WebAuthn credential tied to the Agent's username. Requires Keycloak 26.4+ runtime. |
| **Magic link** | A time-limited sign-in link sent to a verified email address. Shown only when `emailVerified = true` for the entered username. |
| **Create New Business** | The existing registration flow for creating a new Account. Explicitly out of scope — unchanged. |
