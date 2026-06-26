---
document_type: implementation-addendum
epic: 3
created: 2026-06-25
status: authoritative-for-runtime
supersedes_story_assumptions:
  - 3-1-username-bound-passkey-registration.md (standard webauthn-register only)
  - 3-2-passkey-first-authentication-on-login.md (webauthn-authenticator only)
  - 3-4-optional-post-login-enrollment-prompt.md (action param, webauthn-register routing)
---

# Epic 3 — Passkey Runtime Model (Authoritative Addendum)

This document records **how passkeys actually run in dev/local/production** after integration testing (June 2025). It supersedes conflicting assumptions in the original Story 3.1–3.4 specs, which were written against KC's standard (`webauthn` / `webauthn-register`) providers.

## Runtime model (as deployed)

| Concern | Original BMAD spec | **Runtime implementation** |
|---|---|---|
| Login browser execution | `webauthn-authenticator` | **`webauthn-authenticator-passwordless`** (ALTERNATIVE in `browser custom`, priority 32) |
| Theme property | `passkeyAuthExecId` → standard exec UUID | Same property name; value is the **passwordless** execution UUID (set per realm via `setup-realm-auth.sh` / Admin Console) |
| Registration required action | `webauthn-register` | **`webauthn-register-passwordless`** (queued from `PromptPasskeyEnrollment` on enroll) |
| Stored credential type | `"webauthn"` | **`"webauthn-passwordless"`** |
| Registration FTL | `webauthn-register.ftl` (Story 3.1) | **Same FTL** — KC renders it for both `webauthn-register` and `webauthn-register-passwordless` |
| WebAuthn policy | `webAuthnPolicy*` (standard) | **`webAuthnPolicyPasswordless*`** must be configured for registration/login ceremonies (Rp name, resident key, UV, ES256) |

**Why passwordless:** Discoverable resident passkeys used for username-less / one-tap sign-in align with `webauthn-authenticator-passwordless` and `webauthn-register-passwordless`. The standard `webauthn-authenticator` expects a username-bound second-factor flow and does not match the product's passkey-first login UX.

**Realm source of truth:** `keycloak-multi-tenancy/src/test/resources/realm-export.json` — `browser custom` includes `webauthn-authenticator-passwordless`; required actions include `webauthn-register-passwordless`.

**Local deploy wiring:** `keycloak-26.2.5/setup-realm-auth.sh` sets `PASSKEY_PROVIDER=webauthn-authenticator-passwordless` and writes `passkeyAuthExecId` into `themes/azguards-whatsapp/login/theme.properties`.

## Post-login enrollment flow (Story 3.4 + 3.1)

### Intended UX (one prompt, then OS dialog)

1. **Prompt screen** (`passkey-enrollment-prompt.ftl`) — only place the Agent chooses:
   - **Set up a passkey** → enroll
   - **Not now** → continue (e.g. select tenant)
2. **Registration screen** (`webauthn-register.ftl`) — on first load after enroll:
   - Shows **"Follow the prompt on your device…"** only (no duplicate buttons)
   - **Auto-starts** `navigator.credentials.create()` via JS (`autoStart = !showRetry`)
   - Primary button and **Not now** are hidden until a retry (`isSetRetry`)
   - OS cancel → `cancel-aia` skip form (app-initiated / skippable registration)
3. **Retry state** (`isSetRetry`) — full UI: error banner, **Try again**, **Not now**

### `PromptPasskeyEnrollment` behavior

| Item | Value |
|---|---|
| Provider ID | `prompt-passkey-enrollment` |
| Form field | **`enrollmentChoice`** (`enroll` \| `dismiss`) — **not** `action` (collides with KC required-action URL handling) |
| Per-login session note | `passkey-enrollment-choice` = `enroll` or `dismiss` (prevents re-prompt loop in same login) |
| Has-passkey check | `user.credentialManager().getStoredCredentialsByTypeStream("webauthn-passwordless")` |
| On enroll | `authSession.addRequiredAction("webauthn-register-passwordless")`; set `KC_ACTION` / `KC_ACTION_EXECUTING` client notes; **do not** `user.addRequiredAction` (avoids mandatory re-registration on next login) |
| On dismiss | Clear `KC_ACTION*` client notes; `user.removeRequiredAction(webauthn-register-passwordless)`; `context.success()` |

### `webauthn-register.ftl` registration details

- **`authenticatorLabel`:** set to `Passkey-<ISO timestamp>` on success to avoid KC duplicate-name error ("Device already exists with the same name")
- **`registerBtn`:** must be declared with `var` inside the IIFE (strict mode); auto-start path calls `startRegistration()` on load
- **Skip form:** `cancel-aia=true`; hidden on auto-start but still in DOM for OS-cancel routing

## OQ-8 decision (unchanged)

**Option A — per-session dismiss:** "Not now" skips for the current login only; prompt reappears on the next password login if the user still has no passkey. No `passkeyEnrollDeclined` user attribute.

## Deployment & ops

- **Theme JAR vs filesystem:** On Docker, a partial filesystem theme under `/opt/keycloak/themes/azguards-whatsapp` overrides the JAR and can break the UI. Prefer patching inside the provider JAR or removing the filesystem override. See `keycloak-identity-service/README.md`.
- **`passkeyAuthExecId`:** Must be set per realm after deploy (UUID of the passwordless execution). `deploy-local.sh` / `setup-realm-auth.sh` automate this locally.
- **Stuck users:** Admin → Users → remove **Webauthn Register Passwordless** from required user actions; delete duplicate passkey credentials if registration failed mid-flight.
- **Required action toggle:** **Prompt passkey enrollment** must be enabled in Realm → Authentication → Required actions.

## Story doc cross-reference

| Story | Doc update |
|---|---|
| 3.1 | FTL states A/B/**C** (auto-start); serves passwordless registration |
| 3.2 | `webauthn-authenticator-passwordless` + `passkeyAuthExecId` |
| 3.3 | Register-page auto-start is complementary to feature-detect / AIA skip |
| 3.4 | `enrollmentChoice`, passwordless routing, revised AC #3 |

## Change log

- **2026-06-25:** Documented passwordless runtime model; one-screen enrollment UX (auto-start `webauthn-register.ftl`); `PromptPasskeyEnrollment` session-note and KC_ACTION routing; deployment notes from local/dev debugging.
