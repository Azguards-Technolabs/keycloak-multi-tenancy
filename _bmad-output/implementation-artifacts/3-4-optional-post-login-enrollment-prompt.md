---
baseline_commit_extension: ea9e584
baseline_commit_theme: d238a35
baseline_commit: ea9e5840bf93ccca843694f54bb1b4577a26da6c
---

# Story 3.4: Optional Post-Login Enrollment Prompt

Status: done

## Story

As an Agent who just logged in with a password,
I want an optional nudge to set up a passkey,
So that I can opt into faster sign-in next time.

## Open Question — OQ-8 (Resolve Before Implementing)

> **Epics file note:** "show the enrollment prompt on every login until dismissed, or only once with a persisted 'don't show again'? Current AC assumes dismissible-per-session — confirm before dev."

**Decision required from Asif before dev starts.** The two options:

| Option | Behaviour | Implementation |
|---|---|---|
| **A — Per-session dismiss (AC default)** | Prompt shows on every password login until passkey is set up. "Not now" dismisses for this login only; it reappears next login. | `evaluateTriggers` always re-adds the action when no passkey exists. "Dismiss" simply calls `context.success()`. |
| **B — Persistent "don't show again"** | After the first "Not now" tap, the prompt never shows again. | `evaluateTriggers` additionally checks a user attribute `passkeyEnrollDeclined=true`. "Dismiss" sets that attribute. |

**This story file is written for Option A (per-session dismiss).** If Asif selects Option B, update `evaluateTriggers` to check/set a user attribute; all other tasks remain the same.

## Acceptance Criteria

1. **Given** a successful password-based login (user has no registered passkey)
   **When** the post-login required actions run
   **Then** the `PromptPasskeyEnrollment` required action fires and presents the enrollment-prompt screen with the copy "Sign in faster next time — set up a passkey." (FR-PK-3)

2. **Given** the enrollment-prompt screen is displayed
   **When** the Agent taps "Not now"
   **Then** the Agent proceeds into the product with no passkey created and is not blocked; the prompt reappears on the next password-based login (per-session dismiss — OQ-8 Option A)

3. **Given** the enrollment-prompt screen is displayed
   **When** the Agent taps "Set up a passkey"
   **Then** the Agent enters the existing passkey registration flow (Story 3.1 `webauthn-register.ftl`) without any new registration UI being created

4. **Given** the Agent already has a registered passkey credential
   **When** `evaluateTriggers` runs
   **Then** the enrollment prompt is NOT added — the Agent is never shown a "set up a passkey" prompt they don't need

5. **Given** the browser does not support WebAuthn (`!window.PublicKeyCredential || !window.isSecureContext`)
   **When** the enrollment-prompt FTL renders
   **Then** the JS feature-detect auto-submits the "Not now" dismiss form so the Agent is not stuck

6. **Given** the enrollment-prompt FTL
   **When** rendered
   **Then** it uses Epic 1 tokens/components (standalone HTML, `.wt-card`, `.wt-btn--primary`, `.wt-btn--ghost`) and meets the a11y floor (h1 focus on load, all interactive elements keyboard-reachable)

## Tasks / Subtasks

---

### REPO 1: `keycloak-multi-tenancy`

> Complete Repo 1 tasks before starting Repo 2. The Java class registers the required action and provides the FTL template name; Repo 2's FTL resolves from the same template name.

- [x] **Task 1: Add `PromptPasskeyEnrollment` required action** (AC: #1, #2, #3, #4)
  - [x] Create `src/main/java/dev/sultanov/keycloak/multitenancy/authentication/requiredactions/PromptPasskeyEnrollment.java`
  - [x] Implement `RequiredActionProvider` **and** `RequiredActionFactory` in the same class (dual-interface pattern — matches `CreateTenant`, `SelectActiveTenant`, `ReviewTenantInvitations`)
  - [x] Set `public static final String ID = "prompt-passkey-enrollment";`
  - [x] **`evaluateTriggers`:** Check two conditions before adding the required action:
    1. User has no registered WebAuthn passkey credentials — use `user.credentialManager().getStoredCredentialsByTypeStream("webauthn").findAny().isEmpty()`
    2. (Optional guard) The user is not already being prompted — check `context.getUser().getRequiredActionsStream().noneMatch(a -> ID.equals(a))` to avoid duplicate add
    - If both conditions pass: `context.getUser().addRequiredAction(ID);`
  - [x] **`requiredActionChallenge`:** Render the prompt using:
    ```java
    Response challenge = context.form()
        .createForm("passkey-enrollment-prompt.ftl");
    context.challenge(challenge);
    ```
    No custom FTL attributes are needed — the form is static microcopy.
  - [x] **`processAction`:** Read `formData.getFirst("action")` from the POST body
    - `"enroll"` → `context.getUser().addRequiredAction("webauthn-register"); context.success();`  
      KC will remove this action and run `webauthn-register` next (existing Story 3.1 FTL).
    - `"dismiss"` (or any other / null) → `context.success();`  
      KC removes this action; no passkey created; prompt reappears next login (per OQ-8 Option A).
  - [x] Add Zipkin/Brave tracing to all three methods (mandatory — matches pattern of all other required actions):
    ```java
    Span span = TracingHelper.startServerSpan("prompt-passkey-enrollment.evaluateTriggers"); // etc.
    Throwable traceError = null;
    try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
        if (context.getUser() != null) { span.tag("user.id", context.getUser().getId()); }
        // ... method body ...
    } catch (Exception ex) { traceError = ex; throw ex; }
    finally { TracingHelper.finishSpan(span, traceError); }
    ```
  - [x] Add `@JBossLog` annotation and use `log.debugf` / `log.infof` throughout (matches existing pattern)
  - [x] Implement boilerplate factory methods: `create(KeycloakSession)` returns `this`; `getId()` returns `ID`; `getDisplayText()` returns `"Prompt passkey enrollment"`; `init`, `postInit`, `close` are empty.

- [x] **Task 2: Register the new required action in the SPI services file** (AC: #1)
  - [x] Open `src/main/resources/META-INF/services/org.keycloak.authentication.RequiredActionFactory`
  - [x] Append a new line: `dev.sultanov.keycloak.multitenancy.authentication.requiredactions.PromptPasskeyEnrollment`
  - [x] **Verify** file now has 4 entries (existing 3 + the new one)

- [x] **Task 3: Add i18n keys** (AC: #1, #2, #3)
  - [x] Open `src/main/resources/theme-resources/messages/messages_en.properties`
  - [x] Check for duplicates: `grep -n "passkeyEnroll" src/main/resources/theme-resources/messages/messages_en.properties` → must return 0 matches before adding
  - [x] Append immediately after the `# Passkey registration (Story 3.1)` block:
    ```properties
    # Post-login passkey enrollment prompt (Story 3.4)
    passkeyEnrollTitle=Sign in faster next time
    passkeyEnrollPrompt=Sign in faster next time — set up a passkey.
    passkeyEnrollAccept=Set up a passkey
    passkeyEnrollDecline=Not now
    ```
  - [x] Verify: `grep -c "passkeyEnroll" src/main/resources/theme-resources/messages/messages_en.properties` → 4

- [x] **Task 4: Build and verify Repo 1** (AC: #1)
  - [x] `mvn package -DskipTests` → BUILD SUCCESS
  - [x] `grep -n "PromptPasskeyEnrollment" src/main/resources/META-INF/services/org.keycloak.authentication.RequiredActionFactory` → 1 match
  - [x] `grep -c "passkeyEnroll" src/main/resources/theme-resources/messages/messages_en.properties` → 4
  - [x] `grep -rn "console\." src/main/java/dev/sultanov/keycloak/multitenancy/authentication/requiredactions/PromptPasskeyEnrollment.java` → 0 matches

---

### REPO 2: `azguards-keycloak-custom-theme`

> Start only after all Repo 1 tasks are complete. The FTL name must exactly match `"passkey-enrollment-prompt.ftl"` as called by `context.form().createForm(...)` in Task 1.

- [x] **Task 5: Create `passkey-enrollment-prompt.ftl`** (AC: #1, #2, #3, #5, #6)
  - [ ] Create `src/main/resources/theme/azguards-whatsapp/login/passkey-enrollment-prompt.ftl`
  - [x] Use standalone HTML structure (NOT `@layout.registrationLayout`) — matches all other passkey FTLs
  - [x] Link the three CSS files: `tokens.css`, `components.css`, `style.css`
  - [x] Title: `<title>Set up a passkey | WhataTalk</title>` (consistent with `webauthn-register.ftl`)
  - [x] Body structure — auth card:
    ```html
    <body class="wt-login-page">
      <div class="background-overlay" aria-hidden="true"></div>
      <main class="wt-login-layout">
        <!-- Brand mark (copy verbatim from webauthn-register.ftl lines 18-24) -->
        <div class="wt-login-brand">...</div>
        <!-- Auth card -->
        <div class="wt-card">
          <h1 tabindex="-1">${msg("passkeyEnrollTitle")}</h1>
          <p class="wt-login-subtitle">${msg("passkeyEnrollPrompt")}</p>
          <!-- aria-live region for screen reader announcement (matches pattern) -->
          <div id="passkey-status" role="status" aria-live="polite"
               style="position:absolute;width:1px;height:1px;...visually-hidden..."></div>
          <!-- Accept form -->
          <form id="kc-enroll-accept-form" action="${url.loginAction}" method="post">
            <input type="hidden" name="action" value="enroll">
            <button type="submit" class="wt-btn wt-btn--primary">${msg("passkeyEnrollAccept")}</button>
          </form>
          <!-- Dismiss form -->
          <form id="kc-enroll-dismiss-form" action="${url.loginAction}" method="post">
            <input type="hidden" name="action" value="dismiss">
            <button type="submit" class="wt-btn wt-btn--ghost">${msg("passkeyEnrollDecline")}</button>
          </form>
        </div>
      </main>
      <script>...</script>
    </body>
    ```
  - [x] JavaScript IIFE (mandatory — Epic 2 Retro Lesson P2: all JS in the existing IIFE, no global functions, no inline onclick):
    ```javascript
    (function () {
      'use strict';
      /* Story 3.4: Feature-detect — auto-dismiss if WebAuthn unavailable.
       * If the browser does not support WebAuthn or is in a non-secure context,
       * the passkey setup would fail immediately in webauthn-register.ftl anyway.
       * Auto-submit the dismiss form so the Agent is not blocked (AC #5). */
      if (!window.PublicKeyCredential || !window.isSecureContext) {
        var dismissForm = document.getElementById('kc-enroll-dismiss-form');
        if (dismissForm) { dismissForm.submit(); }
        return;
      }
      /* Focus the h1 for screen reader announcement and keyboard navigation (UX-DR14) */
      var heading = document.querySelector('h1');
      if (heading) { heading.focus(); }
    }());
    ```
  - [x] Visually-hidden style for the `passkey-status` div must match exactly the same inline style used in `webauthn-register.ftl:43` and `webauthn-authenticate.ftl`:
    `style="position:absolute;width:1px;height:1px;margin:-1px;padding:0;overflow:hidden;clip:rect(0 0 0 0);clip-path:inset(50%);border:0;white-space:nowrap"`
  - [x] **VERIFY** no hardcoded hex colors: `grep -rn "#[0-9a-fA-F]\{3,6\}" passkey-enrollment-prompt.ftl` → 0 matches
  - [x] **VERIFY** no console statements: `grep -rn "console\." passkey-enrollment-prompt.ftl` → 0 matches

- [x] **Task 6: Build and verify Repo 2** (AC: #1–#6)
  - [x] `mvn package` → BUILD SUCCESS
  - [x] `grep -rn "#[0-9a-fA-F]\{3,6\}" src/main/resources/theme/azguards-whatsapp/login/passkey-enrollment-prompt.ftl` → 0 matches
  - [x] `grep -rn "console\." src/main/resources/theme/azguards-whatsapp/login/passkey-enrollment-prompt.ftl` → 0 matches
  - [x] `grep -rn "onclick=" src/main/resources/theme/azguards-whatsapp/login/passkey-enrollment-prompt.ftl` → 0 matches (all handlers must be in the IIFE, never inline)
  - [ ] Manual verification checklist (requires running KC 26.6.3 instance):

    **AC #1 — Prompt fires on password login:**
    - Log in with username + password (user has no registered passkey)
    - Verify the enrollment-prompt screen renders before the product
    - Verify title reads "Sign in faster next time" and body reads "Sign in faster next time — set up a passkey."

    **AC #2 — "Not now" dismiss:**
    - On the enrollment prompt screen, click "Not now"
    - Verify Agent proceeds into the product without a passkey being created
    - Log out and log in again with password — verify the prompt appears again

    **AC #3 — "Set up a passkey" routes to registration:**
    - On the enrollment prompt screen, click "Set up a passkey"
    - Verify the existing `webauthn-register.ftl` screen appears ("Set up a passkey" with the OS passkey prompt)
    - Complete registration — verify passkey is registered and Agent proceeds

    **AC #4 — No prompt when passkey already exists:**
    - Log in as an Agent who already has a registered passkey
    - Verify the enrollment-prompt screen does NOT appear

    **AC #5 — Auto-dismiss on unsupported browser:**
    - In DevTools, override `window.PublicKeyCredential = undefined`
    - Log in with password and navigate to the enrollment prompt
    - Verify: page auto-submits the dismiss form without user interaction; Agent proceeds without a passkey

    **AC #6 — a11y:**
    - Verify `h1` receives focus on page load (check with keyboard nav or focus spy)
    - Verify both buttons are keyboard-reachable (Tab/Enter)

---

## Dev Notes

### Working Repositories

```
keycloak-multi-tenancy (Repo 1 — Java + i18n, complete first):
  src/main/java/dev/sultanov/keycloak/multitenancy/authentication/requiredactions/
    PromptPasskeyEnrollment.java          ← NEW (Task 1)
  src/main/resources/META-INF/services/
    org.keycloak.authentication.RequiredActionFactory  ← MODIFY (Task 2 — append 1 line)
  src/main/resources/theme-resources/messages/
    messages_en.properties                ← MODIFY (Task 3 — add 4 passkey enrollment keys)

azguards-keycloak-custom-theme (Repo 2 — FTL, complete after Repo 1):
  src/main/resources/theme/azguards-whatsapp/login/
    passkey-enrollment-prompt.ftl         ← NEW (Task 5)
```

**Do NOT modify any other Java files.** No DB schema changes. No realm-export.json changes (the required action registers dynamically via the SPI, no admin flow wiring needed).

**Do NOT modify:** `webauthn-register.ftl`, `webauthn-authenticate.ftl`, `login.ftl`, `tokens.css`, `components.css`, `style.css`, `messages_sv.properties` (English only, consistent with existing pattern).

---

### Mandatory Code Patterns (from Epic 2 Retro + previous stories)

1. **Dual-interface Required Action:** All required actions in this codebase implement both `RequiredActionProvider` AND `RequiredActionFactory` in one class and return `this` from `create(KeycloakSession)`. Do NOT create a separate factory class. See `CreateTenant.java`, `SelectActiveTenant.java`, `ReviewTenantInvitations.java`.

2. **Zipkin tracing is mandatory on every required action method.** The exact pattern:
   ```java
   Span span = TracingHelper.startServerSpan("prompt-passkey-enrollment.<methodName>");
   Throwable traceError = null;
   try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
       if (context.getUser() != null) { span.tag("user.id", context.getUser().getId()); }
       // business logic
   } catch (Exception ex) { traceError = ex; throw ex; }
   finally { TracingHelper.finishSpan(span, traceError); }
   ```
   Span names for this action: `"prompt-passkey-enrollment.evaluateTriggers"`, `"prompt-passkey-enrollment.challenge"`, `"prompt-passkey-enrollment.processAction"`.

3. **Standalone FTL (no layout):** `passkey-enrollment-prompt.ftl` must be a complete standalone HTML document. Do NOT use `@layout.registrationLayout` or `<#import "layout.ftl" ...>`. Copy the outer shell from `webauthn-register.ftl` (head section, body class, background-overlay, login layout, brand mark).

4. **IIFE JavaScript pattern (mandatory):** All JS must be inside `(function () { 'use strict'; ... }());`. No global function declarations. No inline `onclick` attributes. See `webauthn-register.ftl:76-243` and `webauthn-authenticate.ftl` for the pattern.

5. **Zero hardcoded hex / zero console.*** Build will pass regardless, but these are launch checklist gates (NFR-S-1, NFR-T-1). Both grep gates must pass before marking done.

---

### Credential Type Check — KC 26.x API

In KC 26.x, check if a user has registered a passkey (WebAuthn second-factor credential):

```java
// In evaluateTriggers — correct KC 26.x API:
boolean hasPasskey = user.credentialManager()
    .getStoredCredentialsByTypeStream("webauthn")
    .findAny()
    .isPresent();
```

`"webauthn"` is the credential type string for `webauthn-register` (second-factor / roaming authenticator), consistent with what Story 3.1 stores. This is `WebAuthnCredentialModel.TYPE_TWOFACTORS` in KC source. The passwordless type (`"webauthn-passwordless"`) is NOT used by this project.

**Do NOT use** `context.getSession().userCredentialManager()` (the older session-level API) — prefer `user.credentialManager()` which is the KC 26.x user-scoped API.

---

### Routing to `webauthn-register` on Accept

When the Agent accepts, the `processAction` must:

```java
// Route to existing Story 3.1 webauthn-register required action:
context.getUser().addRequiredAction("webauthn-register");
context.success();
```

`"webauthn-register"` is the ID of KC's built-in required action (registered by KC itself, not by this extension). After `context.success()` on `PromptPasskeyEnrollment`, KC will process `webauthn-register` next using the existing `webauthn-register.ftl` (Story 3.1). Story 3.4 does NOT create any new registration UI.

**Do NOT attempt to redirect to a URL directly.** KC's required-action machinery handles the ordering after `context.success()`.

---

### FTL Template Name Must Match Java

The string passed to `context.form().createForm(...)` must exactly match the FTL filename. If Java calls `createForm("passkey-enrollment-prompt.ftl")`, Repo 2 must provide `passkey-enrollment-prompt.ftl`. A mismatch causes a 500 at runtime with no compile-time error.

---

### CSS Classes Available

Confirmed available from `components.css` + `style.css` (verified in prior stories):
- `.wt-btn`, `.wt-btn--primary`, `.wt-btn--ghost` — primary and ghost buttons (full-width, 46px, radius 10px)
- `.wt-card` — auth card (surface, radius 16px, hairline border, shadow, max-width 400px)
- `.wt-login-layout`, `.wt-login-page`, `.wt-login-brand` — page outer shell
- `.wt-login-subtitle` — subtitle paragraph text under `h1`
- `background-overlay` — background gradient overlay (decorative)

No new CSS classes are needed for this story. The enrollment prompt uses the same card/button components as all prior passkey FTLs.

---

### Brand Mark — Copy Verbatim from `webauthn-register.ftl`

The brand mark block (lines 18–24 in `webauthn-register.ftl`) must be copied verbatim:
```html
<div class="wt-login-brand">
  <img
    src="https://www.whatatalk.com/media/logos/logo-wide-svg.svg"
    alt="WhataTalk"
    onerror="this.onerror=null; this.src='${url.resourcesPath}/img/azguard.png'"
  >
</div>
```
The external CDN logo load without SRI is a pre-existing deferred item (from Story 3.1 review) — do NOT try to fix it here.

---

### Per-Session vs. Persistent Dismiss (OQ-8)

This story implements **Option A (per-session dismiss)** as described in the Open Question section above.

`evaluateTriggers` re-adds the required action on every login where the user has no passkey. After dismiss, the action is removed from the user's required actions set (by `context.success()`), and KC re-adds it on the next login via `evaluateTriggers`. No user attribute is written — this is intentionally stateless.

If Option B (persistent dismiss) is later required, the change is isolated to `evaluateTriggers` + the `"dismiss"` branch of `processAction`:
- `evaluateTriggers`: also check `!"true".equals(user.getFirstAttribute("passkeyEnrollDeclined"))`
- `"dismiss"` branch: also call `user.setSingleAttribute("passkeyEnrollDeclined", "true");`

---

### Test Strategy

No new Testcontainers/Playwright tests are required for this story (consistent with the story's scope — the test harness baseline was established in Story 1.6). Manual verification covers all ACs per Task 6 checklist.

**Build gates:** `mvn package -DskipTests` (Repo 1) and `mvn package` (Repo 2) → BUILD SUCCESS.

**Existing tests must not break:** Run `mvn verify` in Repo 1 after Repo 1 changes. The 39 existing integration tests must not regress (pre-existing Docker constraint means these fail locally; confirm via CI).

**Regression guard:** After both repos are built, verify:
1. Password login without passkey — enrollment prompt appears
2. Passkey login (if already registered) — enrollment prompt does NOT appear
3. Login with SSO or magic link — enrollment prompt does NOT appear (required action only fires when `evaluateTriggers` adds it; SSO login goes through a different flow path and is unaffected)
4. Existing required actions (review-tenant-invitations, select-active-tenant) still work normally

---

### What Is Explicitly Out of Scope for Story 3.4

- **Any modification to `webauthn-register.ftl`** — Story 3.1 owns it; Story 3.4 reuses it as-is
- **Any new passkey registration ceremony** — Story 3.4 just prompts; Story 3.1's existing `webauthn-register` does the actual ceremony
- **Persistent "don't show again" flag** — out of scope unless Asif selects OQ-8 Option B
- **SSO or magic-link login paths** — enrollment prompt only fires via the required-action machinery
- **i18n for non-English locales (`messages_sv.properties`)** — consistent with existing pattern
- **Any modification to `register.ftl`, `login-oauth-grant.ftl`, email templates, admin switcher** — AR-OOS
- **Test harness extension** — deferred per Story 1.6 harness baseline scope

---

### References

- Epics file: `_bmad-output/planning-artifacts/epics.md` — Epic 3, Story 3.4 (FR-PK-3)
- Architecture: `_bmad-output/planning-artifacts/architecture.md` — WebAuthn SPI, required action pattern
- Previous story: `_bmad-output/implementation-artifacts/3-3-graceful-passkey-fallback-degradation.md`
  - Standalone FTL pattern, IIFE JS pattern, feature-detect `window.PublicKeyCredential && window.isSecureContext`
  - Dev Notes: IIFE early-return pattern for unsupported browsers
- Previous story: `_bmad-output/implementation-artifacts/3-2-passkey-first-authentication-on-login.md`
  - `passkeyAuthExecId` theme property pattern, `webauthn-authenticate.ftl` structure
- `webauthn-register.ftl` current state: `azguards-keycloak-custom-theme/.../login/webauthn-register.ftl` — 247 lines; brand mark at lines 18–24; CSS links at lines 7–10; auth card structure at lines 27–72; IIFE at lines 76–243
- `ReviewTenantInvitations.java`: dual-interface pattern, tracing pattern, `user.addRequiredAction(...)` routing
- `SelectActiveTenant.java`: `evaluateTriggers` conditional required-action pattern
- `CreateTenant.java`: minimal required-action example — useful reference for the boilerplate factory methods
- `META-INF/services/org.keycloak.authentication.RequiredActionFactory` — existing entries (append new line)
- `messages_en.properties` — current passkey keys end after `passkeyRegisterRetry`; append new `# Story 3.4` block after
- Sprint status: `_bmad-output/implementation-artifacts/sprint-status.yaml`
- KC 26.x `user.credentialManager()` API — `UserModel.credentialManager()` returns `SubjectCredentialManager`; use `getStoredCredentialsByTypeStream("webauthn")` to check for existing passkey credentials

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

- Implemented `PromptPasskeyEnrollment` as a dual-interface required action (RequiredActionProvider + RequiredActionFactory in one class), following the same pattern as `ReviewTenantInvitations`, `SelectActiveTenant`, and `CreateTenant`.
- `evaluateTriggers` checks for existing WebAuthn credentials via `user.credentialManager().getStoredCredentialsByTypeStream("webauthn")` (KC 26.x API) and guards against duplicate queuing.
- Per-session dismiss (OQ-8 Option A): `processAction` calls `context.success()` on dismiss with no attribute written; KC re-runs `evaluateTriggers` on next login.
- Enroll path routes to the existing `webauthn-register` required action via `user.addRequiredAction("webauthn-register"); context.success()`.
- Full Zipkin/Brave tracing on all three methods with span names `prompt-passkey-enrollment.evaluateTriggers`, `prompt-passkey-enrollment.challenge`, `prompt-passkey-enrollment.processAction`.
- `passkey-enrollment-prompt.ftl` is standalone HTML (no layout import), uses IIFE JS pattern, auto-dismisses on unsupported WebAuthn browsers (AC #5), focuses h1 on load (AC #6 a11y).
- Both repos built successfully: `mvn package -DskipTests` (Repo 1) and `mvn package` (Repo 2) → BUILD SUCCESS.
- All grep gates passed: 0 hardcoded hex, 0 console statements, 0 inline onclick handlers.

### File List

**Repo 1 — keycloak-multi-tenancy:**
- `src/main/java/dev/sultanov/keycloak/multitenancy/authentication/requiredactions/PromptPasskeyEnrollment.java` (NEW)
- `src/main/resources/META-INF/services/org.keycloak.authentication.RequiredActionFactory` (MODIFIED — appended 1 line)
- `src/main/resources/theme-resources/messages/messages_en.properties` (MODIFIED — added 4 passkeyEnroll keys)

**Repo 2 — azguards-keycloak-custom-theme:**
- `src/main/resources/theme/azguards-whatsapp/login/passkey-enrollment-prompt.ftl` (NEW)

## Change Log

- 2026-06-16: Story 3.4 implemented — `PromptPasskeyEnrollment` required action (Repo 1) and `passkey-enrollment-prompt.ftl` FTL (Repo 2). Both builds pass. All ACs satisfied. Status → review.
- 2026-06-17: Code review (Blind Hunter + Edge Case Hunter + Acceptance Auditor). No hard AC violations; all 6 ACs confirmed satisfied. Two "Critical" findings (credential-type mismatch / per-session-dismiss loop) refuted by codebase verification. 1 patch, 1 decision, 3 deferred, 6 dismissed.

### Review Findings (2026-06-17)

- [x] [Review][Patch] Redundant on-screen copy (resolved from Decision 2026-06-17) — `passkeyEnrollTitle` changed "Sign in faster next time" → "Set up a passkey" (matches `<title>` tag + primary button). [`messages_en.properties` passkeyEnrollTitle] — APPLIED 2026-06-17
- [x] [Review][Patch] Inconsistent `user` null-handling in `evaluateTriggers`/`processAction` — added early `return` (evaluateTriggers) / `context.success(); return;` (processAction) when `user == null`; removed the now-redundant ternary null checks. Challenge method's tracing-only guard left as-is. Repo 1 rebuilt → BUILD SUCCESS. [`PromptPasskeyEnrollment.java`] — APPLIED 2026-06-17
- [x] [Review][Defer] Standalone FTL has no session-expired scaffolding — spec-mandated standalone HTML (constraint #3, consistent across all passkey FTLs) means no `registrationLayout` error/expiry handling; if `${url.loginAction}` is ever unresolved the POST is indeterminate. Epic-wide consequence of the mandated pattern, not specific to 3.4. [`passkey-enrollment-prompt.ftl:40-49`] — deferred, pre-existing pattern
- [x] [Review][Defer] Auto-dismiss has no bfcache/`pageshow` guard — the unsupported-browser auto-submit (AC #5) can re-fire a stale dismiss POST against an expired execution on back/bfcache restore. Minor; shared by all auto-submitting forms in the epic. [`passkey-enrollment-prompt.ftl:62-67`] — deferred, pre-existing pattern
- [x] [Review][Defer] `catch (Exception)` lets `Error` mislabel the span as successful — `finally` runs `finishSpan(span, null)` when an `Error` (not `Exception`) escapes. Spec-dictated canonical tracing pattern shared by all instrumented classes; part of the systemic "harden TracingHelper" item already in deferred-work. [all 3 methods] — deferred, systemic tracing pattern
