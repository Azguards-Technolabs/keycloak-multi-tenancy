---
baseline_commit_extension: 7f51fae39e426e282ef127975a848f14e9d9bcf3
baseline_commit_theme: d435581
baseline_commit: 7f51fae39e426e282ef127975a848f14e9d9bcf3
---

# Story 3.2: Passkey-first authentication on login

Status: ready-for-dev

## Story

As a returning Agent with a registered passkey,
I want to sign in with my passkey as the primary option,
So that I authenticate in one tap without typing a password.

## Acceptance Criteria

1. **Given** the WebAuthn SPI is integrated against KC 26.6.3 (AR-4) and a passkey is registered for the current username (Story 3.1) **When** the login screen renders **Then** "Use your passkey" is offered as the primary affordance, displayed above the password field (FR-PK-2, FR-L-3)

2. **Given** the passkey affordance **When** the Agent activates it **Then** the browser WebAuthn prompt appears (also triggerable via the `autocomplete="username webauthn"` token already on the username field) **And** on success the Agent is authenticated and proceeds to the next step

3. **Given** the `webauthn-authenticate.ftl` template **When** rendered **Then** it uses the Epic 1 tokens/components and meets the a11y floor (h1 focus, labels, aria-live regions)

## Tasks / Subtasks

---

### REPO 1: `keycloak-multi-tenancy`

> Complete all Repo 1 tasks before starting Repo 2. The flow wiring must be present in realm-export.json before the theme integration can be tested end-to-end.

- [ ] **Task 1: Wire `webauthn-authenticator` into the browser flow in realm-export.json** (AC: #1, #2)
  - [ ] Open `src/test/resources/realm-export.json`
  - [ ] Locate the top-level `browser custom` flow (alias `"browser custom"`, builtIn `false`) — it already contains `login-with-sso` as ALTERNATIVE at priority 31
  - [ ] Add `webauthn-authenticator` as a new ALTERNATIVE execution **at priority 32** in the `browser custom` top-level flow, immediately after `login-with-sso`:
    ```json
    {
      "authenticator": "webauthn-authenticator",
      "authenticatorFlow": false,
      "requirement": "ALTERNATIVE",
      "priority": 32,
      "autheticatorFlow": false,
      "userSetupAllowed": false
    }
    ```
  - [ ] **CRITICAL: Verify the provider ID** — KC 26.6.3's non-passwordless WebAuthn authenticator factory uses provider ID `webauthn-authenticator` (class `WebAuthnAuthenticatorFactory`). Check KC 26.6.3 source: `https://github.com/keycloak/keycloak/blob/26.6.3/services/src/main/java/org/keycloak/authentication/authenticators/browser/WebAuthnAuthenticatorFactory.java` — confirm `getId()` returns `"webauthn-authenticator"` (not `"webauthn-password-less"`). Do NOT confuse with the passwordless variant.
  - [ ] Do NOT change `browser custom forms` sub-flow — `auth-username-password-form` stays REQUIRED at priority 10. Passkey selection bypasses the forms sub-flow entirely via the top-level ALTERNATIVE, the same mechanism used by `login-with-sso` (Story 2.3).
  - [ ] Do NOT touch other flows (browser, direct grant, reset credentials, etc.)

- [ ] **Task 2: Add passkey authentication i18n keys to extension messages bundle** (AC: #2, #3)
  - [ ] Open `src/main/resources/theme-resources/messages/messages_en.properties`
  - [ ] Check for duplicates: `grep -n "passkeyAuth\|passkeyAffordance" src/main/resources/theme-resources/messages/messages_en.properties` — must return 0 matches before adding
  - [ ] Append the following keys:
    ```properties
    # Passkey authentication (Story 3.2)
    passkeyAuthTitle=Sign in with your passkey
    passkeyAuthBtn=Use your passkey
    passkeyAuthInProgress=Follow the prompt on your device…
    passkeyAuthError=Passkey sign-in failed — try again or use your password
    passkeyAuthRetry=Try again
    passkeyAuthUsePassword=Use password instead
    passkeyAffordanceBtn=Use your passkey
    ```
  - [ ] Verify count: `grep -c "passkeyAuth\|passkeyAffordance" src/main/resources/theme-resources/messages/messages_en.properties` → 7

- [ ] **Task 3: Build and verify Repo 1** (AC: #1)
  - [ ] `mvn package -DskipTests` → BUILD SUCCESS
  - [ ] `grep -c "passkeyAuth\|passkeyAffordance" src/main/resources/theme-resources/messages/messages_en.properties` → 7
  - [ ] No new Java files created — the built-in `webauthn-authenticator` provider (`WebAuthnAuthenticatorFactory`) requires no extension code in KC 26.6.3
  - [ ] Verify realm-export.json has `webauthn-authenticator` as ALTERNATIVE at priority 32 in `browser custom` top-level flow: `grep -A 6 "webauthn-authenticator" src/test/resources/realm-export.json`

---

### REPO 2: `azguards-keycloak-custom-theme`

> Start only after Repo 1 tasks are complete. Test both FTL changes against a running KC 26.6.3 instance with the updated realm-export.json loaded.

- [ ] **Task 4: Create `webauthn-authenticate.ftl` — the WebAuthn ceremony page** (AC: #2, #3)
  - [ ] Create `src/main/resources/theme/azguards-whatsapp/login/webauthn-authenticate.ftl`
  - [ ] **CRITICAL: Standalone HTML only — do NOT use `@layout.registrationLayout`** (same constraint as `login-with-sso.ftl`, `login-magic-link.ftl`, `webauthn-register.ftl`)
  - [ ] **Structure:** follows the same chrome as `webauthn-register.ftl` (verified in Story 3.1):
    - `<!DOCTYPE html>` + `<html lang="en">` + `<head>` with same 3 CSS links (tokens.css, components.css, style.css)
    - Brand mark `<img>` with `onerror` fallback (same pattern as login.ftl lines 18-24)
    - `<div class="background-overlay" aria-hidden="true"></div>`
    - `<main class="wt-login-layout"><div class="wt-login-brand">...</div><div class="wt-card">...</div></main>`
    - `<h1 tabindex="-1">` — focused on page load
  - [ ] **CRITICAL: Verify KC 26.6.3 `webauthn-authenticate.ftl` context variables BEFORE coding**
    - Check KC source: `https://github.com/keycloak/keycloak/blob/26.6.3/themes/src/main/resources/theme/base/login/webauthn-authenticate.ftl`
    - And the JS helper: `https://github.com/keycloak/keycloak/blob/26.6.3/themes/src/main/resources/theme/keycloak/login/resources/js/webauthnAuthenticate.js`
    - Expected context variables (verify each name before use — KC changes these between versions):
      | Variable | Type | Description |
      |---|---|---|
      | `challenge` | String | Base64URL-encoded WebAuthn challenge |
      | `userVerificationRequirement` | String | `"required"`, `"preferred"`, or `"discouraged"` |
      | `rpId` | String | Relying party ID (may be empty — use `?has_content` guard) |
      | `createTimeout` | Number | Timeout value (verify unit: seconds or ms; base JS multiplies by 1000) |
      | `authenticators` | Sequence | List of registered WebAuthn credentials for this user |
      | `shouldDisplayAuthenticators` | Boolean | Whether to list credentials to the user |
      | `isSetRetry` | Boolean | `true` if retrying after a failed attempt |
      | `url.loginAction` | String | Form POST target |
      | `url.resourcesPath` | String | Theme resource base path |
      | `msg("key")` | String | i18n message lookup |
  - [ ] **Template states to handle:**
    - **State A (initial):** Show title (`msg("passkeyAuthTitle")`), primary "Use your passkey" button, "Use password instead" fallback link
    - **State B (retry — `isSetRetry == true`):** Show error message (`msg("passkeyAuthError")`), "Try again" button, "Use password instead" fallback link
  - [ ] **Hidden result form (verify field names against KC 26.6.3 `WebAuthnAuthenticator.java`):**
    ```html
    <form id="kc-webauthn-authenticate-form" action="${url.loginAction}" method="post" style="display:none">
      <input type="hidden" id="clientDataJSON" name="clientDataJSON">
      <input type="hidden" id="authenticatorData" name="authenticatorData">
      <input type="hidden" id="signature" name="signature">
      <input type="hidden" id="credentialId" name="credentialId">
      <input type="hidden" id="userHandle" name="userHandle">
      <input type="hidden" id="error" name="error">
    </form>
    ```
    - **CRITICAL: Verify** these field names against KC 26.6.3 source: `https://github.com/keycloak/keycloak/blob/26.6.3/services/src/main/java/org/keycloak/authentication/authenticators/browser/WebAuthnAuthenticator.java` — look for `context.getHttpRequest().getDecodedFormParameters()` to confirm exact parameter names. Wrong names = silent auth failure.
  - [ ] **WebAuthn get ceremony JavaScript (IIFE pattern, no inline onclick):**
    ```javascript
    (function () {
      'use strict';

      // Inline base64url helper (same as webauthn-register.ftl — paste verbatim)
      var base64url = {
        decode: function (s) {
          s = s.replace(/-/g, '+').replace(/_/g, '/');
          while (s.length % 4) { s += '='; }
          return Uint8Array.from(atob(s), function(c) { return c.charCodeAt(0); }).buffer;
        },
        encode: function (buf) {
          return btoa(String.fromCharCode.apply(null, new Uint8Array(buf)))
            .replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
        }
      };

      var publicKey;
      try {
        // ⚠ VERIFY: Context variable names and KC's timeout unit before use.
        // KC base JS typically multiplies createTimeout * 1000 (seconds → ms).
        var allowCredentials = [];
        <#list authenticators as auth>
        // Build allowCredentials from registered authenticators (only for non-discoverable fallback)
        // For discoverable passkeys (resident keys), allowCredentials MUST be empty so the browser
        // shows all available passkeys rather than requiring a specific one.
        // IF the realm policy uses requireResidentKey=required (as set in Story 3.1), leave empty.
        // VERIFY against KC 26.6.3 base template for the correct approach.
        </#list>

        publicKey = {
          challenge: base64url.decode("${challenge?no_esc}"),
          <#if rpId?has_content>rpId: "${rpId?js_string?replace("<\/","<\\/")}",</#if>
          userVerification: "${userVerificationRequirement?js_string}",
          <#if (createTimeout!0) != 0>timeout: ${createTimeout?c} * 1000,</#if>
          // allowCredentials: leave empty for discoverable passkeys (resident keys).
          // Story 3.1 sets webAuthnPolicyRequireResidentKey = "required", so passkeys are
          // discoverable — the browser surfaces them without allowCredentials.
          // VERIFY this approach against KC 26.6.3 base webauthn-authenticate.ftl before shipping.
          allowCredentials: []
        };
      } catch (e) {
        // Malformed server-rendered value — route to error form immediately
        var errInp = document.getElementById('error');
        if (errInp) { errInp.value = 'setup-error: ' + e.toString(); }
        var f = document.getElementById('kc-webauthn-authenticate-form');
        if (f) { f.submit(); }
        return;
      }

      function startAuthentication() {
        var statusEl = document.getElementById('passkey-status');
        if (statusEl) { statusEl.textContent = "${msg("passkeyAuthInProgress")?js_string?replace("<\/","<\\/")}"; }

        var btn = document.getElementById('passkey-auth-btn');
        if (btn) { btn.disabled = true; }

        navigator.credentials.get({ publicKey: publicKey, mediation: 'optional' })
          .then(function (credential) {
            document.getElementById('clientDataJSON').value =
              base64url.encode(credential.response.clientDataJSON);
            document.getElementById('authenticatorData').value =
              base64url.encode(credential.response.authenticatorData);
            document.getElementById('signature').value =
              base64url.encode(credential.response.signature);
            document.getElementById('credentialId').value =
              base64url.encode(credential.rawId);
            var userHandle = credential.response.userHandle;
            document.getElementById('userHandle').value =
              userHandle ? base64url.encode(userHandle) : '';
            document.getElementById('error').value = '';
            document.getElementById('kc-webauthn-authenticate-form').submit();
          })
          .catch(function (err) {
            // NotAllowedError / AbortError = user dismissed — route to retry (error field)
            document.getElementById('error').value = err.toString();
            document.getElementById('kc-webauthn-authenticate-form').submit();
          });
      }

      var authBtn = document.getElementById('passkey-auth-btn');
      if (authBtn) { authBtn.addEventListener('click', startAuthentication); }

      // Focus management — h1 for screen reader announcement on page load
      var h1 = document.querySelector('h1');
      if (h1) { h1.focus(); }
    }());
    ```
  - [ ] **"Use password instead" link:** routes back to password login by posting `authenticationExecution=` of the `browser custom forms` sub-flow. Alternatively, a simple `<a href="...">` that reloads the login URL. **VERIFY** the correct KC mechanism for routing back to password from the WebAuthn ceremony page — check whether the base `webauthn-authenticate.ftl` uses a "try another way" link or back navigation. Replicate the mechanism.
  - [ ] **A11y requirements (AC: #3):**
    - `<h1 tabindex="-1">` auto-focused on load
    - Status div: `id="passkey-status" role="status" aria-live="polite"` — announces "Follow the prompt…" when ceremony starts
    - Error div (retry state): `role="alert" aria-live="assertive"` for error announcement
    - Primary button: `.wt-btn.wt-btn--primary` minimum 44×44px touch target
    - "Use password instead": `<a>` or `<button type="button">` with visible focus ring
    - No hardcoded hex colors — CSS custom properties only
    - No `console.*` in production code

- [ ] **Task 5: Update `login.ftl` — add passkey affordance above the password field** (AC: #1)
  - [ ] Open `src/main/resources/theme/azguards-whatsapp/login/login.ftl`
  - [ ] Add a passkey affordance button BETWEEN the username field and the password field (after `</div><!-- username -->` and before `<!-- Password -->`):
    ```html
    <!-- Passkey affordance — Story 3.2 — shown when passkeyAuthExecId is configured -->
    <#if (properties.passkeyAuthExecId!'') != ''>
    <button
      type="button"
      class="wt-btn wt-btn--primary"
      id="passkey-affordance-btn"
      aria-label="${msg("passkeyAffordanceBtn")}"
    >${msg("passkeyAffordanceBtn")}</button>
    <!-- Hidden form: posts authenticationExecution to switch KC flow to webauthn-authenticate -->
    <form id="kc-select-passkey-form" action="${url.loginAction}" method="post" style="display:none">
      <input type="hidden" name="authenticationExecution" value="${(properties.passkeyAuthExecId!'')?html}">
    </form>
    </#if>
    ```
  - [ ] Add the passkey button's click handler inside the existing IIFE in `login.ftl` (after the magic-link handler section, before the closing `}());`):
    ```javascript
    /* ── Story 3.2: Passkey affordance ── */
    var passkeyBtn  = document.getElementById('passkey-affordance-btn');
    var passkeyForm = document.getElementById('kc-select-passkey-form');
    if (passkeyBtn && passkeyForm) {
      passkeyBtn.addEventListener('click', function () {
        try { localStorage.setItem('wt_last_auth_method', 'passkey'); } catch (e) {}
        passkeyForm.submit();
      });
    }
    ```
  - [ ] **Do NOT change** the existing `autocomplete="username webauthn"` on the username field — this is already present (Story 3.1 baseline) and enables browser Conditional UI for passkeys
  - [ ] **Verify `wt-btn--primary` class conflict** — the passkey affordance uses `.wt-btn.wt-btn--primary` which is the same class as the "Login" submit button. If both appear on the same page, verify the visual hierarchy is correct (passkey is above password, Login is the password-flow primary action). Consider whether the passkey button should use a distinct style (e.g., `.wt-btn.wt-btn--passkey` or a secondary button style). Match the UX design intent: passkey is the "primary affordance" per FR-PK-2, so primary styling is correct when a passkey is registered. See EXPERIENCE.md for visual hierarchy guidance.

- [ ] **Task 6: Build and verify Repo 2** (AC: #3)
  - [ ] `mvn package` → BUILD SUCCESS
  - [ ] `grep -rn "#[0-9a-fA-F]\{3,6\}" src/main/resources/theme/azguards-whatsapp/login/webauthn-authenticate.ftl` → 0 matches
  - [ ] `grep -rn "console\." src/main/resources/theme/azguards-whatsapp/login/webauthn-authenticate.ftl` → 0 matches
  - [ ] `grep -rn "console\." src/main/resources/theme/azguards-whatsapp/login/login.ftl` → 0 matches
  - [ ] Template renders in a local KC 26.6.3 instance:
    - Deploy updated realm-export.json + theme JARs
    - Configure `passkeyAuthExecId` in the `azguards-whatsapp` theme's `theme.properties` — set it to the UUID of the new `webauthn-authenticator` execution in the `browser custom` flow (find it in KC Admin Console → Authentication → browser custom → execution list)
    - Log in as a test user who has a registered passkey (from Story 3.1); verify the "Use your passkey" button appears above the password field
    - Click it; verify the browser WebAuthn authentication prompt appears
    - On success, verify authentication completes

---

## Dev Notes

### Working Repositories

```
keycloak-multi-tenancy (Repo 1 — flow wiring + i18n, complete first):
  src/test/resources/
    realm-export.json                             ← MODIFY (Task 1 — add webauthn-authenticator execution)
  src/main/resources/theme-resources/messages/
    messages_en.properties                        ← MODIFY (Task 2 — passkey auth i18n keys)

azguards-keycloak-custom-theme (Repo 2 — FTL, complete after Repo 1):
  src/main/resources/theme/azguards-whatsapp/login/
    webauthn-authenticate.ftl                     ← NEW (Task 4)
    login.ftl                                     ← MODIFY (Task 5 — passkey affordance above password)
```

**Do NOT create or modify** any Java files in Repo 1 — the built-in `webauthn-authenticator` required action requires no extension code in KC 26.6.3.

**Do NOT modify** in Repo 2: `tokens.css`, `components.css`, `style.css`, `webauthn-register.ftl` (Story 3.1), any existing FTL files except `login.ftl`.

---

### KC 26.6.3 Browser Flow: Current Structure

Current `browser custom` flow (active flow, `"browserFlow": "browser custom"`):

```
browser custom (top-level, builtIn: false):
  ├─ auth-cookie              ALTERNATIVE  priority 10
  ├─ auth-spnego              DISABLED     priority 20
  ├─ identity-provider-redirector ALTERNATIVE priority 25
  ├─ browser custom forms     ALTERNATIVE  priority 30  ← sub-flow
  │    ├─ auth-username-password-form  REQUIRED  priority 10  → renders login.ftl
  │    └─ browser custom Browser - Conditional OTP  CONDITIONAL  priority 20
  ├─ login-with-sso           ALTERNATIVE  priority 31  → renders login-with-sso.ftl  [Story 2.3]
  └─ webauthn-authenticator   ALTERNATIVE  priority 32  → renders webauthn-authenticate.ftl  [NEW — Task 1]
```

**How alternative selection works:** When `login.ftl` posts `authenticationExecution=<exec-id>` to `url.loginAction`, KC selects the ALTERNATIVE execution matching that ID at the top level of `browser custom`. This is the same mechanism that routes SSO (Story 2.3) and magic-link (Story 2.4). The `passkeyAuthExecId` theme property holds the UUID of the `webauthn-authenticator` execution after it is added to the flow.

---

### KC 26.6.3 WebAuthn Authenticate Provider

**Built-in provider:** `org.keycloak.authentication.authenticators.browser.WebAuthnAuthenticatorFactory`
- Provider ID: `webauthn-authenticator` — **verify via KC 26.6.3 source before use**
- Do NOT use `webauthn-authenticator-passwordless` — that is for the separate passwordless flow
- The factory registers itself in `META-INF/services/org.keycloak.authentication.AuthenticatorFactory` in the KC distribution JAR — no entry needed in the extension's services file

**Realm policy applicable to authentication:**
- `webAuthnPolicyRequireResidentKey: "required"` (set in Story 3.1) → passkeys are DISCOVERABLE credentials
- Discoverable = allowCredentials MUST be empty in the `navigator.credentials.get()` call; the browser surfaces all available passkeys without needing to specify credential IDs
- **VERIFY** this is the correct KC 26.6.3 behavior for the WebAuthn authenticate flow

---

### `passkeyAuthExecId` Theme Property Configuration

After adding the execution to `realm-export.json`, the runtime KC instance assigns it a UUID. The theme must be told this UUID via `theme.properties`:

```properties
# In: src/main/resources/theme/azguards-whatsapp/login/theme.properties
passkeyAuthExecId=<UUID of webauthn-authenticator execution in browser custom flow>
```

**How to find the UUID:** After importing the updated realm-export.json into KC, go to Admin Console → Authentication → `browser custom` flow → copy the execution ID for `Webauthn Authenticator` (or similar label).

**Design note:** This matches the `magicLinkAuthExecId` pattern from Story 2.4 — an admin-configured property that activates the affordance. The `passkeyAuthExecId` value is not in the exported JSON (it is realm-specific and runtime-assigned). In the test realm (from realm-export.json), the execution is present but its runtime UUID will differ from any hardcoded value.

---

### `webauthn-authenticate.ftl` — Context Variable Verification (MANDATORY)

> **⚠️ CRITICAL (retro lesson from Epic 2 / Story 3.1):** ALWAYS verify KC context variable names against the actual KC 26.6.3 source BEFORE writing FTL code. KC changes variable names between versions. A wrong variable name causes a FreeMarker render crash or silent JS failure.

**KC 26.6.3 webauthn-authenticate.ftl source:**
`https://github.com/keycloak/keycloak/blob/26.6.3/themes/src/main/resources/theme/base/login/webauthn-authenticate.ftl`

**KC 26.6.3 webauthnAuthenticate.js helper:**
`https://github.com/keycloak/keycloak/blob/26.6.3/themes/src/main/resources/theme/keycloak/login/resources/js/webauthnAuthenticate.js`

**KC 26.6.3 WebAuthnAuthenticator.java (form field names):**
`https://github.com/keycloak/keycloak/blob/26.6.3/services/src/main/java/org/keycloak/authentication/authenticators/browser/WebAuthnAuthenticator.java`

Cross-check the following before committing the template:
- Variable `challenge` — confirm exact name and encoding (Base64URL string vs binary)
- Variable `createTimeout` — confirm units (seconds? KC may multiply by 1000 already)
- Variable `authenticators` — confirm type (FreeMarker sequence vs JSON string)
- Variable `userVerificationRequirement` — confirm exact string values
- Variable `rpId` — confirm null/empty handling
- Form field names: `clientDataJSON`, `authenticatorData`, `signature`, `credentialId`, `userHandle`, `error` — confirm each against the Java handler

---

### `webauthn-authenticate.ftl` — "Use password instead" Mechanism

The "Use password instead" fallback must route the user back to the password form (`auth-username-password-form` → `login.ftl`). Options, in order of preference:

1. **KC "try another way" pattern (preferred):** KC 26.x provides an `isUserSetupAllowed` check and a "try another way" form. Check if the base `webauthn-authenticate.ftl` includes this and replicate the mechanism. Typically: `<form action="${url.loginAction}" method="post"><input type="hidden" name="tryAnotherWay" value="on"><button type="submit">...</button></form>`
2. **Fresh login URL redirect:** `<a href="${url.loginUrl}">` — navigates to a fresh login, losing current session state. Only use if option 1 is not available.
3. **`authenticationExecution` switch back:** Post `authenticationExecution=<forms-sub-flow-exec-id>` to switch to `browser custom forms`. Less reliable across KC versions.

**VERIFY** the correct mechanism against KC 26.6.3 `webauthn-authenticate.ftl` base template before implementing. Do not invent a custom mechanism if KC provides one.

---

### Standalone FTL Pattern (Mandatory)

All custom theme FTL files use standalone HTML, NOT `@layout.registrationLayout`. Copy the chrome from `webauthn-register.ftl` (created in Story 3.1) verbatim:

```html
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Sign in with your passkey | WhataTalk</title>
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
        <h1 tabindex="-1">${msg("passkeyAuthTitle")}</h1>
        <!-- state-based content -->
      </div>
    </main>
    <script>/* IIFE ceremony code */</script>
  </body>
</html>
```

---

### IIFE JavaScript Pattern (Mandatory — Epic 2 Retro Lesson P2)

All JavaScript in FTL templates MUST use the IIFE pattern with `addEventListener`. Functions called from user interaction must be attached via `addEventListener` inside the IIFE — never inline `onclick="functionName()"` attributes:

```javascript
// CORRECT — inside IIFE, attached via addEventListener
var btn = document.getElementById('passkey-auth-btn');
if (btn) { btn.addEventListener('click', startAuthentication); }

// WRONG — never do this
// <button onclick="startAuthentication()">
```

---

### `login.ftl` — Passkey Affordance Positioning

Current login.ftl layout (lines 45-106):
```
username field (div.wt-field)
password field (div.wt-field)     ← passkey button goes ABOVE this
Login button (button.wt-btn--primary)
```

The passkey affordance section goes between the username `</div>` and `<!-- Password -->` comment. It is gated on `(properties.passkeyAuthExecId!'') != ''` so it is invisible until the admin configures the property.

**Epic 2 retro lesson S1 reminder:** The working tree currently has multiple uncommitted modified files from Stories 2.4 and earlier (visible in `git status`). Before starting Repo 2 changes, ensure this story's branch is clean or that unrelated changes are stashed/committed so the diff remains legible for code review.

---

### CSS Classes Available (Verified in Story 3.1)

Available in `components.css` and used in prior templates:
- `.wt-btn.wt-btn--primary` — primary action button (min 44×44px, full-width in card)
- `.wt-btn.wt-btn--ghost` — ghost/secondary action
- `.wt-card` — auth card container
- `.wt-login-layout`, `.wt-login-brand`, `.wt-login-page` — layout chrome
- `.wt-field`, `.wt-field__label`, `.wt-field__input` — form field components

**Do NOT use** classes that don't exist in `components.css` (they'll render unstyled). Story 3.1's review found `wt-alert`, `wt-alert--error`, `wt-link-btn` were absent — avoid inventing new class names. Run `grep -r "wt-<class>" src/main/resources/theme/azguards-whatsapp/css/components.css` to verify before using.

---

### No Tracing Required for Built-in Authenticator

`TracingHelper` spans are added to custom Java SPI classes only (AR-12). The built-in `webauthn-authenticator` provider cannot be instrumented without sub-classing. **Do not create a wrapper authenticator just for tracing** — consistent with the decision made in Story 3.1 for `webauthn-register`.

---

### What Is Explicitly Out of Scope for Story 3.2

- **Graceful degradation when no passkey is registered or device doesn't support WebAuthn** — Story 3.3. The passkey button is visible whenever `passkeyAuthExecId` is set; Story 3.3 adds client-side detection to conditionally hide/show it.
- **Post-login enrollment prompt** — Story 3.4.
- **`webauthn-register.ftl` changes** — Story 3.1 (complete, do not touch).
- **Passwordless WebAuthn flow** (`webauthn-authenticator-passwordless`) — do NOT configure; it is a distinct KC mechanism.
- **`aria-live` announcement on passkey fallback to password** — Story 3.3 (explicitly in that story's AC).
- **Any modification to `register.ftl`, `login-oauth-grant.ftl`, `email/` templates, admin tenant switcher** — AR-OOS.
- **Modifications to other browser flows** (direct grant, reset credentials, first-broker-login).

---

### Test Strategy

Repo 1 changes are flow wiring + i18n only (no Java code), so no new unit tests are needed. Integration test coverage:

- **Manual verification (required before done):** Deploy updated realm-export.json + theme JARs to a KC 26.6.3 instance. Configure `passkeyAuthExecId` in `theme.properties`. For a test user with a registered passkey (from Story 3.1):
  1. Login page shows "Use your passkey" above the password field ✓
  2. Clicking it triggers the browser WebAuthn authentication prompt ✓
  3. On successful passkey use, the Agent is authenticated and proceeds to the next KC step ✓
  4. For a test user without a passkey, the button still appears (Story 3.3 will fix the conditional display) — verify it doesn't break the login flow (the WebAuthn ceremony fails gracefully, or the user can use "Use password instead")
- **Build gate:** `mvn package -DskipTests` → BUILD SUCCESS for Repo 1. `mvn package` → BUILD SUCCESS for Repo 2.
- **Regression gate:** All 39 existing Repo 1 integration tests must continue to pass (`mvn verify`) — these fail locally due to Docker not running (pre-existing constraint, unrelated to this story's changes). Confirm via CI.

---

### References

- Epics file: `_bmad-output/planning-artifacts/epics.md` — Epic 3, Story 3.2 (AR-4, FR-PK-2, FR-L-3)
- Architecture: `_bmad-output/planning-artifacts/architecture.md` — WebAuthn SPI, KC 26.4+ platform requirement
- UX design: `_bmad-output/planning-artifacts/ux-designs/ux-keycloak-multi-tenancy-2026-06-11/EXPERIENCE.md` — passkey-first entry model
- Previous story: `_bmad-output/implementation-artifacts/3-1-username-bound-passkey-registration.md` — standalone FTL pattern, IIFE JS pattern, base64url helpers, form field verification approach, retro lessons
- Sprint status: `_bmad-output/implementation-artifacts/sprint-status.yaml`
- Deferred work: `_bmad-output/implementation-artifacts/deferred-work.md` — Story 3.1 deferrals (feature-detect → Story 3.3; unhandled rejection types → Story 3.3)
- realm-export.json: `src/test/resources/realm-export.json` — `browser custom` flow (lines ~1882), `browserFlow` setting (line ~2499)
- KC 26.6.3 source (verify context variables): `https://github.com/keycloak/keycloak/blob/26.6.3/themes/src/main/resources/theme/base/login/webauthn-authenticate.ftl`
- KC 26.6.3 WebAuthn authenticator (verify form field names): `https://github.com/keycloak/keycloak/blob/26.6.3/services/src/main/java/org/keycloak/authentication/authenticators/browser/WebAuthnAuthenticator.java`
- KC 26.6.3 WebAuthnAuthenticatorFactory (verify provider ID): `https://github.com/keycloak/keycloak/blob/26.6.3/services/src/main/java/org/keycloak/authentication/authenticators/browser/WebAuthnAuthenticatorFactory.java`
- login.ftl (current): `azguards-keycloak-custom-theme/src/main/resources/theme/azguards-whatsapp/login/login.ftl` — 273 lines; passkey affordance inserts between lines 57 and 59 (between username `</div>` and `<!-- Password -->`)

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

(none yet)

### Completion Notes List

(none yet)

### File List

**keycloak-multi-tenancy (Repo 1):**
- `src/test/resources/realm-export.json` (MODIFY — Task 1: add webauthn-authenticator execution)
- `src/main/resources/theme-resources/messages/messages_en.properties` (MODIFY — Task 2: passkey auth i18n keys)

**azguards-keycloak-custom-theme (Repo 2):**
- `src/main/resources/theme/azguards-whatsapp/login/webauthn-authenticate.ftl` (NEW — Task 4)
- `src/main/resources/theme/azguards-whatsapp/login/login.ftl` (MODIFY — Task 5: passkey affordance above password field)

## Change Log

- 2026-06-15: Story 3.2 created — Passkey-first authentication on login. Repo 1: add webauthn-authenticator execution to browser custom flow + 7 passkey auth i18n keys. Repo 2: new webauthn-authenticate.ftl (standalone HTML, IIFE get ceremony, States A/B, "Use password instead" fallback, a11y) + login.ftl update (passkey affordance above password field via passkeyAuthExecId theme property, IIFE listener, localStorage method tracking).
