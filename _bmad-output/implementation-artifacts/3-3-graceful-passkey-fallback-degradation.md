---
baseline_commit_extension: ea9e584
baseline_commit_theme: d238a35
---

# Story 3.3: Graceful Passkey Fallback & Degradation

Status: done

## Story

As an Agent on a device without passkey support,
I want the password flow to remain seamless,
So that I never hit a dead-end.

## Acceptance Criteria

1. **Given** the device does not support WebAuthn (`!window.PublicKeyCredential`), or the page is loaded in an insecure context (`!window.isSecureContext`) **When** the login screen renders **Then** the passkey affordance button (`#passkey-affordance-btn`) is silently absent — no error state, no dead-end (FR-L-4, NFR-B-3)

2. **Given** a passkey attempt fails or is cancelled **When** the error is caught in the WebAuthn get-ceremony **Then** the system announces fallback via `aria-live="polite"` ("Try again or use your password") and redirects to `url.loginUrl` (fresh login/password flow) without posting an error back to KC — so no passkey error message is shown on the resulting password-flow page (FR-PK-5)

3. **Given** the `webauthn-authenticate.ftl` template loads on a browser without WebAuthn support **When** the IIFE runs **Then** it redirects immediately to `url.loginUrl` with no ceremony attempted

4. **Given** the `webauthn-register.ftl` template loads on a browser without WebAuthn support **When** the IIFE runs **Then** if `isAppInitiatedAction` is true it auto-skips via the cancel-aia form; otherwise the register button is disabled and labelled with "Your browser doesn't support passkeys" so the user is not silently stuck

## Tasks / Subtasks

---

### REPO 1: `keycloak-multi-tenancy`

> Minimal changes — i18n keys only. Complete before Repo 2 so the FTL can use `msg()` during build.

- [x] **Task 1: Add passkey fallback i18n keys to extension messages bundle** (AC: #2, #4)
  - [x] Open `src/main/resources/theme-resources/messages/messages_en.properties`
  - [x] Check for duplicates: `grep -n "passkeyFallback\|passkeyUnsupported" src/main/resources/theme-resources/messages/messages_en.properties` → must return 0 matches before adding
  - [x] Append the following block immediately after the `# Passkey authentication (Story 3.2)` block (after line 54 — `passkeyAffordanceBtn=...`):
    ```properties
    # Passkey fallback / degradation (Story 3.3)
    passkeyFallbackAnnounce=Try again or use your password
    passkeyUnsupportedBrowser=Your browser doesn't support passkeys
    ```
  - [x] Verify count: `grep -c "passkeyFallback\|passkeyUnsupported" src/main/resources/theme-resources/messages/messages_en.properties` → 2

- [x] **Task 2: Build and verify Repo 1** (AC: #1)
  - [x] `mvn package -DskipTests` → BUILD SUCCESS
  - [x] Confirm no Java files were created or modified — this story adds zero Java code to Repo 1
  - [x] Verify key count: `grep -c "passkeyFallback\|passkeyUnsupported" src/main/resources/theme-resources/messages/messages_en.properties` → 2

---

### REPO 2: `azguards-keycloak-custom-theme`

> Start only after Repo 1 tasks are complete. Test all three FTL changes together.

- [x] **Task 3: Update `login.ftl` — silently hide passkey affordance on unsupported browsers** (AC: #1)
  - [x] Open `src/main/resources/theme/azguards-whatsapp/login/login.ftl`
  - [x] Locate the Story 3.2 passkey IIFE section (currently around lines 284–292):
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
  - [x] Replace that block with the expanded version that adds the feature-detect **after** attaching the listener
  - [x] **VERIFY** the change doesn't affect the SSO or magic-link sections — only the `passkeyBtn`/`passkeyForm` variables are in scope
  - [x] **Do NOT** change the `autocomplete="username webauthn"` token on the username field — it enables browser Conditional UI independently of the affordance button

- [x] **Task 4: Update `webauthn-authenticate.ftl` — fallback to password on failure/cancel** (AC: #2, #3)
  - [x] Open `src/main/resources/theme/azguards-whatsapp/login/webauthn-authenticate.ftl`
  - [x] **Step A — Add the `loginUrl` FTL variable and feature-detect redirect at IIFE start.**
    
    Locate the opening of the `<script>` IIFE:
    ```javascript
    (function () {
      'use strict';

      var base64url = {
    ```
    Replace with:
    ```javascript
    (function () {
      'use strict';

      /* ── Story 3.3: Feature-detect — redirect to password flow if WebAuthn unavailable ──
       * If the browser does not expose window.PublicKeyCredential, or the page is in a
       * non-secure context (plain HTTP), the get ceremony will always fail synchronously.
       * Redirect immediately to the fresh login URL (password flow) rather than presenting
       * a broken ceremony screen. This also satisfies AC #3 (no dead-end on unsupported device). */
      var loginUrl = '${(url.loginUrl!"")?js_string?replace("</", "<\\/")}';
      if (!window.PublicKeyCredential || !window.isSecureContext) {
        if (loginUrl) { window.location.replace(loginUrl); }
        return;
      }

      var base64url = {
    ```
  - [x] **Step B — Change `.catch()` to announce then redirect instead of posting error to KC.**
    
    Locate the current `.catch()` handler in `startAuthentication()`:
    ```javascript
    .catch(function (err) {
      // NotAllowedError / AbortError = user dismissed — post error so KC
      // re-renders the retry state with the error message.
      document.getElementById('error').value = err ? err.toString() : 'error';
      document.getElementById('webauth').submit();
    });
    ```
    Replace with:
    ```javascript
    .catch(function (err) {
      /* Story 3.3: On any failure or cancellation, announce the fallback via
       * aria-live then redirect to the fresh login URL (password flow).
       * Do NOT post the error back to KC — AC #2 requires "no error for the
       * passkey step" visible on the resulting password page (FR-PK-5).
       *
       * Known error types and their meaning:
       *   NotAllowedError / AbortError — user dismissed the OS prompt (or timeout)
       *   InvalidStateError            — credential not registered on this device
       *   SecurityError               — rpId / origin mismatch
       *   NotSupportedError           — authenticator does not support this credential
       * All are handled identically: announce → redirect to password flow.
       * (Distinguishing cancel from timeout is not reliably possible client-side.) */
      var statusEl = document.getElementById('passkey-status');
      if (statusEl) { statusEl.textContent = '${msg("passkeyFallbackAnnounce")?js_string?replace("</", "<\\/")}'; }
      /* Brief pause so screen readers can start reading the polite announcement
       * before the page navigates away. 500 ms is below any animation threshold
       * and is unaffected by prefers-reduced-motion (this is a timeout, not an
       * animation). If loginUrl is empty (unexpected), fall back to form submit. */
      window.setTimeout(function () {
        if (loginUrl) {
          window.location.replace(loginUrl);
        } else {
          document.getElementById('error').value = err ? err.toString() : 'error';
          document.getElementById('webauth').submit();
        }
      }, 500);
    });
    ```
  - [x] **Step C — Update the `syncErr` catch to use the same announce → redirect pattern.**
    
    Locate the current `syncErr` catch:
    ```javascript
    } catch (syncErr) {
      document.getElementById('error').value = syncErr ? syncErr.toString() : 'error';
      document.getElementById('webauth').submit();
    }
    ```
    Replace with:
    ```javascript
    } catch (syncErr) {
      /* Story 3.3: sync throw (e.g. malformed publicKey options) — announce + redirect. */
      var statusEl = document.getElementById('passkey-status');
      if (statusEl) { statusEl.textContent = '${msg("passkeyFallbackAnnounce")?js_string?replace("</", "<\\/")}'; }
      window.setTimeout(function () {
        if (loginUrl) {
          window.location.replace(loginUrl);
        } else {
          document.getElementById('error').value = syncErr ? syncErr.toString() : 'error';
          document.getElementById('webauth').submit();
        }
      }, 500);
    }
    ```
  - [x] **VERIFY** the `publicKey = null; setupError = null;` path at the top of `startAuthentication()` also redirects rather than submitting. That path currently does:
    ```javascript
    if (!publicKey) {
      document.getElementById('error').value = setupError ? setupError.toString() : 'setup-error';
      document.getElementById('webauth').submit();
      return;
    }
    ```
    Replace with:
    ```javascript
    if (!publicKey) {
      /* Story 3.3: malformed server data — announce + redirect to password flow. */
      var statusEl = document.getElementById('passkey-status');
      if (statusEl) { statusEl.textContent = '${msg("passkeyFallbackAnnounce")?js_string?replace("</", "<\\/")}'; }
      window.setTimeout(function () {
        if (loginUrl) { window.location.replace(loginUrl); } else { document.getElementById('webauth').submit(); }
      }, 500);
      return;
    }
    ```
  - [x] **A11y check:** the `passkey-status` div already has `role="status" aria-live="polite"` — the redirect announce will fire correctly on all four error paths
  - [x] **No new HTML changes needed** — the "Use password instead" link (`#passkey-use-password-link`) already points to `url.loginUrl!''` and remains as the static visible fallback; it continues to work for sighted users and keyboard navigation before any JS runs

- [x] **Task 5: Update `webauthn-register.ftl` — feature-detect, auto-skip or disable on unsupported browser** (AC: #4) _(fixes deferred issue from Stories 3.1/3.2 review)_
  - [x] Open `src/main/resources/theme/azguards-whatsapp/login/webauthn-register.ftl`
  - [x] At the very top of the IIFE body, **before** `var base64url = {`, insert the feature-detect block:
    ```javascript
    /* ── Story 3.3: Feature-detect WebAuthn on the register page ──
     * If WebAuthn is unavailable (no window.PublicKeyCredential or non-secure context),
     * the current try/catch around navigator.credentials.create would catch the sync throw
     * and re-submit an error to KC — but KC then re-renders the same broken register page,
     * creating an infinite retry loop with no escape (except the skip button for AIA flows).
     * Detect early and handle gracefully:
     *   - AIA flow (isAppInitiatedAction=true): auto-submit the cancel-aia skip form.
     *   - Non-AIA required-action flow: disable the button and surface the reason. */
    if (!window.PublicKeyCredential || !window.isSecureContext) {
      var skipForm = document.getElementById('kc-webauthn-skip-form');
      if (skipForm) {
        skipForm.submit();
      } else {
        var registerBtn = document.getElementById('passkey-register-btn');
        if (registerBtn) {
          registerBtn.disabled = true;
          registerBtn.textContent = '${msg("passkeyUnsupportedBrowser")?js_string?replace("</", "<\\/")}';
        }
      }
      return;
    }

    var base64url = {
    ```
  - [x] **VERIFY** the `return` statement inside the IIFE exits the IIFE function cleanly — this is standard JS: `(function () { ... return; ... }())`. The `var registerBtn` declaration at line 214 (`var registerBtn = document.getElementById('passkey-register-btn'); if (registerBtn) { registerBtn.addEventListener('click', startRegistration); }`) is below the early return path and must NOT be declared inside the `if (!window.PublicKeyCredential...)` block — they must stay at the bottom where they currently are.
  - [x] **CRITICAL:** After adding the early-return block, verify the `var registerBtn = document.getElementById('passkey-register-btn'); if (registerBtn) { registerBtn.addEventListener('click', startRegistration); }` at the bottom is still present and reached only when WebAuthn IS available

- [x] **Task 6: Build and verify Repo 2** (AC: #1, #2, #3, #4)
  - [x] `mvn package` → BUILD SUCCESS
  - [x] `grep -rn "#[0-9a-fA-F]\{3,6\}" src/main/resources/theme/azguards-whatsapp/login/webauthn-authenticate.ftl` → 0 matches
  - [x] `grep -rn "#[0-9a-fA-F]\{3,6\}" src/main/resources/theme/azguards-whatsapp/login/webauthn-register.ftl` → 0 matches
  - [x] `grep -rn "#[0-9a-fA-F]\{3,6\}" src/main/resources/theme/azguards-whatsapp/login/login.ftl` → 0 matches (pre-existing check)
  - [x] `grep -rn "console\." src/main/resources/theme/azguards-whatsapp/login/webauthn-authenticate.ftl` → 0 matches
  - [x] `grep -rn "console\." src/main/resources/theme/azguards-whatsapp/login/webauthn-register.ftl` → 0 matches
  - [ ] Manual verification checklist (requires running KC 26.6.3 instance):

    **AC #1 — Silently absent on unsupported browser:**
    - In DevTools, temporarily override `window.PublicKeyCredential = undefined` on `login.ftl`
    - Reload — verify "Use your passkey" button is NOT visible; all other login elements render normally
    - Verify no error message or dead-end
    - Restore `PublicKeyCredential`, reload — button reappears (when `passkeyAuthExecId` is set)

    **AC #2 — Fallback on passkey failure:**
    - Log in as a user with a registered passkey; click "Use your passkey"
    - On `webauthn-authenticate.ftl`, cancel the OS passkey prompt (press Escape or "Cancel")
    - Verify: aria-live region announces "Try again or use your password" (check with screen reader or aria-live spy)
    - Verify: page redirects to the fresh login page (password form) — no "Passkey sign-in failed" error message visible on the login page
    - Verify: timing is ≥ 300ms before redirect (aria-live has time to announce)

    **AC #3 — Feature-detect redirect on webauthn-authenticate.ftl:**
    - Override `window.PublicKeyCredential = undefined` and navigate directly to the webauthn-authenticate URL
    - Verify: immediate redirect to the login page (password form), no ceremony attempted

    **AC #4 — webauthn-register.ftl AIA auto-skip:**
    - Trigger `webauthn-register.ftl` as an App-Initiated Action
    - Override `window.PublicKeyCredential = undefined`
    - Verify: page auto-submits the cancel-aia skip form without user interaction

---

### Review Findings

_Code review 2026-06-15 (bmad-code-review, 3 adversarial layers: Blind Hunter, Edge Case Hunter, Acceptance Auditor). Scope: both repos. Jenkinsfile change in theme repo flagged out-of-scope, not reviewed._

- [x] [Review][Decision→Accepted] Passkey affordance is rendered server-side then JS-hidden, not "silently absent" (AC #1) — _Resolved 2026-06-15: kept as-is per Asif. Server cannot detect WebAuthn (client-only); the brief flash affects only unsupported/insecure browsers while the supported majority sees none. AC met in spirit (no dead-end)._ `login.ftl:60-66` renders `#passkey-affordance-btn` whenever `passkeyAuthExecId` is set, with no FTL/CSS WebAuthn gate; `login.ftl:299-302` hides it via JS after paint. On unsupported/insecure browsers the button briefly flashes before JS hides it (FOUC). Strictly AC #1 says "silently absent." Server cannot detect WebAuthn (client-only), so JS is the only mechanism — but the flash could be eliminated by defaulting the button hidden and revealing it when supported. Tradeoff: that moves the flash onto the supported-browser majority (flash-in) instead of the unsupported minority (flash-out). Needs a human call.

- [x] [Review][Patch] Consolidate duplicate `var` declarations [webauthn-authenticate.ftl:135 / webauthn-register.ftl:93] — _Applied 2026-06-15: single `var statusEl` hoisted to top of `startAuthentication`, reused on all 4 paths; `registerBtn` declared once in the feature-detect block and reused at the bottom wiring. `mvn package` BUILD SUCCESS; hex/console gates still 0._ — `var statusEl` is declared 3× in one function scope; `var registerBtn` is declared twice (the spec Task 5 note even prohibited this, though its stated rationale is wrong — `var` redeclaration is legal under `'use strict'`). Zero runtime impact; cosmetic / `no-redeclare` lint hygiene.

- [x] [Review][Defer] `isSecureContext` false behind misconfigured TLS-terminating proxy [login.ftl:299 / webauthn-authenticate.ftl:84] — deferred, deployment/infra concern not caused by this change. If the browser origin is `https://` `isSecureContext` is true regardless of backend hop; only a misconfigured KC frontend-URL/proxy (serving `http://` origin) would wrongly force supported browsers to the password flow. Verify KC proxy/frontend-URL config in deployment.

- [x] [Review][Defer] Non-AIA required-action passkey registration on an unsupported browser is a dead-end [webauthn-register.ftl:88-99] — deferred, this is AC #4-defined behavior (disabled button + "unsupported" label) but leaves no forward path when no skip-form exists. Real product dead-end only if passkey registration is ever a hard required action on unsupported browsers; ties to deferred webAuthnPolicy items.

- [x] [Review][Defer] Diagnostic signal lost on genuine `SecurityError`/`InvalidStateError` [webauthn-authenticate.ftl:167-193] — deferred, by-design tension. AC #2 mandates not posting the error to KC and Task 6 bans `console.*`, so real rpId/origin-mismatch and credential-state errors now silently redirect with no server/console trail. Observability gap to revisit alongside the deferred empty-`webAuthnPolicyRpId` multi-hostname item.

**Dismissed as noise (8):** open-redirect via `url.loginUrl` (KC-generated trusted URL, `?js_string`-escaped; existing "Use password instead" link already uses it); missing null-guard on `passkeyBtn`/`passkeyForm` in `login.ftl` (hide code is inside the `if (passkeyBtn && passkeyForm)` guard); add `console.warn` for swallowed errors (directly violates AC #2 + Task 6 0-console gate); i18n keys en-only (explicitly out of scope, matches existing pattern); bfcache/back-button stale page (`location.replace` chosen by design for exactly this); partial WebAuthn impl where `PublicKeyCredential` present but `.get` absent (caught by existing `syncErr` try/catch); double-click racing ceremonies (`btn.disabled=true` set before ceremony; a rejected promise can't also resolve); comment-quality / magic-number-500 nits.

## Dev Notes

### Working Repositories

```
keycloak-multi-tenancy (Repo 1 — i18n only, complete first):
  src/main/resources/theme-resources/messages/
    messages_en.properties    ← MODIFY (Task 1 — add 2 passkey fallback/unsupported keys)

azguards-keycloak-custom-theme (Repo 2 — FTL, complete after Repo 1):
  src/main/resources/theme/azguards-whatsapp/login/
    login.ftl                 ← MODIFY (Task 3 — hide passkey affordance on unsupported browsers)
    webauthn-authenticate.ftl ← MODIFY (Task 4 — announce + redirect on fail/cancel; feature-detect redirect)
    webauthn-register.ftl     ← MODIFY (Task 5 — feature-detect early return; AIA auto-skip or button disable)
```

**Do NOT create or modify any Java files** in either repo — this story is entirely FTL + i18n resource changes.

**Do NOT modify** in Repo 2: `tokens.css`, `components.css`, `style.css`, any other FTL files.

---

### Deferred Items Being Resolved in This Story

This story directly owns three deferred items from prior code reviews:

1. **Deferred from Story 3.1 review (2026-06-15):** "No WebAuthn feature/secure-context detection (`webauthn-register.ftl:165`) — on a browser without WebAuthn or insecure context, `navigator.credentials` is undefined and `.create` throws synchronously, leaving the button disabled with no error." → Fixed by Task 5.

2. **Deferred from Story 3.1 review (2026-06-15):** "Unsupported-browser / insecure-context dead-end (`webauthn-register.ftl:134, 152-153`) — no `window.PublicKeyCredential` feature-detect; on an unsupported browser `navigator.credentials.create` throws a synchronous `TypeError` outside the promise chain, leaving the primary button disabled and the status region stuck on 'in progress…' with no fallback." → Fixed by Task 5.

3. **Deferred from Story 3.1 review (2026-06-15) + Story 3.2 scope notes:** "Graceful degradation when no passkey is registered or device doesn't support WebAuthn — Story 3.3." → Fixed by Tasks 3 and 4.

---

### Why Redirect to `url.loginUrl` (not post error to KC)

**Current behavior (Stories 3.1/3.2):** On passkey ceremony failure/cancel, the error field is posted to KC which re-renders `webauthn-authenticate.ftl` in the error/retry state showing "Passkey sign-in failed — try again or use your password." This is the KC-standard error path.

**Story 3.3 change:** On failure/cancel, announce via `aria-live` then redirect to `url.loginUrl` (fresh browser flow). This satisfies AC #2: "falls back to the password flow **without showing an error for the passkey step**" — the resulting `login.ftl` page shows no passkey-related error message.

**Key detail:** Redirecting to `url.loginUrl` starts a completely fresh KC browser flow. The user will need to re-enter their username. This is the same behavior as clicking the existing "Use password instead" link that was delivered in Story 3.2 — that link already uses `url.loginUrl!''`. Story 3.3 makes failure/cancel follow the same path automatically, rather than routing through the KC error-state re-render.

**Why not keep the retry state and just not show an error?** The AC says "falls back to the password flow" which implies routing to the password form (`login.ftl`), not staying on `webauthn-authenticate.ftl`. The KC error-state re-render is still `webauthn-authenticate.ftl` — not the password form.

**Fallback within `url.loginUrl` empty guard:** If `url.loginUrl` resolves to an empty string (unexpected; the variable is a standard KC URL), the code falls back to submitting the error form to KC — preserving the prior behavior rather than silently swallowing the error.

---

### `window.PublicKeyCredential` Feature Flag

`window.PublicKeyCredential` is the primary WebAuthn API availability check:
- `undefined` / falsy on: Internet Explorer, Android WebView < Chrome 67, Opera Mini, any browser without WebAuthn support, or any page that has the WebAuthn API disabled via Feature Policy/Permissions Policy.
- Defined but may still fail for secure-context reasons — hence the additional `window.isSecureContext` check.

`window.isSecureContext`:
- `false` on plain-HTTP origins (non-localhost). WebAuthn requires a secure context; passkeys will always fail with a `SecurityError` or be entirely unavailable on `http://`.

Both checks together (`window.PublicKeyCredential && window.isSecureContext`) are the minimal sufficient feature gate. No other feature flags are needed for this story.

**Do NOT use `try { navigator.credentials.create({}) } catch {}` as the feature-detect** — this is an async API and the feature-detect would have to be async. The synchronous `window.PublicKeyCredential` check is the correct approach (MDN and WebAuthn spec).

---

### `passkeyFallbackAnnounce` aria-live Timing Rationale

The 500 ms `setTimeout` before the `location.replace()` redirect:
- Gives screen readers and aria-live polite queues time to start processing the announcement before navigation
- 500 ms is below any user-perceptible delay threshold and is not an animation (unaffected by `prefers-reduced-motion`)
- `window.location.replace()` (not `.href =`) replaces the history entry — so Back navigation goes to `login.ftl` (where the user came from), not back to `webauthn-authenticate.ftl` in a failed state
- If the browser supports the [Navigation API Transition](https://developer.chrome.com/docs/web-platform/navigation-api) or bfcache, the redirect will interrupt any running ceremony cleanly since the ceremony already failed before the redirect fires

---

### Standalone FTL Pattern Preserved

All three FTL files use standalone HTML (NOT `@layout.registrationLayout`). This story does not change the outer HTML structure of any file — only the IIFE JavaScript blocks are modified.

---

### IIFE JavaScript Pattern (Mandatory — Epic 2 Retro Lesson P2)

All JS changes stay inside the existing IIFE in each file. No new global functions. No inline `onclick` attributes. The `return` in `webauthn-register.ftl` exits the IIFE (not a `for` loop or nested function) — this is valid and the intended pattern for early exit.

---

### Test Strategy

No new unit tests needed (i18n + FTL-only changes with no new Java code).

**Manual verification (required before done):**
1. All four scenarios in Task 6 manual checklist
2. **Regression gate:** login with password (no passkey) — verify the passkey affordance hides correctly, the login form still submits, and the SSO/magic-link method rows still work
3. **Regression gate:** login with passkey (happy path from Story 3.2) — verify clicking "Use your passkey" still routes to `webauthn-authenticate.ftl` and completes successfully (the redirect on error path must NOT fire on success)

**Build gates:** `mvn package -DskipTests` (Repo 1) and `mvn package` (Repo 2) → BUILD SUCCESS.

**Regression gate (Repo 1):** All 39 existing integration tests in `mvn verify` — pre-existing Docker constraint means these fail locally; confirm via CI.

---

### CSS Classes Available (Unchanged from Story 3.2)

No new CSS classes needed for this story. All changes are behavioral (JS) + i18n.

Available from `components.css` (verified prior stories):
- `.wt-btn`, `.wt-btn--primary`, `.wt-btn--ghost` — buttons
- `.wt-card`, `.wt-login-layout`, `.wt-login-page` — layout chrome

---

### What Is Explicitly Out of Scope for Story 3.3

- **Post-login enrollment prompt** — Story 3.4
- **Any modification to `register.ftl`, `login-oauth-grant.ftl`, `email/` templates, admin tenant switcher** — AR-OOS
- **Passwordless WebAuthn flow** (`webauthn-authenticator-passwordless`) — not in scope for this epic
- **i18n for non-English locales** — broader i18n pass (existing pattern)
- **SRI / self-hosting the brand logo** — pre-existing deferred item from Story 3.1 review (cross-project concern)
- **Empty `webAuthnPolicyRpId` multi-hostname passkey validation issue** — deferred from Story 3.2 review; depends on deployment hostname strategy finalization
- **`authenticatorLabel` passkey-naming UI in webauthn-register.ftl** — deferred UX enhancement from Story 3.1 review

---

### References

- Epics file: `_bmad-output/planning-artifacts/epics.md` — Epic 3, Story 3.3 (FR-L-4, FR-PK-5, NFR-B-3)
- Architecture: `_bmad-output/planning-artifacts/architecture.md` — WebAuthn SPI, graceful degradation
- UX design: EXPERIENCE.md — UX-DR16 (passkey unavailable→password fallback)
- Previous story: `_bmad-output/implementation-artifacts/3-2-passkey-first-authentication-on-login.md`
  - Standalone FTL pattern, IIFE JS pattern, `url.loginUrl!''` redirect, `passkeyAuthExecId` theme property
  - Dev Notes: "Use password instead" link uses `url.loginUrl!''` — confirmed working mechanism for redirect
- Deferred work: `_bmad-output/implementation-artifacts/deferred-work.md`
  - Stories 3.1 + 3.2 deferrals — all WebAuthn feature-detect issues are owned by this story
- Sprint status: `_bmad-output/implementation-artifacts/sprint-status.yaml`
- `login.ftl` (current): `azguards-keycloak-custom-theme/.../login/login.ftl` — 297 lines; passkey section at ~lines 284–292
- `webauthn-authenticate.ftl` (current): `azguards-keycloak-custom-theme/.../login/webauthn-authenticate.ftl` — 175 lines; IIFE at lines 75–171
- `webauthn-register.ftl` (current): `azguards-keycloak-custom-theme/.../login/webauthn-register.ftl` — 224 lines; IIFE at lines 77–220
- MDN WebAuthn feature detection: `window.PublicKeyCredential` is the canonical check (MDN + WebAuthn Level 3 spec)
- KC 26.6.3 `webauthn-authenticate.ftl` source: `https://github.com/keycloak/keycloak/blob/26.6.3/themes/src/main/resources/theme/base/login/webauthn-authenticate.ftl`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

None — all tasks completed without errors.

### Completion Notes List

- **Task 1 (Repo 1 i18n):** Added `passkeyFallbackAnnounce` and `passkeyUnsupportedBrowser` keys after `passkeyAffordanceBtn` in `messages_en.properties`. Duplicate check passed (0 matches before add, 2 matches after).
- **Task 2 (Repo 1 build):** `mvn package -DskipTests` → BUILD SUCCESS in 8.5 s. Zero Java files modified.
- **Task 3 (login.ftl):** Added Story 3.3 feature-detect block inside the existing `if (passkeyBtn && passkeyForm)` guard — hides both button and form via `style.display='none'` when `!window.PublicKeyCredential || !window.isSecureContext`. SSO and magic-link sections are unaffected (different variable scope).
- **Task 4 (webauthn-authenticate.ftl):** Four error paths all updated to announce→redirect pattern:
  1. Feature-detect at IIFE top: `loginUrl` var captured, immediate `window.location.replace(loginUrl)` if WebAuthn unavailable (AC #3).
  2. `!publicKey` path: announce + 500 ms setTimeout → redirect (was: post error to KC).
  3. `.catch()` handler: announce + 500 ms setTimeout → redirect (AC #2) (was: post error to KC).
  4. `syncErr` catch: same announce+redirect pattern.
  All four paths fall back to form submit if `loginUrl` is unexpectedly empty.
- **Task 5 (webauthn-register.ftl):** Feature-detect block inserted at very top of IIFE before `var base64url`. AIA path: auto-submits `kc-webauthn-skip-form`. Non-AIA path: disables register button and sets text to `passkeyUnsupportedBrowser`. The `var registerBtn … addEventListener` at the bottom of the IIFE (line 236) is correctly below the `return` and only reached when WebAuthn IS available (verified with grep).
- **Task 6 (Repo 2 build + automated checks):** `mvn package` → BUILD SUCCESS in 1.5 s. All grep checks pass (0 hex colors, 0 console.* calls in all three FTL files).
- **Manual verification (Task 6 checklist):** Requires running KC 26.6.3 instance — deferred to reviewer QA. All four AC scenarios (AC #1–#4) are covered by the code changes and are ready for manual validation per the Task 6 checklist.

### File List

**keycloak-multi-tenancy (Repo 1):**
- `src/main/resources/theme-resources/messages/messages_en.properties` (MODIFY — Task 1: add 2 fallback/unsupported keys)

**azguards-keycloak-custom-theme (Repo 2):**
- `src/main/resources/theme/azguards-whatsapp/login/login.ftl` (MODIFY — Task 3: feature-detect, hide passkey affordance)
- `src/main/resources/theme/azguards-whatsapp/login/webauthn-authenticate.ftl` (MODIFY — Task 4: feature-detect redirect + announce→redirect on fail/cancel)
- `src/main/resources/theme/azguards-whatsapp/login/webauthn-register.ftl` (MODIFY — Task 5: feature-detect early-return; AIA auto-skip; button disable)

### Change Log

- 2026-06-15: Story 3.3 implemented — graceful passkey fallback & degradation. Added 2 i18n keys (Repo 1). Updated login.ftl, webauthn-authenticate.ftl, webauthn-register.ftl with WebAuthn feature-detect guards and announce→redirect fallback pattern (Repo 2). Both repos BUILD SUCCESS.
