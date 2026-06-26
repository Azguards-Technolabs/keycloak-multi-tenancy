---
baseline_commit: 7f51fae39e426e282ef127975a848f14e9d9bcf3
---

# Story 2.3: "Sign in with SSO" Secondary Link

Status: done

## Story

As an Agent whose org uses an IdP,
I want a single "Sign in with SSO" link rather than a raw alias box on the main screen,
so that the primary login stays uncluttered.

## Acceptance Criteria

1. **Given** the login screen **When** it renders **Then** the alias input is NOT on the primary screen; instead a "Sign in with SSO" method-row link is shown in the `.wt-methods` area (FR-L-5)
2. **Given** the Agent clicks "Sign in with SSO" **When** the action fires **Then** the SSO alias-entry screen is presented using WhataTalk tokens/components, styled consistently with the login screen (not the old Keycloak base-theme form)
3. **Given** the IdP redirect is in progress **When** the Agent has submitted a valid SSO name **Then** an interstitial ("Redirecting…") is shown via JS on the `login-with-sso.ftl` page (UX-DR12)
4. **Given** an IdP-redirect failure / access-denied **When** the Agent is returned to the login flow **Then** a calm error with "Use password instead" method-row is shown — never a dead-end (UX-DR16)
5. **Given** the Agent enters an invalid SSO name **When** the form submits **Then** an inline field error "That SSO name doesn't match. Check with your admin." appears below the field with `wt-field--error` styling and `aria-invalid` (UX-DR3, UX-DR7)
6. **Given** the SSO screen loads **When** it renders **Then** focus is on the `sso-id` input (autofocus); on field error, `wtA11y.focusFirstError(document)` is called (UX-DR14)
7. **Given** the Agent clicks "Sign in with SSO" **When** the navigation triggers **Then** `localStorage.setItem('wt_last_auth_method', 'sso')` is written (matches the `'password'` pattern from Story 2.1)

## Tasks / Subtasks

- [x] **Task 1: `messages_en.properties` — Override SSO copy** (AC: #2, #5)
  - [x] Add `ssoHeader=Sign in with SSO`
  - [x] Add `ssoInfo=Enter your organisation's SSO name to continue.`
  - [x] Add `ssoError=That SSO name doesn't match. Check with your admin.`
  - [x] `ssoLabel=SSO name` — keep (already matches UX copy)
  - [x] Verify no duplicate keys: `sort -t= -k1 messages_en.properties | uniq -d -f0` → no output

- [x] **Task 2: `login.ftl` — Add "Sign in with SSO" method-row** (AC: #1, #7)
  - [x] Replace the `<!-- 2.3: ... -->` placeholder comment inside `.wt-methods` with the actual method-row markup (see Dev Notes: login.ftl Changes)
  - [x] Add hidden credential-selection form (see Dev Notes: KC authenticationExecution Mechanism)
  - [x] Add `loginWithSso()` JS function + `wt_last_auth_method` write inside the existing IIFE (after the error-focus block)
  - [x] Guard the SSO method-row with `<#if authenticationSelection??>` so the link is silently absent if the SSO alternative is not configured in the realm flow

- [x] **Task 3: `login-with-sso.ftl` (NEW) — Create styled SSO alias-entry screen** (AC: #2–#7)
  - [x] Create file at `src/main/resources/theme/azguards-whatsapp/login/login-with-sso.ftl`
  - [x] This is a STANDALONE HTML file — same structure as `login.ftl`, NOT using `<@layout.registrationLayout>` (see Dev Notes: Why Standalone)
  - [x] Include the standard HTML head with tokens.css, components.css, style.css, favicon
  - [x] Render the WhataTalk brand mark (same `onerror` fallback as login.ftl)
  - [x] Auth card with:
    - `<h1>Sign in with SSO</h1>` (using the `${msg("ssoHeader")}` i18n key)
    - `<p>` info text (`${msg("ssoInfo")}`)
    - SSO name field (`wt-field`, with `wt-field--error` when `messagesPerField.existsError('sso-id')`)
    - Field error message via `wt-field__error` (below the input)
    - Primary "Continue" button (`wt-btn wt-btn--primary`) with submitting spinner
    - Interstitial div (initially hidden, shown on submit — see Dev Notes: Interstitial)
    - "Use password instead" form (method-row style, uses `tryAnotherWay=true` — see Dev Notes)
  - [x] Load `script.js` and add inline IIFE for: autofocus, field-error focus, interstitial on submit, bfcache reset
  - [x] NO `autocomplete` token on the sso-id field (it's a custom alias, not a standard credential)

- [x] **Task 4: Build verification**
  - [x] `cd ~/WorkSpace/azguards-whatsapp/azguards-keycloak-custom-theme && mvn package` → `BUILD SUCCESS`
  - [x] `grep -n "#[0-9a-fA-F]\{3,\}" src/main/resources/theme/azguards-whatsapp/login/login-with-sso.ftl` → zero hits (no hardcoded hex)
  - [x] `grep -n "#[0-9a-fA-F]\{3,\}" src/main/resources/theme/azguards-whatsapp/login/login.ftl` — no NEW hits in the changed lines

---

## Dev Notes

### Working Repository

**100% `azguards-keycloak-custom-theme`.** No changes to `keycloak-multi-tenancy`.

```
~/WorkSpace/azguards-whatsapp/azguards-keycloak-custom-theme/
  src/main/resources/theme/azguards-whatsapp/login/
    messages/
      messages_en.properties          ← MODIFY (Task 1)
    login.ftl                         ← MODIFY (Task 2)
    login-with-sso.ftl                ← CREATE (Task 3) — new file
```

**Do NOT modify** `tokens.css`, `components.css`, `style.css`, `script.js` — all required styles and utilities already exist.

---

### SSO Authentication Flow Architecture

The `login-with-sso` authenticator is wired into the **`browser custom` Keycloak authentication flow** as:

```
browser custom flow:
  auth-cookie             (ALTERNATIVE, priority 10)  — automatic
  auth-spnego             (DISABLED,    priority 20)
  identity-provider-redirector (ALTERNATIVE, priority 25) — automatic
  browser custom forms    (ALTERNATIVE, priority 30)  — renders login.ftl
    └── auth-username-password-form (REQUIRED)
  login-with-sso          (ALTERNATIVE, priority 31)  — renders login-with-sso.ftl
```

The `LoginWithSsoAuthenticator` (`LoginWithSsoAuthenticator.java`) implements:
- `authenticate()` → renders `login-with-sso.ftl` (no Java changes needed)
- `action()` → takes `sso-id` POST param, looks up IdP alias; on match redirects to IdP; on miss shows `ssoError` field error

The `login-with-sso.ftl` in the extension's `theme-resources/templates/` (the OLD one using `@layout.registrationLayout`) is **overridden** by our custom theme's version at the same template name — Keycloak looks up theme templates from the custom theme before falling back to theme-resources. So putting `login-with-sso.ftl` in our custom theme directory replaces the extension's old version entirely.

---

### KC `authenticationExecution` Mechanism (login.ftl → login-with-sso.ftl)

The README for the `keycloak-multi-tenancy` extension documents a direct-link approach for switching to the SSO authenticator from `login.ftl`:

```html
<!-- Hidden form to select the SSO execution in the KC authentication flow -->
<form id="kc-select-sso-form" action="${url.loginAction}" method="post">
  <input type="hidden" id="authexec-sso-input" name="authenticationExecution">
</form>
```

When `name="authenticationExecution"` is submitted with the SSO authenticator's execution ID, Keycloak switches the active alternative in the flow from `browser custom forms` to `login-with-sso`.

The SSO execution ID is exposed via `authenticationSelection.authExecId` in the FTL context — this variable is set by Keycloak when the flow has alternatives at the same level. The README confirms this is available in `login.ftl` when `login-with-sso` is an ALTERNATIVE in the flow.

**Guard always:** Wrap everything with `<#if authenticationSelection??>` so the SSO link is silently absent if the realm's authentication flow is reconfigured without the `login-with-sso` alternative.

---

### login.ftl Changes (Task 2 — exact changes)

#### Change 1: Replace the `.wt-methods` placeholder comments with the SSO method-row

**Current (lines 119–122 in login.ftl):**
```html
        <!-- Method rows — Stories 2.3 (SSO) and 2.4 (magic link) inject here -->
        <div class="wt-methods" id="auth-methods" aria-label="Other sign-in options">
          <!-- 2.3: <a class="wt-method-row" ...>Sign in with SSO</a> -->
          <!-- 2.4: <button class="wt-method-row" ...>Email me a sign-in link</button> -->
        </div>
```

**Replace with:**
```html
        <!-- Method rows — SSO (2.3) and magic link (2.4) -->
        <div class="wt-methods" id="auth-methods" aria-label="Other sign-in options">
          <#if authenticationSelection??>
          <button
            type="button"
            class="wt-method-row"
            id="sso-method-btn"
            onclick="loginWithSso()"
          >
            <!-- SSO globe icon -->
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24"
                 fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                 stroke-linejoin="round" aria-hidden="true" focusable="false">
              <circle cx="12" cy="12" r="10"/>
              <line x1="2" y1="12" x2="22" y2="12"/>
              <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/>
            </svg>
            Sign in with SSO
          </button>
          <!-- Hidden form: posts authenticationExecution to switch KC flow to login-with-sso -->
          <form id="kc-select-sso-form" action="${url.loginAction}" method="post">
            <input type="hidden" id="authexec-sso-input" name="authenticationExecution">
          </form>
          </#if>
          <!-- 2.4: <button class="wt-method-row" ...>Email me a sign-in link</button> -->
        </div>
```

#### Change 2: Add `loginWithSso()` to the inline IIFE

Inside the existing `(function () { 'use strict'; ... }());` in `login.ftl`, add after the Story 2.2 error-focus IIFE block (before the closing `}());`):

```js
        /* ── Story 2.3: SSO method-row ── */
        function loginWithSso() {
          try { localStorage.setItem('wt_last_auth_method', 'sso'); } catch (e) {}
          var ssoInput = document.getElementById('authexec-sso-input');
          var ssoForm  = document.getElementById('kc-select-sso-form');
          if (ssoInput && ssoForm) {
            ssoInput.value = '${authenticationSelection.authExecId!}';
            ssoForm.submit();
          }
        }
```

**Why `${authenticationSelection.authExecId!}` instead of `${authenticationSelection.authExecId}`:** The `!` (default operator) produces an empty string instead of a FTL error if `authExecId` is somehow null. The outer `<#if authenticationSelection??>` guard handles the case where the whole object is absent, but the `!` is a safety net.

---

### login-with-sso.ftl Full Implementation (Task 3)

This is a **standalone HTML file** identical in structure to `login.ftl` — no Keycloak base layout import. The extension's `@layout.registrationLayout` approach is replaced entirely.

```html
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Sign in with SSO | WhataTalk</title>
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

        <#assign hasSsoError = messagesPerField?? && messagesPerField.existsError('sso-id')>

        <h1 class="wt-login-title">${msg("ssoHeader")}</h1>
        <p class="wt-login-subtitle">${msg("ssoInfo")}</p>

        <!-- SSO alias-entry form -->
        <form action="${url.loginAction}" method="post" class="wt-login-form" id="ssoForm" novalidate>

          <div class="wt-field<#if hasSsoError> wt-field--error</#if>">
            <label class="wt-field__label" for="sso-id">${msg("ssoLabel")}</label>
            <input
              class="wt-field__input"
              type="text"
              id="sso-id"
              name="sso-id"
              required
              autofocus
              <#if hasSsoError>aria-invalid="true" aria-describedby="sso-error"</#if>
            >
            <#if hasSsoError>
            <div class="wt-field__error" id="sso-error" role="alert" aria-live="polite">
              ${kcSanitize(messagesPerField.get('sso-id'))?no_esc}
            </div>
            </#if>
          </div>

          <!-- Primary action -->
          <button type="submit" id="ssoSubmitBtn" class="wt-btn wt-btn--primary">
            Continue
          </button>

        </form>

        <!-- Interstitial: hidden by default, shown via JS on valid submit -->
        <div class="wt-interstitial" id="ssoInterstitial" style="display:none" aria-live="polite">
          <div class="wt-interstitial__spinner" aria-hidden="true"></div>
          <p class="wt-interstitial__motion-text">Redirecting to your SSO provider…</p>
          <p class="wt-interstitial__caption" aria-hidden="true">Redirecting…</p>
        </div>

        <!-- "Use password instead" — switches KC flow back to username/password form -->
        <#if auth?? && auth.showTryAnotherWay>
        <div class="wt-methods" aria-label="Other sign-in options">
          <form id="try-another-way-form" action="${url.loginAction}" method="post">
            <input type="hidden" name="tryAnotherWay" value="true">
            <button type="submit" class="wt-method-row">
              Use password instead
            </button>
          </form>
        </div>
        </#if>

      </div><!-- /.wt-card -->

    </main><!-- /.wt-login-layout -->

    <script src="${url.resourcesPath}/js/script.js"></script>
    <script>
      (function () {
        'use strict';

        /* ── Submit: show interstitial + last-used method ── */
        var form        = document.getElementById('ssoForm');
        var submitBtn   = document.getElementById('ssoSubmitBtn');
        var interstitial = document.getElementById('ssoInterstitial');

        if (form && submitBtn) {
          form.addEventListener('submit', function () {
            if (!form.checkValidity()) { return; }
            try { localStorage.setItem('wt_last_auth_method', 'sso'); } catch (e) {}
            /* Hide form, show interstitial — server will either redirect to IdP
             * (interstitial was seen briefly) or return a new page with a field error
             * (new page load removes interstitial automatically). */
            form.style.display = 'none';
            if (interstitial) { interstitial.style.display = ''; }
            submitBtn.disabled = true;
          });

          /* bfcache / back-navigation: restore form if page is cached */
          window.addEventListener('pageshow', function (evt) {
            if (evt.persisted) {
              form.style.display = '';
              if (interstitial) { interstitial.style.display = 'none'; }
              submitBtn.disabled = false;
            }
          });
        }

        /* ── Focus management ── */
        (function () {
          var hasSsoError = document.getElementById('sso-error');
          if (hasSsoError) {
            /* Field error present: focus the invalid sso-id input */
            if (typeof wtA11y !== 'undefined') { wtA11y.focusFirstError(document); }
          }
          /* No error: autofocus attribute on #sso-id handles initial focus */
        }());

      }());
    </script>
  </body>
</html>
```

---

### Why Standalone HTML (Not `@layout.registrationLayout`)

Our existing custom theme templates (`login.ftl`, `select-tenant.ftl`, `review-invitations.ftl`) are all **standalone HTML**, not using the Keycloak base theme's `template.ftl` layout. The extension's original `login-with-sso.ftl` uses `@layout.registrationLayout` — that old version is in `keycloak-multi-tenancy/src/main/resources/theme-resources/templates/login-with-sso.ftl` and uses Bootstrap/PatternFly classes.

Our custom theme file at `azguards-keycloak-custom-theme/src/main/resources/theme/azguards-whatsapp/login/login-with-sso.ftl` **completely overrides** the extension's version. Keycloak's template resolution checks the custom theme directory first. The standalone approach is mandatory because:
1. `@layout.registrationLayout` imports `template.ftl` from the Keycloak base theme — we'd inherit the old CSS framework
2. All Epic 1 components and tokens are on the standalone template's CSS stack only
3. Visual consistency requires using our CSS (not the base theme's PatternFly)

---

### Interstitial Behavior

The interstitial (UX-DR12) is shown **client-side** when the SSO form submits:
- JS hides the form (`form.style.display = 'none'`)
- JS shows `#ssoInterstitial` (`display = ''`)
- The form submission proceeds normally

Two outcomes from here:
- **Valid alias** → server redirects to IdP (302) → interstitial is shown briefly, then the browser navigates to the IdP login page ✓
- **Invalid alias** → server re-renders `login-with-sso.ftl` with `hasSsoError=true` → new page load: form is shown with error, interstitial is gone ✓

On bfcache restore (back-button), the pageshow handler re-shows the form and hides the interstitial.

The interstitial structure follows `components.css` exactly:
- `.wt-interstitial` — wrapper (flex column, centered, min-height 200px)
- `.wt-interstitial__spinner` — spinning ring (hidden under `prefers-reduced-motion`)
- `.wt-interstitial__caption` — "Redirecting…" text (hidden under `prefers-reduced-motion`)
- `.wt-interstitial__motion-text` — "Redirecting to your SSO provider…" (shown under `prefers-reduced-motion`, hidden otherwise)

---

### "Use Password Instead" / Try Another Way

The `tryAnotherWay=true` form POST is the standard Keycloak mechanism for switching between ALTERNATIVE authenticators in a flow. When `login-with-sso.ftl` renders:
- `auth.showTryAnotherWay` is `true` when Keycloak has another alternative at the same flow level (which it does: `browser custom forms` with the username/password form)
- Submitting the `tryAnotherWay=true` form makes KC re-evaluate the flow and present the next ALTERNATIVE — which will be the username/password form → `login.ftl`

Guard with `<#if auth?? && auth.showTryAnotherWay>` so it's silently absent if the auth flow is changed to have no alternative.

This pattern satisfies AC #4: "on IdP-redirect failure / access-denied → calm error with 'Use password instead' → back to login". When KC receives an IdP error and returns the Agent to the flow, the `login-with-sso.ftl` will re-render with the `wt-methods` "Use password instead" option available.

---

### SSO i18n Keys — What Exists vs What to Add

**Extension's `messages_en.properties`** (`keycloak-multi-tenancy/src/main/resources/theme-resources/messages/messages_en.properties`) — these are the current defaults:
```
ssoHeader=Initiate Single Sign-on (SSO)
ssoLabel=SSO name
ssoInfo=Initiate Single Sign-on (SSO) by entering your SSO name.
ssoError=Could not find an identity provider with this SSO name.
```

**Theme's `messages_en.properties`** (`azguards-keycloak-custom-theme/.../messages/messages_en.properties`) — overrides the extension's values. Currently has 4 keys. **Add these overrides:**
```
ssoHeader=Sign in with SSO
ssoInfo=Enter your organisation’s SSO name to continue.
ssoError=That SSO name doesn’t match. Check with your admin.
```

(`’` = right single quotation mark / apostrophe — `.properties` files should not contain raw curly quotes; use unicode escapes.)

**Do NOT add `ssoLabel`** — the current value `SSO name` already matches the UX spec.

The current theme `messages_en.properties` tail is:
```
accountTemporarilyDisabledMessage=Too many attempts. Your account is temporarily locked — check your email for reset instructions or try again in 15 minutes.
```
Append the 3 new keys after it. The file has no trailing newline — add one.

---

### Field Error Pattern (`wt-field__error`)

The SSO field error uses the FIELD-LEVEL error pattern (not the form-level `wt-login-error` from Story 2.2). This is the standard inline-error component (UX-DR3, UX-DR7):

From `components.css` (lines ~162–192), the `.wt-field__error` div:
```css
.wt-field__error {
  display: flex; align-items: center; gap: ...;
  color: var(--wt-danger-text); font-size: var(--wt-text-caption-size);
}
.wt-field--error .wt-field__input { border-color: var(--wt-danger); background-color: var(--wt-danger-bg); }
```

The markup pattern (from `components.css` usage convention):
```html
<div class="wt-field wt-field--error">
  <label class="wt-field__label" for="sso-id">SSO name</label>
  <input class="wt-field__input" id="sso-id" aria-invalid="true" aria-describedby="sso-error">
  <div class="wt-field__error" id="sso-error" role="alert" aria-live="polite">
    <!-- error message -->
  </div>
</div>
```

This is the SAME pattern used in `review-invitations.ftl` for per-field errors. Do NOT use `wt-login-error` (that's the form-level error component from Story 2.2, which shows below the form).

---

### wtA11y API Available in login-with-sso.ftl

`script.js` is loaded before the inline IIFE. The same `wtA11y` object is available:

| Function | Use in this story |
|---|---|
| `wtA11y.focusFirstError(form)` | On page load with `sso-id` field error — focuses the invalid input |

Guard: `if (typeof wtA11y !== 'undefined') { ... }`

---

### Current State of `.wt-methods` in login.ftl

From the current `login.ftl` (line 118–122):
```html
        <!-- Method rows — Stories 2.3 (SSO) and 2.4 (magic link) inject here -->
        <div class="wt-methods" id="auth-methods" aria-label="Other sign-in options">
          <!-- 2.3: <a class="wt-method-row" ...>Sign in with SSO</a> -->
          <!-- 2.4: <button class="wt-method-row" ...>Email me a sign-in link</button> -->
        </div>
```

**Replace the inner comments with the SSO button** (see Task 2 / "login.ftl Changes"). Keep the `<!-- 2.4: ... -->` comment so Story 2.4 can find its injection point.

The `aria-label="Other sign-in options"` on the `.wt-methods` div is already correct — preserve it.

---

### Patterns from Story 2.2 to Preserve in login.ftl

| Pattern | What it does | Action |
|---|---|---|
| `wt_last_auth_method` localStorage write | Written as `'password'` on form submit | ADD `'sso'` write in `loginWithSso()` |
| `bfcache pageshow` reset handler | Restores submit button on back-navigation | Already present — no change |
| `autocomplete="username webauthn"` on username field | Passkey enablement | No change |
| Error-focus IIFE (Story 2.2) | Focuses error region on load | No change — add SSO JS AFTER this block |
| `auth.showTryAnotherWay` pattern | Standard KC alternative switching | Used in `login-with-sso.ftl`, not login.ftl |
| `.wt-methods` empty slot for 2.4 | Story 2.4 injection point | Preserve `<!-- 2.4: ... -->` comment |

---

### Background Style in login-with-sso.ftl

The `login-with-sso.ftl` uses the same page chrome as `login.ftl`:
- `<body class="wt-login-page">` — same body class (mint `bg`, background-overlay)
- `<div class="background-overlay" aria-hidden="true">` — same decorative overlay
- `<main class="wt-login-layout">` — same centered layout
- `.wt-login-brand` with same brand mark + onerror fallback

All of these classes are defined in `style.css` (delivered in Epic 1). The `login-with-sso.ftl` screen must be visually indistinguishable from the login screen in terms of page chrome.

---

### FTL Context Variables Available in login-with-sso.ftl

These are guaranteed by the `LoginWithSsoAuthenticator` rendering context:

| Variable | Availability | Use |
|---|---|---|
| `url.loginAction` | Always | Form `action` attribute |
| `url.resourcesPath` | Always | CSS/JS/image paths |
| `msg("key")` | Always | i18n messages |
| `kcSanitize(...)` | Always | Safe HTML rendering |
| `messagesPerField` | Always when field errors exist | Check `messagesPerField.existsError('sso-id')` |
| `auth.showTryAnotherWay` | True when ALTERNATIVE exists | "Use password instead" form |
| `properties.*` | Always | Theme properties (not used in standalone) |

**NOT available** in `login-with-sso.ftl` (only in the standard KC login.ftl context):
- `authenticationSelection` — that's from the username-password-form context, not the SSO context
- `social` — social providers object
- `register` — registration URL

---

### What Is Explicitly Out of Scope

- "Email me a sign-in link" (`MagicLinkAuthenticator`) — Story 2.4
- Any Java/SPI changes — zero Java changes in this story
- Styling `login-reset-password.ftl` or `login-update-password.ftl` — not in Epic 2
- Email-domain-based IdP discovery — explicitly rejected in EXPERIENCE.md (no email-domain detection)
- Adding icons/logos to the SSO field — the field takes a typed alias, not a list of IdP buttons
- Modifying `LoginWithSsoAuthenticator.java` — the existing tracing and `ssoError` error handling are correct and in place from Story 1.6

### Deferred Items

These remain tracked in `_bmad-output/implementation-artifacts/deferred-work.md`:
- Bootstrap CDN on other legacy templates — Epics 4/5
- `axe-core` full scan — post-launch

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

- Task 1: Added 3 SSO i18n override keys (`ssoHeader`, `ssoInfo`, `ssoError`) to theme's `messages_en.properties`. Used literal ASCII apostrophes (not MessageFormat escapes) consistent with existing theme messages that have no `{n}` format args. Verified no duplicate keys.
- Task 2: Replaced `.wt-methods` placeholder comments in `login.ftl` with the SSO `<button>` + hidden `kc-select-sso-form`, guarded by `<#if authenticationSelection??>`. Added `loginWithSso()` function inside the existing IIFE after the Story 2.2 error-focus block; writes `wt_last_auth_method='sso'` to localStorage before submitting the execution-selection form. Preserved the 2.4 injection-point comment.
- Task 3: Created `login-with-sso.ftl` as a standalone HTML file (no `@layout.registrationLayout`). Implements: brand mark with onerror fallback, `wt-field--error` + `aria-invalid` + `aria-describedby` on sso-id field error, interstitial show/hide on submit + bfcache pageshow reset, `wtA11y.focusFirstError()` guard on page load with error, `auth.showTryAnotherWay` guard for "Use password instead" form. No `autocomplete` on sso-id field.
- Task 4: `mvn package` → BUILD SUCCESS (1.137s). Zero hardcoded hex colors in new/changed files.

### File List

- `src/main/resources/theme/azguards-whatsapp/login/messages/messages_en.properties` (azguards-keycloak-custom-theme) — Task 1
- `src/main/resources/theme/azguards-whatsapp/login/login.ftl` (azguards-keycloak-custom-theme) — Task 2
- `src/main/resources/theme/azguards-whatsapp/login/login-with-sso.ftl` (azguards-keycloak-custom-theme) — Task 3 (NEW)

## Change Log

- 2026-06-14: Story 2.3 created — "Sign in with SSO" secondary link. Theme-only story targeting azguards-keycloak-custom-theme. Covers login.ftl SSO method-row injection and new standalone login-with-sso.ftl with interstitial, field error, and try-another-way navigation.
- 2026-06-14: Story 2.3 implemented — all 4 tasks complete. Added SSO i18n overrides, injected SSO method-row in login.ftl (guarded by authenticationSelection), created standalone login-with-sso.ftl. mvn BUILD SUCCESS.

---

### Review Findings

_Code review 2026-06-14 (Blind Hunter + Edge Case Hunter + Acceptance Auditor). Files reviewed in `azguards-keycloak-custom-theme`. 7 patch (1 from a resolved decision), 1 defer, ~10 dismissed as noise/false-positive._

**Patch (fixable, unambiguous):**

- [x] [Review][Patch] `wt-login-subtitle` CSS class does not exist [login-with-sso.ftl:32] — `<p class="wt-login-subtitle">` is in none of tokens.css/components.css/style.css, and `login.ftl` never used a subtitle, so `ssoInfo` renders with default `<p>` styling. **Decision (2026-06-14): add `.wt-login-subtitle` to a CSS file** (overrides the story's "don't modify CSS" constraint by explicit reviewer call).

- [x] [Review][Patch] CRITICAL — SSO button is dead: `loginWithSso()` is unreachable from its inline `onclick` [login.ftl:125 vs :226]. The function is declared inside the top-level IIFE (opens :161, closes :236) so it is NOT on `window`; the inline `onclick="loginWithSso()"` resolves against global scope → `ReferenceError`, button does nothing. Breaks AC#1/#7 (the entire SSO entry path). Fix: attach the handler via `addEventListener` inside the IIFE (preferred), or expose `window.loginWithSso`.
- [x] [Review][Patch] Unescaped server value injected into JS string literal [login.ftl:231] — `ssoInput.value = '${authenticationSelection.authExecId!}';` interpolates a server value into a single-quoted JS literal with no escaping. A quote/backslash/newline breaks the script or enables injection. Fix: use `?js_string`, or set the value via the hidden input's FTL `value="..."` attribute instead of JS.
- [x] [Review][Patch] Empty `authExecId` posts an empty execution id [login.ftl:120 vs :231] — button render guard is `<#if authenticationSelection??>` but the value uses `authExecId!` (defaults to ""), so a present-but-empty `authExecId` yields a dead POST (`authenticationExecution=`). Fix: guard the button/form on `<#if authenticationSelection.authExecId??>` (or `?has_content`).
- [x] [Review][Patch] `novalidate` + early return without `preventDefault()` posts empty `sso-id` [login-with-sso.ftl:88-97] — form has `novalidate`; the submit handler does `if (!form.checkValidity()) { return; }` but never calls `preventDefault()`, so an empty required field still round-trips to the server with no client-side block/error. Fix: `e.preventDefault()` in the invalid branch (or drop `novalidate`).
- [x] [Review][Patch] No fallback focus when `wtA11y` is undefined on the SSO error page [login-with-sso.ftl:118-125] — if script.js fails to load, the error-focus path is a no-op and nothing focuses the invalid `#sso-id`. Fix: add an `else { document.getElementById('sso-id').focus(); }` fallback.
- [x] [Review][Patch] Interstitial `aria-live` region toggled from `display:none` may not announce to screen readers [login-with-sso.ftl:62] — a live region revealed from `display:none` is unreliably announced across AT. Fix: keep it perceivable (e.g. visually-hidden until active) or move the announced text into a region that is present on load.

**Deferred (low-priority robustness, tracked):**

- [x] [Review][Defer] No safety net if the SSO navigation stalls [login-with-sso.ftl:96-105] — on a valid submit the form is hidden and the button disabled; if `ssoForm.submit()` never completes (network stall) and it is not a bfcache restore, the user is trapped with a hidden form and no recovery control. Deferred — minor edge robustness; primary outcomes (redirect / error re-render) reset the DOM.

### Review Fixes Applied (2026-06-14)

All 7 patch findings fixed in `azguards-keycloak-custom-theme`; `mvn package` → BUILD SUCCESS, zero hardcoded hex.

- **login.ftl** — SSO click handler moved inside the IIFE and attached via `addEventListener` (was an unreachable global `loginWithSso()` called from inline `onclick` → fixes the dead button); execution id now set on the hidden input's FTL-escaped `value` attribute instead of interpolated into a JS string; method-row render guard tightened to `authenticationSelection?? && authenticationSelection.authExecId?has_content`.
- **login-with-sso.ftl** — `evt.preventDefault()` added to the invalid-submit branch (stops empty `sso-id` round-trip under `novalidate`); added an always-present visually-hidden `#ssoStatus` `role="status"` live region for reliable redirect announcement (visual interstitial now `aria-hidden`); added a direct-focus fallback for when `wtA11y` is undefined.
- **style.css** — added `.wt-login-subtitle` (token-based: `--wt-text-body-*`, `--wt-muted`) per reviewer decision.
