---
baseline_commit_extension: 7f51fae39e426e282ef127975a848f14e9d9bcf3
baseline_commit_theme: d435581
baseline_commit: 7f51fae39e426e282ef127975a848f14e9d9bcf3
---

# Story 3.1: Username-bound passkey registration

Status: done

## Story

As an Agent,
I want to register a passkey tied to my username credential,
So that I have a passwordless credential for future logins.

## Acceptance Criteria

1. **Given** the WebAuthn registration provider (AR-4) and the `webauthn-register.ftl` template **When** an Agent registers a passkey **Then** the passkey credential is created and bound to the Agent's username credential (FR-PK-1)

2. **Given** a registered passkey **When** the Agent next logs in **Then** the passkey is available as the first-class option (feeds Story 3.2)

3. **Given** the registration template **When** rendered **Then** it uses Epic 1 tokens/components and meets the a11y floor

## Tasks / Subtasks

---

### REPO 1: `keycloak-multi-tenancy`

> Complete all Repo 1 tasks before starting Repo 2. The realm policy must be configured before the theme FTL registration ceremony can be tested end-to-end.

- [x] **Task 1: Configure WebAuthn passkey policy in realm-export.json** (AC: #1, #2)
  - [x] Open `src/test/resources/realm-export.json`
  - [x] Update the standard (non-passwordless) WebAuthn policy fields to passkey-appropriate values:
    - `"webAuthnPolicyRequireResidentKey": "required"` — passkeys MUST be resident/discoverable credentials
    - `"webAuthnPolicyUserVerificationRequirement": "required"` — passkeys require biometric/PIN user verification
    - `"webAuthnPolicyAuthenticatorAttachment": "not specified"` — allow both platform (Touch ID, Face ID, Windows Hello) and cross-platform (hardware key) authenticators
    - `"webAuthnPolicyRpEntityName": "WhataTalk"` — relying party display name shown in browser dialogs
    - `"webAuthnPolicyRpId": ""` — leave empty (KC defaults to the current hostname; correct for dev/staging/prod; do NOT hardcode a domain)
    - `"webAuthnPolicySignatureAlgorithms": ["ES256"]` — keep or add ES256 (ECDSA P-256) as it is universally supported; keep RS256 if already present
    - `"webAuthnPolicyAvoidSameAuthenticatorRegister": false` — allow re-registration from the same authenticator
    - `"webAuthnPolicyCreateTimeout": 60` — 60-second registration window (0 = browser default; 60s is explicit and reasonable)
  - [x] Verify `webauthn-register` required action entry already exists and is enabled (it is at line 2445 in realm-export.json): `"alias": "webauthn-register", "enabled": true, "defaultAction": false` — **do NOT change defaultAction to true** (auto-prompting every user is Story 3.4's enrollment prompt, not this story)
  - [x] Do NOT touch `webAuthnPolicyPasswordless*` fields — those are for the separate passwordless flow, not passkeys via `webauthn-register`

- [x] **Task 2: Add passkey i18n keys to extension messages bundle** (AC: #3)
  - [x] Open `src/main/resources/theme-resources/messages/messages_en.properties`
  - [x] Append the following keys (verify none already exist to avoid duplicate key build failure):
    ```properties
    # Passkey registration (Story 3.1)
    passkeyRegisterTitle=Set up a passkey
    passkeyRegisterIntro=Set up a passkey to sign in faster next time — no password needed.
    passkeyRegisterBtn=Set up passkey
    passkeyRegisterSkip=Not now
    passkeyRegisterInProgress=Follow the prompt on your device…
    passkeyRegisterSuccess=Passkey set up successfully
    passkeyRegisterError=Passkey set up failed — try again or skip for now.
    passkeyRegisterRetry=Try again
    ```
  - [x] Check for duplicates: `grep -n "passkeyRegister" src/main/resources/theme-resources/messages/messages_en.properties` — must return 0 matches before adding

- [x] **Task 3: Build and verify Repo 1** (AC: #1)
  - [x] `mvn package -DskipTests` → BUILD SUCCESS
  - [x] `grep -c "passkeyRegister" src/main/resources/theme-resources/messages/messages_en.properties` → 8 (matches count of added keys)
  - [x] No webauthn-related Java files created in this story — the built-in `webauthn-register` required action (`org.keycloak.authentication.requiredactions.WebAuthnRegisterFactory`) is KC's own provider and requires no extension code

---

### REPO 2: `azguards-keycloak-custom-theme`

> Start only after Repo 1 is verified. The WebAuthn registration ceremony requires the configured rpEntityName/rpId from the realm policy. Test with a running KC instance that has the updated realm-export.json loaded.

- [x] **Task 4: Create `webauthn-register.ftl` — standalone registration page** (AC: #1, #3)
  - [x] Create `src/main/resources/theme/azguards-whatsapp/login/webauthn-register.ftl`
  - [x] **CRITICAL: Standalone HTML only — do NOT use `@layout.registrationLayout`** (same constraint as login-with-sso.ftl, login-magic-link.ftl — PatternFly CSS would break visual consistency)
  - [x] Structure: follows the same HTML skeleton as `login-with-sso.ftl` and `login-magic-link.ftl`:
    - `<!DOCTYPE html>` + `<html lang="en">` + `<head>` with same 3 CSS links (tokens.css, components.css, style.css)
    - Brand mark `<img>` with `onerror` fallback (copy exact pattern from login.ftl lines 18-24)
    - `<main class="wt-login-layout"><div class="wt-card">...</div></main>`
    - `<h1 tabindex="-1">` — focused on page load for screen reader announcement
  - [x] **Template states to handle:**
    - **State A (initial):** Show title (`msg("passkeyRegisterTitle")`), intro text (`msg("passkeyRegisterIntro")`), primary "Set up passkey" button that triggers the WebAuthn ceremony, and "Not now" skip link
    - **State B (retry — `isSetRetry == true`):** Show error message (`msg("passkeyRegisterError")`), "Try again" button, and "Not now" skip link
  - [x] **WebAuthn ceremony JavaScript — must be preserved verbatim:**
    - KC provides context variables: `challenge`, `userid`, `username`, `signatureAlgorithms`, `rpEntityName`, `rpId`, `attestationConveyancePreference`, `authenticatorAttachment`, `requireResidentKey`, `userVerificationRequirement`, `createTimeout`, `excludeCredentialIds`
    - **CRITICAL:** Verify these variable names against actual KC 26.6.3 `webauthn-register.ftl` source before use. Variable names can differ between KC versions — read the KC default template at runtime or from KC source to confirm exact names.
    - The ceremony JavaScript calls `navigator.credentials.create(publicKeyCredentialCreationOptions)` and posts the result to `url.loginAction` via a hidden form
    - Use KC's built-in `WebAuthnRegister` JS helper if available in KC 26.6.3 (check for `${url.resourcesPath}/js/webauthn-register.js` or equivalent), otherwise inline the ceremony logic from the KC base `webauthn-register.ftl`
    - The JS must be inside the `<body>` IIFE pattern — no inline `onclick` attributes calling IIFE-scoped functions (Epic 2 retro lesson P2)
  - [x] **Form structure for ceremony result submission:**
    ```html
    <form id="kc-webauthn-register-form" action="${url.loginAction}" method="post" style="display:none">
      <input type="hidden" id="clientDataJSON" name="clientDataJSON">
      <input type="hidden" id="attestationObject" name="attestationObject">
      <input type="hidden" id="publicKeyCredentialId" name="publicKeyCredentialId">
      <input type="hidden" id="authenticatorLabel" name="authenticatorLabel">
      <input type="hidden" id="error" name="error">
    </form>
    ```
    - Field names: verify against KC 26.6.3 `WebAuthnRegisterAuthenticator` or `WebAuthnRegister` SPI — the form field names used by KC's built-in handler must match exactly
  - [x] **"Not now" / Skip button:** posts to `url.loginAction` with no credential fields — KC drops the optional required action
  - [x] **a11y requirements (AC: #3):**
    - `<h1 tabindex="-1">` auto-focused on load: `document.querySelector('h1').focus()`
    - Status div with `role="status" aria-live="polite"` that announces "Follow the prompt on your device…" when ceremony starts
    - Error div with `role="alert" aria-live="assertive"` for retry state
    - Primary button minimum 44×44px touch target (use `.wt-btn.wt-btn--primary` component)
    - "Not now" link uses `<button type="button" class="wt-link-btn">` or anchor with visible focus ring
    - No hardcoded hex colors — use CSS custom properties from tokens.css only

- [x] **Task 5: Build and verify Repo 2** (AC: #3)
  - [x] `mvn package` → BUILD SUCCESS
  - [x] `grep -rn "#[0-9a-fA-F]\{3,6\}" src/main/resources/theme/azguards-whatsapp/login/webauthn-register.ftl` → 0 matches (zero hardcoded hex colors)
  - [x] Template renders in a local KC instance (deploy JAR + theme, run the `webauthn-register` required action for a test user) — verified via `WebAuthnRegisterRenderTest` (automated, KC Testcontainers + Playwright); test PASS. Two theme bugs found and fixed during render verification: (1) `?html` in `login.ftl` lines 138/143 conflicts with FreeMarker auto-escaping in KC 26.6.3 HTML output format — removed `?html`; (2) `message?is_hash` guard missing — `message` can be a scalar (e.g. `false`) when no login error is present, causing `NonHashException` on `.summary` access — added `message?is_hash` check and guarded `errorText` assignment with `showError?then(...)`.

---

## Dev Notes

### Working Repositories

```
keycloak-multi-tenancy (Repo 1 — realm config + i18n, complete first):
  src/test/resources/
    realm-export.json                        ← MODIFY (Task 1 — WebAuthn policy fields)
  src/main/resources/theme-resources/messages/
    messages_en.properties                   ← MODIFY (Task 2 — passkey i18n keys)

azguards-keycloak-custom-theme (Repo 2 — FTL, complete after Repo 1):
  src/main/resources/theme/azguards-whatsapp/login/
    webauthn-register.ftl                    ← NEW (Task 4)
```

**Do NOT create or modify** any Java files in Repo 1 — the built-in `webauthn-register` required action requires no extension code in KC 26.6.3.

**Do NOT modify** in Repo 2: `tokens.css`, `components.css`, `style.css`, `script.js`, `login.ftl` (passkey affordance on login.ftl is Story 3.2), any existing FTL files.

---

### KC 26.6.3 Built-in WebAuthn Required Action

The project targets **Keycloak 26.6.3** (`pom.xml` line 20: `<keycloak.version>26.6.3</keycloak.version>`).

**Built-in provider:** `org.keycloak.authentication.requiredactions.WebAuthnRegisterFactory`
- Provider ID: `webauthn-register`
- Already in `realm-export.json` (lines 2445-2451): `enabled: true, defaultAction: false`
- **Do NOT set `defaultAction: true`** — auto-enrollment is Story 3.4's enrollment prompt, not this story

**WebAuthn policy fields in `realm-export.json`** (standard, not passwordless):
- `webAuthnPolicyRpEntityName` — displayed in browser WebAuthn dialog ("Set up for WhataTalk")
- `webAuthnPolicyRpId` — empty = uses request hostname (correct; do not hardcode)
- `webAuthnPolicyRequireResidentKey: "required"` — makes credentials discoverable (passkeys, not just security keys)
- `webAuthnPolicyUserVerificationRequirement: "required"` — forces biometric/PIN (defines passkey as distinct from bare security key)
- `webAuthnPolicyAuthenticatorAttachment: "not specified"` — accepts Touch ID, Face ID, Windows Hello, hardware keys
- `webAuthnPolicySignatureAlgorithms` — keep `["ES256"]` minimum; RS256 can coexist
- `webAuthnPolicyCreateTimeout: 60` — seconds; 0 means browser default

**Triggering the required action:** An agent must have `webauthn-register` in their required actions list. This story does NOT add a trigger — triggering is handled by Story 3.4 (post-login enrollment prompt). To test manually: add `webauthn-register` required action to a test user via KC Admin Console.

---

### KC 26.6.3 WebAuthn Template Context Variables

> **⚠️ CRITICAL (retro lesson from Epic 2):** Verify these variable names against the actual KC 26.6.3 `webauthn-register.ftl` source before coding. KC changes template context variable names between versions. Check the KC source at: `https://github.com/keycloak/keycloak/blob/26.6.3/themes/src/main/resources/theme/base/login/webauthn-register.ftl`

Expected variables in `webauthn-register.ftl` context (typical KC 26.x):

| Variable | Type | Description |
|---|---|---|
| `challenge` | String | Base64URL-encoded WebAuthn challenge |
| `userid` | String | Base64URL-encoded user ID (user handle) |
| `username` | String | Human-readable username for display |
| `signatureAlgorithms` | String | JSON array string of COSEAlgorithm values, e.g. `[-7]` |
| `rpEntityName` | String | From `webAuthnPolicyRpEntityName` realm setting |
| `rpId` | String | From `webAuthnPolicyRpId` (may be empty) |
| `attestationConveyancePreference` | String | From realm policy |
| `authenticatorAttachment` | String | `"not specified"`, `"platform"`, or `"cross-platform"` |
| `requireResidentKey` | String | `"true"` or `"false"` (string, not boolean) |
| `userVerificationRequirement` | String | `"required"`, `"preferred"`, or `"discouraged"` |
| `createTimeout` | Number | Registration timeout in milliseconds (KC multiplies by 1000) |
| `excludeCredentialIds` | String | JSON array of already-registered credential IDs to exclude |
| `isSetRetry` | Boolean | `true` if this is a retry after failed registration |
| `url.loginAction` | String | Form POST target |
| `url.resourcesPath` | String | Theme resource base path |
| `msg("key")` | String | i18n message lookup |
| `kcSanitize(...)` | String | XSS-safe HTML sanitizer |

**JavaScript ceremony pattern (reference KC base theme — adapt, do not invent):**

```javascript
(function () {
  // Parse KC-provided context into WebAuthn PublicKeyCredentialCreationOptions
  var challenge = base64url.decode("${challenge?no_esc}");
  var userid = base64url.decode("${userid?no_esc}");
  var signatureAlgorithms = ${signatureAlgorithms?no_esc}; // already valid JSON
  var excludeCredentialIds = ${excludeCredentialIds?no_esc}; // already valid JSON

  var publicKey = {
    rp: {
      name: "${rpEntityName?js_string}",
      <#if rpId?has_content>id: "${rpId?js_string}",</#if>
    },
    user: {
      id: userid,
      name: "${username?js_string}",
      displayName: "${username?js_string}",
    },
    challenge: challenge,
    pubKeyCredParams: signatureAlgorithms.map(function(alg) {
      return { type: "public-key", alg: alg };
    }),
    timeout: ${createTimeout?c},
    excludeCredentials: excludeCredentialIds.map(function(id) {
      return { type: "public-key", id: base64url.decode(id) };
    }),
    authenticatorSelection: {
      <#if authenticatorAttachment != "not specified">
      authenticatorAttachment: "${authenticatorAttachment?js_string}",
      </#if>
      residentKey: "${requireResidentKey?js_string}",
      userVerification: "${userVerificationRequirement?js_string}",
    },
    attestation: "${attestationConveyancePreference?js_string}",
  };

  function startRegistration() {
    var statusEl = document.getElementById('passkey-status');
    if (statusEl) statusEl.textContent = "${msg("passkeyRegisterInProgress")?js_string}";

    navigator.credentials.create({ publicKey: publicKey })
      .then(function(credential) {
        // Encode result and post to loginAction
        document.getElementById('clientDataJSON').value =
          base64url.encode(credential.response.clientDataJSON);
        document.getElementById('attestationObject').value =
          base64url.encode(credential.response.attestationObject);
        document.getElementById('publicKeyCredentialId').value =
          base64url.encode(credential.rawId);
        document.getElementById('authenticatorLabel').value = "";
        document.getElementById('error').value = "";
        document.getElementById('kc-webauthn-register-form').submit();
      })
      .catch(function(err) {
        document.getElementById('error').value = err.toString();
        document.getElementById('kc-webauthn-register-form').submit();
      });
  }

  var registerBtn = document.getElementById('passkey-register-btn');
  if (registerBtn) {
    registerBtn.addEventListener('click', startRegistration);
  }

  document.querySelector('h1').focus();
}());
```

> **Note:** KC 26.6.3 may ship a `base64url` helper in scope; verify. If not, inline the decode/encode functions or load from KC's `/js/webauthn.js` resource. Check the base theme's `webauthn-register.ftl` for how it handles base64url.

---

### Form Field Names — Must Match KC Handler

The hidden form field names used by `WebAuthnRegisterAuthenticator` in KC 26.6.3:
- Verify via KC source: `https://github.com/keycloak/keycloak/blob/26.6.3/services/src/main/java/org/keycloak/authentication/requiredactions/WebAuthnRegisterAuthenticator.java`
- Typical field names: `clientDataJSON`, `attestationObject`, `publicKeyCredentialId`, `authenticatorLabel`, `error`
- If the KC handler uses different names → the ceremony silently fails (credential posted to wrong field name); always verify before shipping

---

### Skip / "Not Now" Behavior

The built-in `webauthn-register` required action in KC 26.x:
- If the required action is configured as optional (non-default), agents can skip it; the action is removed from their required-actions list
- Skip is achieved by POSTing to `url.loginAction` with no WebAuthn credential fields — KC interprets this as "decline" for OPTIONAL required actions
- If required action is REQUIRED (not optional), there is no skip path — the agent is blocked until they register
- For this story, `webauthn-register` is NOT `defaultAction: true` and NOT required in the browser flow — it will only be present if explicitly added to a user's required actions (e.g., by the Story 3.4 enrollment prompt). When present, check whether it's set optional or required for that user; render skip only if allowed.

---

### Standalone FTL Pattern (Mandatory)

All custom theme FTL files use standalone HTML, NOT `@layout.registrationLayout`. This prevents PatternFly CSS from loading and maintains visual consistency with the WhataTalk design system.

**Reference template structure** (copy chrome from `login-with-sso.ftl`):
```html
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Set up a passkey | WhataTalk</title>
    <link rel="icon" href="${url.resourcesPath}/img/whatatalk-favicon.png" type="image/png">
    <link rel="stylesheet" href="${url.resourcesPath}/css/tokens.css">
    <link rel="stylesheet" href="${url.resourcesPath}/css/components.css">
    <link rel="stylesheet" href="${url.resourcesPath}/css/style.css">
  </head>
  <body class="wt-login-page">
    <div class="background-overlay" aria-hidden="true"></div>
    <main class="wt-login-layout">
      <!-- Brand mark -->
      <div class="wt-login-brand">
        <img
          src="https://www.whatatalk.com/media/logos/logo-wide-svg.svg"
          alt="WhataTalk"
          onerror="this.onerror=null; this.src='${url.resourcesPath}/img/azguard.png'"
        >
      </div>
      <!-- Auth card -->
      <div class="wt-card">
        <h1 tabindex="-1">${msg("passkeyRegisterTitle")}</h1>
        <!-- state-based content -->
      </div>
    </main>
    <script>/* IIFE ceremony code */</script>
  </body>
</html>
```

---

### Epic 2 Retrospective Lessons — Must Apply to This Story

From `epic-2-retro-2026-06-15.md`:

| Lesson | Application to Story 3.1 |
|---|---|
| **JS scope (P2):** Functions called from HTML attributes must use `addEventListener` inside IIFE — never inline `onclick` calling IIFE-scoped functions | The `startRegistration()` function is attached via `registerBtn.addEventListener('click', startRegistration)` inside the IIFE. No `onclick="startRegistration()"` attributes. |
| **KC API constants must be verified (P3):** Absolute vs relative seconds, SPI registration file names, method signatures — verify against KC 26.6.3 Javadoc/source before use | Verify all context variable names (`challenge`, `userid`, form field names) against KC 26.6.3 `webauthn-register.ftl` source BEFORE writing the FTL. |
| **Clean working tree (S1):** Stash or commit adjacent uncommitted work before starting | Current working tree has uncommitted changes (git status shows multiple MM files). **Stash or commit all changes from Story 2.4 and tenant-CRUD work before starting Story 3.1.** |
| **Code review mandatory:** Must run on every story | After both repos complete, run `/bmad-code-review` before marking done. |
| **C5 (Epic 3 Critical Path):** WebAuthn SPI API research spike must complete before Story 3.1 dev starts | If C5 has not been completed (KC 26.6.3 `webauthn-register` FTL context variable names not verified), **pause and verify the KC source first**. Do not implement on assumptions. |

---

### No Tracing Required for Built-in Required Action

`TracingHelper` spans are added to custom Java SPI classes only (AR-12). The built-in `webauthn-register` required action cannot be instrumented without sub-classing the KC provider. **Do not create a wrapper required action just for tracing in this story.** Tracing over WebAuthn paths can be addressed as a deferred enhancement in a future story if needed.

---

### What Is Explicitly Out of Scope for Story 3.1

- **Passkey affordance on `login.ftl`** — that is Story 3.2 (`webauthn-authenticate`, not `webauthn-register`)
- **Post-login enrollment prompt** — that is Story 3.4 (`passkey_enrollment_dismissed` user attribute; OQ-8 resolved as once-only)
- **Graceful degradation on unsupported devices** — that is Story 3.3
- **`webauthn-register-passwordless`** — do NOT configure the passwordless WebAuthn policy or provider; it is a different KC mechanism from the standard passkey `webauthn-register`
- **Any modification to `realm-export.json` browser flow** — `webauthn-register` is a required action, not a browser flow execution; it is triggered per-user, not in the flow directly
- **Creating `webauthn-authenticate.ftl`** — that is Story 3.2
- **Touchpoints: `register.ftl`, `login-oauth-grant.ftl`, `email/` templates, admin tenant switcher** — AR-OOS, do not touch

---

### Test Strategy

This story's Repo 1 changes are realm policy only (no Java code), so no new unit tests are needed. Integration test coverage:

- **Manual verification (required before done):** Deploy updated realm + theme to a KC 26.6.3 instance, add `webauthn-register` required action to a test user, complete the registration ceremony with a platform authenticator (or WebAuthn emulator in Chrome DevTools)
- **Test harness update (optional):** If `BrowserIntegrationTest` or `BaseIntegrationTest` needs WebAuthn emulation for future automation, note it as a deferred task in `deferred-work.md` — Playwright WebAuthn virtual authenticator API (`context.addVirtualAuthenticator`) supports this; do not block this story on it

Build gate: `mvn package -DskipTests` → BUILD SUCCESS for both repos. All 39 existing tests must continue to pass (`mvn verify`).

---

### References

- Epics file: `_bmad-output/planning-artifacts/epics.md` — Epic 3, Story 3.1 (AR-4, FR-PK-1)
- Architecture: `_bmad-output/planning-artifacts/architecture.md` — WebAuthn SPI, KC 26.4+ platform requirement
- UX design: `_bmad-output/planning-artifacts/ux-designs/ux-keycloak-multi-tenancy-2026-06-11/EXPERIENCE.md` — passkey entry model
- Epic 2 retro: `_bmad-output/implementation-artifacts/epic-2-retro-2026-06-15.md` — process lessons + C5 prerequisite
- Previous story: `_bmad-output/implementation-artifacts/2-4-conditional-email-me-a-sign-in-link-magic-link.md` — standalone FTL pattern, tracing pattern, IIFE JS pattern
- realm-export.json: `src/test/resources/realm-export.json` lines 388-411 (WebAuthn policy), lines 2445-2461 (required actions)
- KC 26.6.3 source (verify context variables): `https://github.com/keycloak/keycloak/blob/26.6.3/themes/src/main/resources/theme/base/login/webauthn-register.ftl`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- Integration tests (mvn verify) fail with `ContainerFetchException` because Docker daemon is not running in dev environment. Pre-existing constraint unrelated to this story's changes. Build gate `mvn package -DskipTests` passes for both repos.

### Completion Notes List

- ✅ Task 1: realm-export.json WebAuthn policy updated — `webAuthnPolicyRpEntityName: WhataTalk`, `webAuthnPolicyRequireResidentKey: required`, `webAuthnPolicyUserVerificationRequirement: required`, `webAuthnPolicyCreateTimeout: 60`. Passwordless fields untouched.
- ✅ Task 2: 8 passkey i18n keys appended to messages_en.properties (no duplicates).
- ✅ Task 3: Repo 1 `mvn package -DskipTests` → BUILD SUCCESS. 8 passkeyRegister keys confirmed. No new Java files.
- ✅ Task 4: `webauthn-register.ftl` created in Repo 2 — standalone HTML, no registrationLayout, State A/B, inline base64url helpers, IIFE ceremony JS, addEventListener pattern, h1 focus, polite/assertive ARIA regions, skip form posts empty to loginAction.
- ✅ Task 5: Repo 2 `mvn package` → BUILD SUCCESS. Zero hardcoded hex colors confirmed. Template render verified via `WebAuthnRegisterRenderTest` — KC 26.6.3 Testcontainers + Playwright, PASS. Two `login.ftl` bugs found and fixed: `?html` auto-escaping conflict (lines 138/143 → dropped `?html`); `message?is_hash` guard missing (lines 29/31 → added guard to handle scalar `message` variable).

### File List

**keycloak-multi-tenancy (Repo 1):**
- `src/test/resources/realm-export.json` (MODIFY — Task 1: WebAuthn policy fields)
- `src/main/resources/theme-resources/messages/messages_en.properties` (MODIFY — Task 2: passkey i18n keys)

**azguards-keycloak-custom-theme (Repo 2):**
- `src/main/resources/theme/azguards-whatsapp/login/webauthn-register.ftl` (NEW — Task 4)
- `src/main/resources/theme/azguards-whatsapp/login/login.ftl` (MODIFY — Task 5 render verification: drop `?html` auto-escaping conflict, add `message?is_hash` guard)

**keycloak-multi-tenancy (Repo 1 — render test infrastructure):**
- `src/test/java/dev/sultanov/keycloak/multitenancy/WebAuthnRegisterRenderTest.java` (NEW — Task 5: automated render verification)
- `src/test/java/dev/sultanov/keycloak/multitenancy/support/BaseIntegrationTest.java` (MODIFY — Task 5: include theme JAR in KC provider libs for render tests)

## Change Log

- 2026-06-15: Story 3.1 created — Username-bound passkey registration. Repo 1: realm-export.json WebAuthn policy configuration + i18n keys. Repo 2: standalone webauthn-register.ftl using Epic 1 tokens/components. No new Java SPI code — built-in KC 26.6.3 webauthn-register required action covers the registration ceremony.
- 2026-06-15: Story 3.1 implemented — Repo 1: realm-export.json passkey policy (rpEntityName=WhataTalk, requireResidentKey=required, userVerificationRequirement=required, createTimeout=60), 8 passkeyRegister i18n keys added. Repo 2: webauthn-register.ftl created (standalone HTML, IIFE ceremony JS, inline base64url helpers, State A/B, a11y). Both repos build successfully.
- 2026-06-15: Story 3.1 render verification — KC 26.6.3 Testcontainers + Playwright render test (WebAuthnRegisterRenderTest) confirms webauthn-register.ftl renders correctly. Two login.ftl bugs discovered and fixed: (1) `?html` conflict with FreeMarker auto-escaping HTML output format (lines 138/143); (2) missing `message?is_hash` guard on lines 29/31. BaseIntegrationTest updated to include theme JAR in KC provider libs for render testing.

## Review Findings

> Code review 2026-06-15 (`/bmad-code-review`, 3 adversarial layers + KC-source verification). Theme file path: `azguards-keycloak-custom-theme/src/main/resources/theme/azguards-whatsapp/login/webauthn-register.ftl`. All four high-severity findings independently verified against upstream KC base template (`keycloak/themes/.../base/login/webauthn-register.ftl` + `webauthnRegister.js`). Root cause across the criticals: the deferred manual KC render test (Task 5, unchecked) meant runtime template/JS breakage was never exercised.

### Decision Needed

> Both resolved during review (2026-06-15): browser-support dead-end → deferred to Story 3.3; user-cancel → route to decline/skip (now a patch below).

### Patches

- [x] [Review][Patch] **`signatureAlgorithms` & `excludeCredentialIds` emitted bare via `?no_esc` — not JSON; breaks the script on a normal load** — KC provides `signatureAlgorithms` as a FreeMarker *sequence* (base renders `[<#list signatureAlgorithms as sigAlg>${sigAlg?c},</#list>]`) and `excludeCredentialIds` as a *comma-separated String* (base wraps `'${excludeCredentialIds}'` then JS does `.split(',')`). The template emits both bare (`var x = ${...?no_esc};`) → FreeMarker sequence-interpolation error / JS `SyntaxError`/`ReferenceError`, and `.map()` on a string. Replicate base handling. [webauthn-register.ftl:96-97, 110, 114]
- [x] [Review][Patch] **WebAuthn `timeout` is 60 ms, not 60 s; `createTimeout==0` unguarded** — base JS does `publicKey.timeout = input.createTimeout * 1000` and skips assignment when `0`. Template uses `timeout: ${createTimeout?c}` directly → 60 ms (instant abort on real authenticators). Multiply by 1000 and omit `timeout` when 0. [webauthn-register.ftl:113]
- [x] [Review][Patch] **"Not now" / Skip does not map to KC cancel — posts an empty form → server error path, not a clean decline; also shown unconditionally** — KC's cancel control is `name="cancel-aia" value="true"` gated on `<#if !isSetRetry?has_content && isAppInitiatedAction?has_content>`. The skip form posts no fields, so `processAction` falls through to `Base64…decode(null clientDataJSON)` → exception → registration-error/retry instead of skip. Add a `cancel-aia=true` hidden input and gate the skip control on `isAppInitiatedAction`. [webauthn-register.ftl:62-66]
- [x] [Review][Patch] **Three referenced CSS classes do not exist → unstyled error banner & skip control** — `wt-alert`, `wt-alert--error`, `wt-link-btn` are absent from the theme CSS (verified by grep). The retry/error banner and the "Skip" affordance render unstyled. Use the existing alert pattern (`wt-banner` / `wt-toast--*` in components.css) and an existing link/button class, or add the missing classes to components.css. [webauthn-register.ftl:32, 63]
- [x] [Review][Patch] **`?js_string` does not neutralize `</script>` inside the inline `<script>` (XSS hardening)** — `username` (and other `?js_string` values) are interpolated into an inline script; `?js_string` escapes JS metachars but not the `</script>` HTML-parser breakout. If usernames are attacker-influenced this is stored XSS. (`challenge`/`userid` are base64url so practically safe, but escaping is still correct.) Add HTML-context escaping or move data into safely-encoded data attributes. [webauthn-register.ftl:101-129]
- [x] [Review][Patch] **User-cancel should route to decline/skip, not error-retry** (resolved decision) — in `.catch`, detect `NotAllowedError`/`AbortError` and route to the decline path (the `cancel-aia` skip flow) instead of posting `err.toString()` to the `error` field, so dismissing the OS prompt exits cleanly rather than bouncing into the retry screen. Pairs with the Skip-form patch above. [webauthn-register.ftl:146-149]

### Deferred

- [x] [Review][Defer] **Dirty working tree — unrelated Story 2.4 / tenant-CRUD changes pollute the story diff** — Repo 1 carries many uncommitted unrelated files (contradicts Epic 2 retro S1 lesson). Story 3.1's own files are correct, but unrelated changes should be separated before merge. — deferred, process/pre-existing
- [x] [Review][Defer] **External logo dependency with no SRI in a security-critical auth flow** — `<img src="https://www.whatatalk.com/...">` with local `onerror` fallback; copied verbatim from the existing `login.ftl` pattern, so consistent project-wide. — deferred, pre-existing convention
- [x] [Review][Defer] **Unsupported-browser / insecure-context dead-end** — no `window.PublicKeyCredential` feature-detect; on an unsupported browser `navigator.credentials.create` throws synchronously, leaving the button disabled and the user stuck. — deferred (decision 2026-06-15): Story 3.3 owns full graceful passkey fallback/degradation [webauthn-register.ftl:134, 152-153]

## Review Findings (re-verification 2026-06-15)

> Re-review (`/bmad-code-review`, 3 adversarial layers) scoped to confirming the 6 prior patches landed correctly. **All 6 patches independently verified as correctly applied** (Acceptance Auditor, cross-checked vs KC 26.6.3 base `webauthn-register.ftl` + `webauthnRegister.js`): JSON array/string emission, `timeout` seconds→ms + `==0` omit, `cancel-aia=true` skip gated on `isAppInitiatedAction`, no phantom CSS classes (all 10 referenced classes resolve), `</script>`-hardened `?js_string` on user-controlled fields, and `NotAllowedError`/`AbortError`→decline routing. Edge Case Hunter's "missing i18n keys" finding was a **false positive** — the 8 `passkeyRegister*` keys live in the extension's `theme-resources/messages/messages_en.properties` (merged into all themes via the theme-resources SPI; the theme repo grep couldn't see them). No HIGH/MED in-scope defect found; remaining items are defense-in-depth hardening + items already owned by Story 3.3.

### Decision Needed

- [x] [Review][Decision→Defer] **`NotAllowedError` also fires on ceremony timeout / platform failure — current cancel→skip routing silently abandons enrollment on timeout** — Patch 6 routes any `NotAllowedError`/`AbortError` to the silent `cancel-aia` skip form (when AIA). But WebAuthn throws `NotAllowedError` for ceremony *timeout* and some platform-authenticator failures too, not only a deliberate user dismissal. — **deferred to Story 3.3** (decision 2026-06-15): the WebAuthn spec deliberately makes user-cancel and timeout indistinguishable client-side, so reliable split-routing isn't achievable here; folds into 3.3's graceful-degradation/error-handling work. [webauthn-register.ftl:162-167]

### Patches

- [x] [Review][Patch] **FreeMarker render-crash guard on `authenticatorAttachment`** — `<#if authenticatorAttachment != "not specified">` raises a render error if the var is ever null/undefined (unlike `rpId` which uses `?has_content`). KC's policy sets it today, but guard defensively: `<#if (authenticatorAttachment!"") != "not specified">`. LOW / defensive. [webauthn-register.ftl:126]
- [x] [Review][Patch] **Complete `</script>` breakout hardening uniformly (finish Patch 5)** — `username`/`rpEntityName`/`rpId` carry `?replace("</","<\\/")` but the remaining inline-script interpolations do not: `challenge`/`userid` use bare `?no_esc` (swap to `?js_string`, which preserves base64url), and `authenticatorAttachment`/`requireResidentKey`/`userVerificationRequirement`/`attestationConveyancePreference`/`msg(...InProgress)` lack the `</`-replace. All realm-config/i18n/base64url-sourced → nil practical risk, but completes the patch intent. LOW / defense-in-depth. [webauthn-register.ftl:97-98, 127, 129, 130, 132, 141]
- [x] [Review][Patch] **Wrap eager top-level `base64url.decode` in try/catch** — `challenge`/`userid` are decoded at IIFE load (outside any handler); a malformed server value makes `atob` throw `InvalidCharacterError`, aborting the whole IIFE so the click listener never attaches and the button is inert with no error surfaced. Wrap the eager decodes (+ `publicKey` build) and route failures to the error form. LOW / robustness (requires a server bug to trigger). [webauthn-register.ftl:97-98, 119-124]
- [x] [Review][Patch] **Null-guard `h1` focus** — `document.querySelector('h1').focus()` runs unconditionally and is the only DOM access without a null guard (every `getElementById` is guarded). h1 is statically present so safe today; guard for consistency/future-proofing: `var h=document.querySelector('h1'); if(h)h.focus();`. LOW. [webauthn-register.ftl:176]

### Deferred

- [x] [Review][Defer] **No WebAuthn feature detection → silent dead-end on unsupported/insecure-context browsers** — `navigator.credentials.create` throws a *synchronous* `TypeError` (bypasses `.catch`) when `navigator.credentials` is undefined (no WebAuthn / plain HTTP); button stays disabled, status stuck. — deferred: Story 3.3 owns graceful passkey fallback/degradation (re-confirms prior deferral) [webauthn-register.ftl:146]
- [x] [Review][Defer] **Unhandled rejection types surfaced as opaque `err.toString()`** — only `NotAllowedError`/`AbortError` are special-cased; `InvalidStateError` (passkey already registered on this authenticator → confusing infinite-retry loop), `SecurityError`, `NotSupportedError` all fall through to the generic retry with a raw error string. — deferred: belongs with Story 3.3 graceful degradation [webauthn-register.ftl:162-168]

## Review Findings (third pass 2026-06-15)

> Re-review (`/bmad-code-review`, 3 adversarial layers: Blind Hunter / Edge Case Hunter / Acceptance Auditor) scoped to `webauthn-register.ftl` (committed `d7a2e7f`) + `login.ftl` (uncommitted Task-5 fixes), verified against the actual source and the KC base `webauthn-register.ftl` + `webauthnRegister.js`. Acceptance Auditor confirmed all 3 ACs, all Task-4 requirements, all out-of-scope constraints, and all 10 prior patches are correctly present. New findings below come from the Edge/Blind layers exercising runtime ceremony semantics (the path the never-run manual ceremony test would have covered). 1 decision-needed, 4 patch, 3 defer, 5 dismissed.

### Decision Needed

- [x] [Review][Decision→Patch] **`residentKey` / `userVerification` emit the raw KC policy string — correct only because the realm sets `"required"`; diverges from KC base mapping** — RESOLVED 2026-06-15: chose **option (a)** — leave as-is (the `"required"` realm config is authoritative); add a guard comment documenting the dependency so a future realm-policy change away from `"required"` doesn't silently emit an invalid enum. Folded into the patch set below. Template emits `residentKey: '${requireResidentKey?js_string...}'` and `userVerification: '${userVerificationRequirement?js_string...}'` verbatim. The WebAuthn `authenticatorSelection.residentKey`/`userVerification` enums accept only `discouraged`/`preferred`/`required`. This realm's policy sets both to `"required"` (Task 1), which happens to be a valid enum value, so the deployed config works. But KC's own `webauthnRegister.js` treats `requireResidentKey` as `"Yes"`/`"No"`/`"not specified"` → maps to boolean `requireResidentKey: true/false` and OMITS the field on `"not specified"`; likewise it omits `userVerification` when `"not specified"`. If the realm policy is ever changed to default/`"not specified"`/`"Yes"`/`"No"`, this template silently emits an invalid enum and the resident-key / UV policy is not enforced. **Decision:** (a) leave as-is and treat the `"required"` realm config as authoritative (add a guard comment), (b) replicate KC base mapping (`Yes/No`→bool, omit on `not specified`) for robustness, or (c) verify empirically via the deferred manual ceremony test (Chrome DevTools virtual authenticator) before deciding. [webauthn-register.ftl:136-137]

### Patches

- [x] [Review][Patch] **Guard comment for `residentKey`/`userVerification` realm-policy dependency** (from resolved decision, option a) — add a comment above the `authenticatorSelection` block noting that `requireResidentKey`/`userVerificationRequirement` are emitted verbatim and rely on the realm policy being a valid WebAuthn enum (`required`); a change to `not specified`/`Yes`/`No` would silently emit an invalid enum. [webauthn-register.ftl:132-137]
- [x] [Review][Patch] **Synchronous throw from `navigator.credentials.create` is not caught** — only `.catch()` handles async rejection; a synchronous `TypeError` (malformed `publicKey` dictionary) escapes, leaving the button disabled and the status region stuck on "Follow the prompt…" with no error surfaced. Wrap the `.create().then().catch()` chain in try/catch and route a sync throw to the error/retry form. [webauthn-register.ftl:165]
- [x] [Review][Patch] **`excludeCredentialIds` is the only inline-script value still interpolated bare** — `var excludeCredentialIds = '${excludeCredentialIds}';` lacks the `?js_string?replace("</","<\\/")` hardening applied to every other interpolation; this is the gap that the re-review "uniform </script> hardening" patch claimed to close. Practically safe (comma-separated base64url) but inconsistent. Add `?js_string` (commas are preserved). [webauthn-register.ftl:109]
- [x] [Review][Patch] **Missing `transports` field — credential stored without transport hints** — KC base `webauthn-register.ftl` ships a hidden `transports` input populated from `credential.response.getTransports()`; this template omits both. KC tolerates its absence, but the stored credential loses transport metadata, degrading Story 3.2 authentication UX. Add the hidden input and populate it in the success handler. [webauthn-register.ftl:46-52, 167-175]
- [x] [Review][Patch] **`login.ftl` `errorText` now coupled to `showError` — lockout detection narrowed to `error`/`warning` types** — `errorText = showError?then((message.summary!"")?lower_case, "")` ties lockout-token detection to the type filter, so a lockout message arriving with any other type (or as a scalar) yields `errorText = ""` and falls through to generic credential-error styling. The `?is_hash` crash guard is correct and should stay; decouple errorText from the type filter: `errorText = message?is_hash?then((message.summary!"")?lower_case, "")`. Low risk in standard KC (lockout is `type=error`). [login.ftl (working tree):~30]

### Deferred

- [x] [Review][Defer] **No WebAuthn feature/secure-context detection → dead-end on unsupported or insecure-context (plain-HTTP) browsers** — `navigator.credentials` is undefined → synchronous `TypeError` that bypasses `.catch`; button stays disabled, status stuck. — deferred: Story 3.3 owns graceful passkey fallback/degradation (re-confirms prior deferral) [webauthn-register.ftl:165]
- [x] [Review][Defer] **Malformed server-rendered base64url → infinite retry loop in a non-AIA flow** — if `challenge`/`userid`/an excluded ID is corrupt, decode throws, the error is POSTed, KC re-renders the identical bad values, and retry hits the same failure with no skip path when `isAppInitiatedAction` is false. — deferred: requires a server-side bug to trigger; non-AIA is not this story's trigger path (triggering is Story 3.4) [webauthn-register.ftl:153-157]
- [x] [Review][Defer] **`authenticatorLabel` is hardwired empty — no "name your passkey" UI** — KC auto-generates a default label, but users cannot distinguish multiple passkeys later. — deferred: minor UX enhancement, outside this story's scope [webauthn-register.ftl:173]

### Dismissed (5, with reasoning)

- **`?html` removal on `login.ftl` hidden inputs is an XSS regression** — FALSE POSITIVE (Blind Hunter lacked KC-version context): the removal was a deliberate, render-test-verified Task-5 fix because KC 26.6.3 login templates render with HTML auto-escaping output format (`?html` double-escapes); the values (`authExecId`, `magicLinkAuthExecId`) are trusted KC-internal IDs, not user-influenced.
- **`signatureAlgorithms` / `createTimeout` lack null-safety** — KC always populates these for `webauthn-register`; the passing render test confirms they are present.
- **Button never re-enabled on the catch path** — benign: every catch branch submits a form and navigates away, so the disabled state never persists (self-acknowledged by the reporting layer).
- **`id="error"` is a generic global id** — no collision on this standalone single-page template; style nit only.
- **Trailing comma in the `signatureAlgorithms` array literal** — legal in modern JS; not a defect.

## Review Findings (fourth pass — patch verification 2026-06-15)

> Re-review (`/bmad-code-review`, 3 adversarial layers) after the 5 third-pass patches were applied, scoped to confirming they landed correctly without regression. **Result: CLEAN — 0 new actionable findings.** Acceptance Auditor confirmed all 5 patches present/correct and all 3 ACs + Task-4 requirements + out-of-scope constraints + 10 prior patches still satisfied with zero regression. Edge Case Hunter confirmed all 5 patches fully close their edge cases (verified the sync-throw catch routes to KC's error path before any decode, `transports.join(',')`↔KC `split(",")` round-trip, and `errorText` lockout-gating consistency). Blind Hunter's 6 items all dismissed: `?html`-removal "XSS" is the recurring no-context false positive (KC login auto-escapes; values are trusted execution IDs); `?then` "evaluates both branches" is a false positive (FreeMarker `?then` is lazy — only the selected branch evaluates); `?js_string`-only on challenge/userid/excludeCredentialIds is non-exploitable (base64url alphabet cannot contain `</script>`); the empty-hash `message.type` and `setupError` echo items are theoretical/out-of-scope; `authenticatorLabel` empty is already deferred. Story remains `done`. Theme `mvn package` → BUILD SUCCESS; 0 hardcoded hex.
