# Authentication & Onboarding Flows

> **Scope:** Login, registration, tenant creation, invitation review, and active-tenant selection flows of the `keycloak-multi-tenancy` extension (single-realm SPI on Keycloak 26.0.7). Generated 2026-06-11 to ground a login/onboarding UX redesign.

## Building Blocks (what the extension registers)

### Required Actions (`META-INF/services/...RequiredActionFactory`)
Order matters — README mandates this exact order in *Authentication → Required actions*:

| Order | ID | Class | Trigger condition |
|---|---|---|---|
| 1 | `review-tenant-invitations` | `ReviewTenantInvitations` | User has pending invitations **and** `user.email` present **and** `emailVerified`; skipped if client note `tenantInvitationsReviewed=true` already set this session |
| 2 | `create-tenant` | `CreateTenant` | User has **zero** tenant memberships |
| 3 | `select-active-tenant` | `SelectActiveTenant` | No `active-tenant-id` session note yet **and** user has **>1** membership (1 membership → auto-selected, no prompt) |
| 4 | `prompt-passkey-enrollment` | `PromptPasskeyEnrollment` | No `webauthn-passwordless` credential; per-login only (auth-session queue). Priority **1004** — runs after tenant steps. See `epic-3-passkey-runtime-model.md`. |

### Authenticators (`META-INF/services/...AuthenticatorFactory`)
| ID | Class | Role | `requiresUser` |
|---|---|---|---|
| `login-with-sso` | `LoginWithSsoAuthenticator` | Renders `login-with-sso.ftl`, asks user to type an "SSO name" (IdP alias), redirects to that IdP | false |
| (idp-memberships) | `IdpTenantMembershipsCreatingAuthenticator` | Post-broker-login step: auto-creates tenant memberships for tenants configured on a tenant-specific IdP | true |

### OIDC Token Mappers (`META-INF/services/...ProtocolMapper`)
| Provider ID | Display | Emits |
|---|---|---|
| `oidc-active-tenant-mapper` | "Active tenant" | The active tenant (from `active-tenant-id` session note) as a configurable claim |
| `oidc-all-tenants-mapper` | "All tenants" | List of all the user's tenant memberships as a claim |
| `oidc-*` (`HardcodedTenantMapper`, `TenantAttributeMapper`) | Hardcoded tenant / Tenant attribute | Static tenant / per-tenant attribute claims |

### FTL Templates (`theme-resources/templates/`)
`create-tenant.ftl`, `select-tenant.ftl`, `review-invitations.ftl`, `login-with-sso.ftl`, plus invitation emails (html + text). Messages in `messages_en.properties` / `messages_sv.properties`.

---

## Flow 1 — Self-serve registration → first tenant (new user, no invite)

```
Standard KC registration/login
   └─> [review-tenant-invitations] no pending invites → success (no-op)
   └─> [create-tenant] user has 0 memberships → SHOW create-tenant.ftl
          form collects ONLY "tenantName"        (mobileNumber/countryCode/status sent as "")
          validates: blank → tenantEmptyError; duplicate name → tenantExistsError
          createTenant(...) → user becomes member
   └─> [select-active-tenant] now 1 membership → auto-set active-tenant-id note → success
   └─> tokens minted with active_tenant / all_tenants claims
```
**UX notes:** Minimal-field tenant creation (good). But it's a separate full-page required action *after* an already-multi-step KC registration — adds steps. The form is plain FreeMarker; copy ("You need to create a tenant.") is functional, not guided. No "what are you setting up?" branching, no example content (vs Notion-style).

## Flow 2 — Invited user accepts/declines (the core onboarding path)

```
Invitation email (invitation-email.ftl) → "log in or register" link to account page
User authenticates (login or registration)
   └─> [review-tenant-invitations] pending invites + verified email → SHOW review-invitations.ftl
          card list of inviting tenants; per-card Accept / Reject buttons
          client-side JS tracks state in sessionStorage; hidden fields acceptedTenants/rejectedTenants
          "Proceed" button (always enabled client-side)
          processAction:
            - if NO memberships AND nothing accepted AND unprocessed invites remain → re-challenge with error
              ("You must accept at least one tenant invitation to proceed…")
            - calls EXTERNAL User Service REST (userServiceRestClient.updateUserTenantInvitationStatuses)
            - per accepted tenant: set tenant ACTIVE, grantMembership(user, roles), revoke invitation
            - per rejected tenant: revoke invitation
            - if now has memberships → remove create-tenant, add select-active-tenant
          sets client note tenantInvitationsReviewed=true
   └─> [select-active-tenant] 1 membership → auto; >1 → SHOW select-tenant.ftl
   └─> [prompt-passkey-enrollment] no webauthn-passwordless credential → SHOW passkey-enrollment-prompt.ftl
          "Set up a passkey" → webauthn-register-passwordless (OS dialog via webauthn-register.ftl auto-start)
          "Not now" → dismiss for this login; next required action or finish (must not re-prompt in same login — 26.6.9+)
   └─> tokens minted
```
**UX notes:**
- Invitee is treated context-aware-ish (skips create-tenant if they accept), which is good. But the invite email copy is generic ("You have been invited to join {0}") — no inviter name, no role context, no CTA framing (research flags inviter context + clear CTA as acceptance-rate levers).
- **External dependency:** invitation processing calls an outside "User Service" (`UserServiceRestClient`) synchronously — a failure point in the onboarding path.
- `review-invitations.ftl` carries **hardcoded dark-theme CSS** and verbose `console.log` debugging in shipped JS.
- Duplicate message keys in `messages_en.properties` (`reviewInvitationsHeader`/`Info` defined twice; also unused `doSkip`, `doAccept`) — last definition wins; cleanup needed.

## Flow 3 — Active-tenant selection at login (the tenant-picker)

```
[select-active-tenant].evaluateTriggers
   - active-tenant-id note already set (incl. via SSO user-session note) → skip
   - exactly 1 membership → auto-select, no UI
   - >1 membership → add required action
requiredActionChallenge → SHOW select-tenant.ftl (card list: logo, name, roles, "Select")
processAction → validate membership → set active-tenant-id session note
IdP filtering: if login came via a tenant-specific IdP, memberships are filtered to the IdP's
  accessibleTenantIds; if none accessible → AuthenticationFlowError.ACCESS_DENIED
```
**UX notes:** `select-tenant.ftl` **is** your tenant-picker — the prime redesign surface (research recommended modeling it on WorkOS AuthKit's org switcher). Currently a static dark-themed (#2c2f33/#00b4d8) card list with hardcoded inline CSS; not theme-driven, so not per-tenant brandable as-is. No search/filter (matters for users in many tenants).

## Flow 4 — SSO login via "SSO name"

```
[login-with-sso authenticator] → SHOW login-with-sso.ftl
   user types "SSO name" (= IdP alias) → looks up enabled, non-link-only IdP
   found → performLogin (redirect to IdP); not found → ssoError
post-broker: IdpTenantMembershipsCreatingAuthenticator auto-creates memberships for
   tenant-specific IdPs (role Constants.TENANT_USER_ROLE)
```
**UX notes:** Requiring the user to **type an IdP alias** is poor discovery UX. Best practice is **email-first → infer the IdP from the email domain** (and only fall back to manual entry). Strong, cheap win.

## Flow 5 — Post-login tenant switching (REST, not a login step)

```
PUT  {realm-resource-base}/switch     (SwitchActiveTenant)
   verifies bearer token; validates user has the target tenant;
   sets user attribute active_tenant + user-session note active-tenant-id;
   regenerates tokens (TokenManager) and returns them (CORS-enabled, all origins)
GET  {realm-resource-base}/user-tenants  (GetUserTenants) → list of the user's tenants (TenantDto)
```
**UX notes:** Switching **re-mints tokens** (matches research best practice — don't reuse the old token). `user-tenants` is the endpoint a front-end tenant-switcher would call. CORS is wide open (`allowAllOrigins`) — fine for dev, tighten for prod.

---

## Cross-cutting observations for the redesign

| Area | Current state | Redesign implication |
|---|---|---|
| Auth methods | Password + passkey-first (passwordless WebAuthn on KC 26.4+); SSO via typed alias; magic link (realm-dependent) | Email-domain IdP discovery; keep `passkeyAuthExecId` patched in theme deploy |
| Theming | Flows are FreeMarker with **hardcoded inline dark CSS**; not theme-variable driven | Move styling to theme; enables per-client/per-tenant branding |
| Step count | Up to 4 sequential full-page required actions after KC login/registration (invites → create tenant → select tenant → passkey prompt) | Consolidate/▼ steps; auto-skip aggressively (already done for 1-membership cases) |
| Tenant picker | `select-tenant.ftl` static card list, no search | Redesign as polished, searchable, branded picker |
| Invites | Generic email copy; sync external User Service call; debug JS shipped | Add inviter/role context + CTA; make external call resilient; strip debug |
| Session re-mint | Tenant switch regenerates tokens ✓ | Keep |
| Tech debt | Duplicate i18n keys; `console.log` in prod JS; wide-open CORS | Clean up alongside redesign |

## External & data touchpoints
- **External "User Service"** (`UserServiceRestClient`) called during invitation acceptance — onboarding depends on it.
- **Email** sending via `EmailSender` for invitation / accepted / declined notifications.
- **Token claims** the front-end can rely on: active tenant (`oidc-active-tenant-mapper`), all tenants (`oidc-all-tenants-mapper`); user attribute `active_tenant`; session note `active-tenant-id`.
