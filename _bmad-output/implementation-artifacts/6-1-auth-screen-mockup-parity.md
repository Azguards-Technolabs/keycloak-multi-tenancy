---
baseline_commit: ea9e5840bf93ccca843694f54bb1b4577a26da6c
---

# Story 6.1: Auth Screen Mockup Parity

**Epic:** 6 — Auth Screen Mockup Parity
**Story ID:** 6.1
**Status:** done
**Working Repositories:** `keycloak-multi-tenancy` (Java SPI — complete first) then `azguards-keycloak-custom-theme` (FTL/CSS)

**Mockup sources:**
- `_bmad-output/planning-artifacts/ux-designs/ux-keycloak-multi-tenancy-2026-06-11/mockups/screen-login.html`
- `_bmad-output/planning-artifacts/ux-designs/ux-keycloak-multi-tenancy-2026-06-11/mockups/screen-select-account.html`
- `_bmad-output/planning-artifacts/ux-designs/ux-keycloak-multi-tenancy-2026-06-11/mockups/screen-invite-accepted.html`

---

## User Story

**As** an Agent signing in or onboarding,
**I want** the login, account picker, and invite-accepted screens to match the approved mockups,
**So that** the auth experience feels consistent with the WhataTalk product design.

---

## Acceptance Criteria

### AC-1 — Login screen matches `screen-login.html`
```
Given  the login screen
When   it renders
Then   mint background (#E6F2EE), CSS wordmark, 400px centered wrap, card padding 30/28/26px
And    title "Login to Agent Account" + muted subtitle
And    dashed passkey affordance above password (when passkey execution is configured)
And    inline credential error with icon between password and Login
And    Login primary CTA, outline Create New Business, Forgot Password inside card
```

### AC-2 — Select Account matches `screen-select-account.html`
```
Given  the account picker
When   it renders
Then   brand wordmark + subtitle "Choose which account to continue into."
And    search field always visible (mockup overrides Epic 4 conditional >4 rule for visual parity)
And    rows: 42px avatar, name + inline "Last used" tag, Roles: prefix, chevron
And    last-used row has 2px primary border + halo shadow
```

### AC-3 — Invite accepted full-screen replaces post-login toast for single invite
```
Given  exactly one pending invitation auto-accepts
When   membership is granted
Then   invite-accepted.ftl is challenged (not context.success() immediately)
And    layout matches screen-invite-accepted.html: toast, success check, You're in, account pill
And    no blank screen at any step (FR-INV-7)
```

### AC-4 — Proceed and decline on invite-accepted screen
```
Given  the invite-accepted challenge
When   the Agent taps Go to account
Then   ReviewTenantInvitations.processAction(action=proceed) calls context.success()

Given  the invite-accepted challenge
When   the Agent taps Not you? Decline
Then   membership is revoked, all sessions logged out, context.failure() (FR-INV-8)
```

---

## Tasks / Subtasks

### Repo 1: `keycloak-multi-tenancy`

- [x] **Task 1:** `ReviewTenantInvitations.autoAcceptInvitation()` — challenge `invite-accepted.ftl` instead of immediate `context.success()`
- [x] **Task 2:** Add `challengeInviteAccepted()`, `handleInviteAcceptedDecline()`, `isInviteAcceptedPending()` helpers
- [x] **Task 3:** `processAction()` — handle `action=proceed` and `action=decline` before multi-invite form processing
- [x] **Task 4:** Remove toast session notes from auto-accept path (full-screen confirmation supersedes account-theme toast for this flow)

### Repo 2: `azguards-keycloak-custom-theme`

- [x] **Task 5:** `login.ftl` + `style.css` / `components.css` — mockup layout (wordmark, passkey dashed row, inline error, outline CTA)
- [x] **Task 6:** `select-tenant.ftl` + `selectTenant.css` — mockup rows, search always, brand, subtitle
- [x] **Task 7:** `invite-accepted.ftl` + `inviteAccepted.css` — toast + success card
- [x] **Task 8:** `messages_en.properties` — login, select-account, invite-accepted keys
- [x] **Task 9:** `tokens.css` — card shadow `0 10px 30px rgba(15,23,42,.06)` per mockup

### Post-implementation QA & bugfixes (2026-06-25)

- [x] **Task 10:** `login.ftl` — remove SSO / magic-link rows (not in `screen-login.html` mockup); scope `.wt-login-form { gap: 0 }` under mockup layout
- [x] **Task 11:** `login-reset-password.ftl` — align chrome with login mockup; fix `backToLogin` message (`messages_en.properties`) so `&laquo;` does not render literally
- [x] **Task 12:** KC 26 FreeMarker compatibility — remove invalid `?html` built-ins (`review-invitations.ftl`, `select-tenant.ftl`, `invite-accepted.ftl`); replace `(tenant.id!"")?hash` with `tenant?index` in `select-tenant.ftl` (fixes HTTP 500 after **Go to Dashboard**)
- [x] **Task 13:** `selectAccountNoMatch` MessageFormat fix — `{query}` → `{0}` in `messages_en.properties`; pass `"{query}"` from `select-tenant.ftl` `data-template` for client-side search replacement (fixes second HTTP 500 on select-account render)
- [x] **Task 14:** `components.css` — suppress visible focus ring on page `<h1>` (`h1:focus`, `h1:focus-visible { outline: none }`) while keeping `wtA11y` programmatic focus for screen readers (Story 1.5 cross-ref)

### Local dev / ops (2026-06-25)

- [x] **Task 15:** `AbstractAdminResource.java` — `allowedOrigins(session, auth.getClient())` for KC **26.2.5** (was `checkAllowedOrigins` from 26.6.3 build; extension must be built with `-Dkeycloak.version=26.2.5`)
- [x] **Task 16:** `keycloak-26.2.5/setup-realm-auth.sh` — MailHog SMTP for local forgot-password; `setup-test-tenants-and-invite.sh` for select-account / multi-invite QA
- [x] **Task 17:** `review-invitations.ftl` — align with select-account mockup shell (wordmark, card, search, scrollable row list, avatar initials); rewrite `reviewTenant.css`; update `script.js` for i18n labels + `wt-banner` errors

---

## Dev Agent Record

### Files changed

**keycloak-multi-tenancy**
- `src/main/java/.../authentication/requiredactions/ReviewTenantInvitations.java`
- `src/main/java/.../resource/AbstractAdminResource.java` (KC 26.2.5 CORS — Task 15)

**azguards-keycloak-custom-theme**
- `login/login.ftl`
- `login/login-reset-password.ftl` (Task 11)
- `login/select-tenant.ftl`
- `login/review-invitations.ftl` (Tasks 12, 17)
- `login/resources/css/reviewTenant.css` (Task 17)
- `login/resources/js/script.js` (Task 17)
- `login/invite-accepted.ftl` (new)
- `login/resources/css/tokens.css`
- `login/resources/css/components.css` (Tasks 5, 14)
- `login/resources/css/style.css`
- `login/resources/css/selectTenant.css`
- `login/resources/css/inviteAccepted.css` (new)
- `login/messages/messages_en.properties` (Tasks 8, 11, 13)

**keycloak-26.2.5** (local harness — not a product repo)
- `setup-realm-auth.sh`, `setup-test-tenants-and-invite.sh`, `deploy-local.sh`
- `themes/azguards-whatsapp/` — rsync target for hot theme QA

### Design notes

- Keeps Epic 1 CSS architecture (`tokens.css` + `components.css` + per-screen CSS) rather than isolated `auth-shared.css` bundles.
- Select-account search is **always shown** for mockup parity; Epic 4.3 conditional search (>4 accounts) is superseded for this visual contract.
- **Login mockup scope:** SSO and magic-link rows were **removed** from `login.ftl` — they are not present in `screen-login.html`. SSO / magic-link templates (`login-with-sso.ftl`, `login-magic-link.ftl`) remain for alternate auth flows.
- Account-theme toast (`ToastInfoResource`) remains for other flows; single-invite auto-accept now uses full-screen `invite-accepted.ftl`.
- **KC 26.2.5 FreeMarker:** Do not use `?html` or `?hash` built-ins in FTL — not available in KC 26's FreeMarker 2.3.32. Use `kcSanitize()`, numeric MessageFormat placeholders (`{0}`), or list index (`tenant?index`).
- **wtA11y focus on `<h1>`:** Programmatic focus on load (Story 1.5) must not show a visible ring on titles — see Task 14 in `components.css`.

### Completion notes

- 2026-06-25: Implemented mockup parity across login, select-account, and invite-accepted screens; wired Java challenge + proceed/decline handlers.
- 2026-06-25 (QA): Fixed accept → **Go to Dashboard** HTTP 500 chain (`?hash` + `selectAccountNoMatch` MessageFormat); aligned reset-password + login spacing with mockup; suppressed heading focus ring; documented local KC 26.2.5 deploy + test scripts.

### Test notes (local)

| Flow | How to trigger |
|------|----------------|
| Login mockup | `azguards-whatsapp-client` auth URL (not admin client) |
| Multi-invite review | `./setup-test-tenants-and-invite.sh` → login as invitee |
| Select Account | Accept 2+ invites → **Go to Dashboard** (requires 2+ memberships) |
| Invite accepted | Single pending invite auto-accept path |

Known non-blocking local noise: `UserServiceRestClient` → `host.docker.internal:4003` unreachable (async retry only).

---

## Change Log

- 2026-06-25: Story created and implemented — mockup parity for three auth screens + invite-accepted required-action challenge.
- 2026-06-25: QA pass — FreeMarker KC 26 fixes (`?hash`, `?html`), `selectAccountNoMatch` MessageFormat, login/reset-password mockup alignment, h1 focus-ring suppression, KC 26.2.5 CORS + local test harness docs (Tasks 10–16).
- 2026-06-25: `review-invitations.ftl` rebuilt to match select-account layout (wordmark, card, search, row list, wt-btn proceed); `reviewTenant.css` rewritten as invite-row extensions on `selectTenant.css`.
