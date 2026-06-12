---
status: final
created: 2026-06-11
updated: 2026-06-11
revision: 2 (reconciled to live WhataTalk product — username login, Account vocabulary, teal)
project: keycloak-multi-tenancy
direction: Trust & Clarity
sources:
  - ../../research/market-login-onboarding-ux-for-keycloak-based-b2b-saas-research-2026-06-11.md
  - ../../../../docs/auth-flows.md
---

# EXPERIENCE.md — keycloak-multi-tenancy login & onboarding

> How the Keycloak-served web auth screens behave. Visual identity is **DESIGN.md** (referenced as `{token}`). **This spine wins on conflict with any mock.** Backend is the `anarsultanov/keycloak-multi-tenancy` extension on Keycloak (target 26.4+ for native passkeys; 26.0.7 today). Screens are FreeMarker required actions / authenticators today — this spine defines their target behavior.

## Foundation

- **Form factor:** Responsive web, served by Keycloak. Mobile-first single-column; centers on larger viewports. No native app surface in scope.
- **Out of scope (this run):** the **Create New Business screen / flow** (kept as-is — the Login button still launches the existing flow, untouched), social sign-in, WhatsApp-channel auth, email templates, in-app/admin tenant switcher.
- **In scope:** Login, Select Account picker, Invitation auto-accept.
- **UI system:** none external — bespoke on Keycloak theme. Visual tokens per DESIGN.md; replaces today's hardcoded-CSS FreeMarker templates.
- **Entry model:** **username + password**, with **passkey added as the passwordless upgrade** (passkey-first when one is registered; password always available). The current product logs in with a system-generated **username** (e.g. `egDrvLuRU5h`) — we keep that identifier. Passkey depends on KC 26.4+; degrades gracefully to password. **Magic link is optional**, offered only when a verified email is on file for the account (not the primary path). **"Sign in with SSO"** remains a secondary option (IdP-based, as today) — no email-domain detection (login isn't email-based).
- **Glossary:** user-facing **"Account"** == backend **"tenant"** (`TenantModel`). Creating one = **"Create New Business."** Users are **"Agents"**; roles **ADMIN / AGENT**. Never "workspace."
- **Verification model:** Login is username-based, but **email is still on file** (invitations are emailed). Invited users are verified by clicking the emailed invite link (sets `emailVerified=true`, required by the backend `review-tenant-invitations` gate). Self-serve agents who create a Business proceed without an email wall. See Backend Deltas.

## Information Architecture

Linear, self-pruning flow. Each surface only appears when it has a job:

```
Login (username + password; passkey upgrade)         ← in scope
 ├─ existing agent → authenticate (passkey if registered → password; magic link if email on file)
 ├─ "Sign in with SSO" (secondary, IdP-based) → IdP
 └─ "Create New Business" → [EXISTING FLOW, out of scope this run]
Post-auth required actions (auto-skip when not needed):
 ├─ Review invitations → AUTO-ACCEPT if exactly 1 invite; picker only if >1   ← in scope
 ├─ Create New Business → only if agent belongs to 0 Accounts                  ← out of scope (untouched)
 └─ Select Account     → only if >1 Account (search + last-used); auto if 1    ← in scope
→ land in product
```

**Surface closure (in-scope surfaces):** Login (all journeys), Invitation accept (Sam, auto), **Select Account** picker (Dana). The "Sign in with SSO" option replaces the legacy alias-typing screen with a cleaner secondary entry. The Create New Business screen remains the current implementation and is not redesigned this run.

## Voice and Tone

Clear, calm, human — never jargon, never alarmist. Mirrors WhataTalk's current copy. Microcopy:
- Login title: **"Login to Agent Account"**; fields: **"Username"**, **"Password"**; primary: **"Login"**; secondary: **"Create New Business"**; **"Forgot Password"** link retained.
- Passkey upgrade prompt: **"Sign in faster next time — set up a passkey."**
- Account picker title: **"Select Account"**; each row shows **"Roles: ADMIN / AGENT"** (matches live).
- Create Business: **"Create New Business"** / field "Business name" placeholder "e.g. Kitaab Alyom".
- Invite: **"{Inviter} invited you to join {Account}"** — always name the inviter and account.
- Errors specific, non-blaming: **"That username or password doesn't match"** / **"That link has expired — we'll send a new one."** Never "Invalid input."
- SSO secondary link: **"Sign in with SSO"**.

## Component Patterns (behavioral)

- **Login form** — `{components.text-field}` Username + Password (with show/hide eye, as today). If the account has a registered passkey, offer **"Use your passkey"** as the primary affordance above the password (browser may auto-prompt via `autocomplete="username webauthn"`); password remains available. `Login` primary button; `Create New Business` secondary; `Forgot Password` link.
- **Primary button** — one per screen (`{components.button}` primary). Disabled only while submitting (inline spinner; never block on client-side over-validation).
- **Method row** — alternate methods (`{components.method-row}`) beneath primary: "Use your passkey", "Use password instead", and "Email me a sign-in link" **only if a verified email is on file**. Remember the agent's last successful method.
- **Account card** — `{components.workspace-card}` (Account card), full-row click target; avatar/logo (initials fallback), account name, **Roles: ADMIN/AGENT**.
- **Search field** — `{components.search-field}` appears above the Account list when the agent belongs to **> 4** Accounts; type-to-filter by name.
- **Inline error** — `{components.inline-error}` beneath the field; never modal/toast for validation.

## State Patterns

For each surface: **empty · loading · error · success · edge**.
- **Login:** loading = button spinner on Login; error = inline under the fields ("That username or password doesn't match"); edge = rate-limit / locked account → calm explanatory message + recovery path (Forgot Password).
- **Passkey:** unavailable/unsupported device → silently fall back to password (no dead-end); failed prompt → "Try again or use your password"; announce fallback to screen readers.
- **Magic link (optional, email-on-file only):** after send → "Check your email" state with throttled resend + "use another method"; expired → auto-offer resend. Hidden entirely when no verified email is on file.
- **Create New Business:** _out of scope this run — existing flow unchanged._
- **Invitation (auto-accept):** success = land in the Account with a confirmation `{components.toast}` ("{Inviter} invited you to join {Account}") **that includes a "Not you? Decline" action** → calls the existing external decline path and removes the membership. >1 invite = multi-invite picker (accept/decline per Account). External User-Service failure = **non-blocking** — let the agent in, retry membership sync in background, surface a quiet `{components.banner}` only if a manual retry is needed.
- **SSO (secondary):** "Sign in with SSO" → IdP redirect; redirecting = interstitial; **IdP-redirect failure / access-denied = calm error with "Try another way" → back to login** (never a dead-end); no accessible Account via IdP = explanatory message + support path.
- **Select Account picker:** loading = `{components.skeleton-row}`s; empty (0 Accounts) = route to Create New Business; > 4 = search visible, last-used pinned top; no search match = "No accounts match '{query}'".

## Interaction Primitives

- Enter submits the focused single-field step.
- Last-used auth method and last-used workspace are remembered across sessions.
- Tenant switch (post-login, out of scope here) re-mints tokens — keep that contract.
- Autofill / passkey prompts respected. Autocomplete tokens: username field `username webauthn`, password `current-password`, any typed code `one-time-code`.
- **Focus management:** on each step transition move focus to the new step's `<h1>` (or first field); when the agent returns from email (magic-link tab, if used), restore focus to the primary action; announce passkey silent-fallback to password via `aria-live`.
- All actions reachable by keyboard; full-row cards are real buttons.

## Accessibility Floor

- WCAG 2.1 AA. Visible focus ring (`{colors.*.focus-ring}`, 2px, 2px offset) on every interactive element.
- Errors announced via `aria-live="polite"`; field errors linked with `aria-describedby`.
- Color never the sole error signal (icon + `{colors.*.danger-text}` text + `aria-invalid`).
- Control boundaries use `{colors.*.border-strong}` (≥3:1, WCAG 1.4.11), not the decorative `border` hairline.
- Targets ≥ 44px; field/button height `{spacing.field-height}` satisfies this.
- Labels always present (not placeholder-only). Logical tab order; single `<h1>` per screen.
- Respect `prefers-reduced-motion` (no spinner-only feedback for reduced-motion users — pair with text).

## Key Flows

**1. Priya — returning agent, single Account.**
Priya opens the WhataTalk login. A passkey prompt appears (she set one up last week); one Face ID tap and she's authenticated. She belongs to a single Account, so the Select Account screen is skipped automatically and she lands straight in.
**Climax:** Priya is inside in one tap — no password typed, no extra screen.
_(New-agent signup via "Create New Business" is out of scope this run — that screen is unchanged.)_

**2. Sam — invited agent.**
Sam gets an email: **"Priya invited you to join Kitaab Alyom."** He clicks the link (which verifies his email), lands on login, and signs in. Because he's invited to exactly one Account, the system **auto-accepts the invitation** and drops him straight in — no accept/reject list to wade through; a small toast confirms "Priya invited you to join Kitaab Alyom" with a quiet "Not you? Decline."
**Climax:** Sam goes from invite email to inside the Account with zero decision screens; the invite *was* the activation.

**3. Dana — agent across five Accounts.**
Dana signs in fast with her passkey (Face ID), password ready as backup. She belongs to several Accounts, so the **Select Account** screen appears — her most recently used Account is pinned at the top with a "Last used" tag, and a search field sits above the list. She types two letters and it filters to the right one; each row still shows its **Role**.
**Climax:** Dana jumps to the correct Account in two taps, never scanning a long list.

## Backend Deltas (for engineering scoping)

This UX is **not a template reskin** — several behaviors are net-new against the current `anarsultanov/keycloak-multi-tenancy` extension (see `docs/auth-flows.md`). Flagged so eng can scope:

| Behavior | Current state | Delta |
|---|---|---|
| Keep username + password login | Username (generated) + password form | **Keep** — restyle only, no identifier change |
| Passkey upgrade | None (KC 26.0.7) | **KC 26.4+ upgrade** + matched extension version; passkey tied to the username credential |
| Magic link (optional) | None | **New, optional** authenticator — only when verified email on file; not the primary path |
| "Sign in with SSO" (secondary) | Manual IdP-alias entry screen (`login-with-sso`) | **Restyle/keep** as a cleaner secondary option (no email-domain detection) |
| Invite-link → `emailVerified=true` | Invite trigger gates on `emailVerified` | **New** verification-on-invite-click so invitees reach the flow |
| Auto-accept single invite | Mandatory accept/reject card list | **Change** `review-tenant-invitations` to auto-accept when exactly 1 invite (keep decline + external User-Service call) |
| Non-blocking acceptance | External User-Service call is **synchronous** (failure point) | **Change** to async/background retry to deliver the non-blocking UX |
| Create New Business screen | Existing required action + `create-tenant.ftl` | **Out of scope this run** — leave unchanged (Login button still launches it) |
| Theme tokenization (WhataTalk teal) | Hardcoded inline CSS in FTL (#2c2f33/#00b4d8) | **Refactor** the in-scope templates (login, select-tenant, review-invitations) to semantic teal tokens |
| Vocabulary | "Select Account" / "Create New Business" / ADMIN-AGENT | **Keep** — no relabeling |

Spine behavior is the target; sequencing is for the PRD/architecture phase.

## Inspiration & Anti-patterns

- **Inspiration:** WorkOS AuthKit (account switcher), Stripe (calm auth), Slack (invite-as-activation).
- **Anti-patterns (replacing today's flows):** raw "type your SSO name" alias entry (restyle to a cleaner secondary option); mandatory accept/reject list for a single invite; hardcoded dark-theme CSS (#2c2f33/#00b4d8) in `select-tenant.ftl`/`review-invitations.ftl`; blank screen during business provisioning; `console.log` debugging shipped in production JS; duplicate i18n keys.
