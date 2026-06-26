---
baseline_commit: 7f51fae39e426e282ef127975a848f14e9d9bcf3
---

# Story 2.2: Inline Authentication Errors & Calm Lockout Messaging

Status: done

## Story

As an Agent who mistypes credentials,
I want clear, non-blaming error feedback with a recovery path,
so that I can correct course without confusion.

## Acceptance Criteria

1. **Given** invalid credentials are submitted **When** authentication fails **Then** an inline error "That username or password doesn't match." appears below the form (no modal), rendered via the `wt-login-error` component (FR-L-9, UX-DR7)
2. **Given** the account is locked or rate-limited **When** the Agent attempts login **Then** a calm explanatory message is shown: "Too many attempts. Your account is temporarily locked — check your email for reset instructions or try again in 15 minutes." — not a generic error (FR-L-10)
3. **Given** an error is displayed **When** it appears **Then** focus moves to the first invalid field (credentials error) or the error region (lockout) and the message is announced via `aria-live` (NFR-A-3, UX-DR14)
4. **Given** a credentials error **When** the error region renders **Then** both username and password fields receive `wt-field--error` class (danger border + danger-bg fill), `aria-invalid="true"`, and `aria-describedby="login-error"` (NFR-A-4, UX-DR3)
5. **Given** a lockout message **When** it renders **Then** the fields do NOT receive error styling — only the calm banner-style error region appears (no red fields for a brute-force lockout)
6. **Given** the error region **When** it renders **Then** it uses the `danger` icon (`⚠`) for credentials errors and no icon for lockout messages (different visual weight intentionally)
7. **Given** no error **When** the page renders **Then** the error region is absent from the DOM entirely (FTL conditional) — no empty `<div>` in the document

## Tasks / Subtasks

- [x] **Task 1: `messages_en.properties` — Override Keycloak auth error copy** (AC: #1, #2)
  - [x] Add `invalidUserMessage=That username or password doesn't match.`
  - [x] Add `invalidPasswordMessage=That username or password doesn't match.`
  - [x] Add `accountTemporarilyDisabledMessage=Too many attempts. Your account is temporarily locked — check your email for reset instructions or try again in 15 minutes.`
  - [x] Verify no duplicate keys introduced (run `sort -t= -k1 messages_en.properties | uniq -d -f0` — expect no output)

- [x] **Task 2: `style.css` — Add full `.wt-login-error` styles** (AC: #1, #2, #6)
  - [x] Add `.wt-login-error` base styles (padding, border-radius, font-size, `wt-field__error`-like icon via `::before`)
  - [x] Credentials-error styling: `var(--wt-danger-bg)` background, `1px solid var(--wt-danger-border)` border, `var(--wt-danger-text)` color, `⚠` icon via `::before`
  - [x] `.wt-login-error--locked` variant: `var(--wt-surface)` background, `3px solid var(--wt-border-strong)` left border only (banner pattern from components.css `.wt-banner`), `var(--wt-ink)` color, no icon
  - [x] Verify no hardcoded hex values in new additions: `grep -n "#[0-9a-fA-F]\{3,\}" style.css` new lines must be clean

- [x] **Task 3: `login.ftl` — Wire error region and field error states** (AC: #1–#7)
  - [x] Add FTL variables block before the card's `<h1>` (inside the `.wt-card` div):
    ```
    <#assign showError = message?has_content && (message.type == "error" || message.type == "warning")>
    <#assign isLocked = showError && (message.summary?lower_case?contains("temporarily locked") || message.summary?lower_case?contains("too many"))>
    <#assign showFieldError = showError && !isLocked>
    ```
  - [x] Move the `wt-login-error` div to BELOW the `</form>` closing tag (it is currently above the form — move it between `</form>` and `<div class="wt-methods">`), preserving `id="login-error"`, `role="alert"`, `aria-live="polite"`, `aria-atomic="true"`
  - [x] Wrap the error div with `<#if showError>` … `</#if>` (so the div is absent when no error — AC #7)
  - [x] Apply `wt-login-error--locked` class conditionally: `class="wt-login-error<#if isLocked> wt-login-error--locked</#if>"`
  - [x] Add `<#if showFieldError> wt-field--error</#if>` to both the username field's `<div class="wt-field">` and the password field's `<div class="wt-field">`
  - [x] Add `<#if showFieldError>aria-invalid="true" aria-describedby="login-error"</#if>` to the `<input>` for both username and password
  - [x] Ensure `autofocus` remains on the username field (no change — it was there before)

- [x] **Task 4: `login.ftl` inline script — Focus management on error** (AC: #3)
  - [x] Add an error-focus block to the inline IIFE (after the bfcache handler, before closing `}()`):
    - If the page loaded with a credentials error (`.wt-login-error` present, no `--locked` class): call `wtA11y.focusFirstError(document)` — focuses username field (it has `aria-invalid="true"`)
    - If the page loaded with a lockout message (`--locked` class present): set `tabindex="-1"` on `#login-error` and focus it directly
  - [x] Guard all `wtA11y` calls with `typeof wtA11y !== 'undefined'` null guard

- [x] **Task 5: Build verification** (AC: none — hygiene)
  - [x] `cd ~/WorkSpace/azguards-whatsapp/azguards-keycloak-custom-theme && mvn package` → `BUILD SUCCESS`
  - [x] `grep -n "#[0-9a-fA-F]\{3,\}" src/main/resources/theme/azguards-whatsapp/login/resources/css/style.css` — new lines must have zero hits (pre-existing `.btn-onboard` etc. are out of scope — do not touch them)

---

## Dev Notes

### Working Repository

**100% `azguards-keycloak-custom-theme`.** No changes to `keycloak-multi-tenancy`.

```
~/WorkSpace/azguards-whatsapp/azguards-keycloak-custom-theme/
  src/main/resources/theme/azguards-whatsapp/login/
    messages/
      messages_en.properties          ← MODIFY (Task 1)
    resources/
      css/
        style.css                     ← MODIFY (Task 2)
    login.ftl                         ← MODIFY (Tasks 3, 4)
```

No Java/SPI changes. No new files. Three targeted edits only.

---

### Current State of login.ftl (what you are changing)

The file was rewritten in Story 2.1. The error region currently sits **above** the `<form>` element (between `<h1>` and the form). Your job is to move it **below** `</form>` and fully wire it.

**Current error region (lines 31–38 in login.ftl):**
```html
<!-- Error region — Story 2.2 wires ${message} content into here -->
<div
  class="wt-login-error"
  id="login-error"
  role="alert"
  aria-live="polite"
  aria-atomic="true"
><#if message?has_content && message.type == "error">${kcSanitize(message.summary)?no_esc}</#if></div>
```

This div must be REMOVED from its current location and RE-PLACED below `</form>` with the fully wired implementation described in Task 3.

**Current form structure (simplified):**
```
<div class="wt-card">
  <h1>…</h1>
  <!-- error div is here NOW — MOVE IT -->
  <form id="loginForm">
    <div class="wt-field"> username </div>
    <div class="wt-field"> password </div>
    <button type="submit"> Login </button>
  </form>
  <!-- error div GOES HERE after move -->
  <div class="wt-methods">…</div>
  <a class="wt-btn wt-btn--ghost">Create New Business</a>
</div>
```

**After your changes the `.wt-card` structure must be:**
```
<div class="wt-card">
  <h1>…</h1>

  <#-- FTL vars: showError, isLocked, showFieldError -->

  <form id="loginForm">
    <div class="wt-field [+ wt-field--error if showFieldError]">
      username input [+ aria-invalid + aria-describedby if showFieldError]
    </div>
    <div class="wt-field [+ wt-field--error if showFieldError]">
      password show/hide wrapper [+ aria-invalid + aria-describedby if showFieldError]
    </div>
    <button type="submit">Login</button>
  </form>

  <#if showError>
  <div class="wt-login-error[--locked if isLocked]" id="login-error" role="alert"
       aria-live="polite" aria-atomic="true">
    ${kcSanitize(message.summary)?no_esc}
  </div>
  </#if>

  <div class="wt-methods">…</div>
  <a class="wt-btn wt-btn--ghost">Create New Business</a>
</div>
```

---

### Keycloak Message Architecture

**How Keycloak surfaces errors to FTL:**

| Scenario | `message.type` | `message.summary` (after our i18n override) |
|---|---|---|
| Invalid credentials | `"error"` | `"That username or password doesn't match."` |
| Brute-force lockout | `"warning"` or `"error"` | `"Too many attempts. Your account is temporarily locked — check your email for reset instructions or try again in 15 minutes."` |
| Session timeout / general | `"warning"` | Various Keycloak-internal strings |
| Success / info | `"success"` / `"info"` | Positive copy — NOT shown by this error region |

The FTL condition `(message.type == "error" || message.type == "warning")` covers both credentials errors and lockout. The lockout detection via `?lower_case?contains("temporarily locked")` is reliable because our `messages_en.properties` override is the canonical source of that string.

**Why `kcSanitize(message.summary)?no_esc` (not `?html`):**
`kcSanitize` is a Keycloak-provided FTL macro that allows safe HTML (e.g., `<a>` tags in server-generated messages) while stripping XSS vectors. This pattern is confirmed present in the existing `review-invitations.ftl`. If `kcSanitize` is ever unavailable, fall back to `${message.summary?html}` — never use `${message.summary}` raw.

**Current `messages_en.properties` state:**
The file contains only one key:
```
updatePasswordSuccess=Your password has been successfully updated.
```
Add the three new keys — do not remove the existing one.

---

### Keycloak Message Keys Reference

These are the canonical Keycloak 26.x message keys that get overridden:

| Key | Default Keycloak text | Our override |
|---|---|---|
| `invalidUserMessage` | "Invalid username or password." | "That username or password doesn't match." |
| `invalidPasswordMessage` | "Invalid username or password." | "That username or password doesn't match." |
| `accountTemporarilyDisabledMessage` | "Account is temporarily disabled, contact admin or try again later." | "Too many attempts. Your account is temporarily locked — check your email for reset instructions or try again in 15 minutes." |

These keys are sourced from Keycloak's `messages_en.properties` in the base theme. Our override file in `src/main/resources/theme/azguards-whatsapp/login/messages/` takes precedence for keys that exist there — unrecognized keys fall back to the base theme.

---

### CSS: What Already Exists vs. What to Add

**Already in `style.css` (line 125):**
```css
/* Error region placeholder — Story 2.2 wires the content */
.wt-login-error:empty { display: none; }
```
This `:empty` rule is a safety net. With Task 3's `<#if showError>` conditional, the div won't be in the DOM when there's no error anyway — but keep the `:empty` rule as defense-in-depth.

**What to ADD to `style.css` (append after the `:empty` rule):**

```css
/* Credentials error: full danger styling (AC #1, AC #4) */
.wt-login-error {
  display: flex;
  align-items: flex-start;
  gap: var(--wt-space-1);
  padding: var(--wt-space-3) var(--wt-space-4);
  background-color: var(--wt-danger-bg);
  border: 1px solid var(--wt-danger-border);
  border-radius: var(--wt-radius-field);
  font-size: var(--wt-text-caption-size);
  font-weight: var(--wt-text-label-weight);
  color: var(--wt-danger-text);
  line-height: var(--wt-text-body-line);
}

.wt-login-error::before {
  content: '\26A0\FE0E';  /* ⚠ forced text variant, not emoji */
  flex-shrink: 0;
  color: var(--wt-danger);
  font-size: 13px;
  speak: never;           /* decorative — message text is the AT signal */
}

/* Lockout variant: calm banner style — no icon, muted border (AC #2, AC #5) */
.wt-login-error--locked {
  background-color: var(--wt-surface);
  border: none;
  border-left: 3px solid var(--wt-border-strong);
  border-radius: 0 var(--wt-radius-field) var(--wt-radius-field) 0;
  color: var(--wt-ink);
  font-size: var(--wt-text-body-size);
  font-weight: var(--wt-text-body-weight);
}

.wt-login-error--locked::before {
  content: none;  /* no icon for calm lockout message */
}
```

**Tokens used** — all already defined in `tokens.css`:
- `--wt-danger-bg`, `--wt-danger-border`, `--wt-danger-text`, `--wt-danger` — light and dark variants defined
- `--wt-surface`, `--wt-border-strong`, `--wt-ink` — defined in both modes
- `--wt-space-1/3/4`, `--wt-radius-field`, `--wt-text-caption-size/body-size` — all defined
- No hardcoded hex values anywhere in the additions

---

### wtA11y API Reference

Defined in `script.js` (lines 272–381). Auto-loaded via `<script src="${url.resourcesPath}/js/script.js">` before the inline script. Fully available in the inline IIFE.

| Function | Signature | What it does |
|---|---|---|
| `wtA11y.focusFirstError(form)` | `(HTMLElement\|undefined)` | Focuses first `[aria-invalid="true"]` field, or `.wt-form__error-summary[role="alert"]` if present |
| `wtA11y.annotateFieldError(inputEl, errorEl, show)` | `(el, el, bool)` | Sets/clears `aria-invalid` + `aria-describedby` on a field |
| `wtA11y.focusTarget(container)` | `(HTMLElement\|undefined)` | Focuses `<h1>` or first focusable in container |

**For this story**: use `focusFirstError()` for credentials errors (username field has `aria-invalid` set in FTL). For lockout, manually focus `#login-error` (no `aria-invalid` fields exist in that state).

**Focus management inline script additions** (add to existing IIFE in login.ftl, after the bfcache `pageshow` handler):

```js
/* ── Story 2.2: focus management on server-side auth error ── */
(function () {
  var errorEl = document.getElementById('login-error');
  if (!errorEl) { return; }                          // no error: nothing to do
  var isLocked = errorEl.classList.contains('wt-login-error--locked');
  if (isLocked) {
    /* Lockout: fields are NOT marked invalid; announce the region to AT */
    if (!errorEl.hasAttribute('tabindex')) { errorEl.setAttribute('tabindex', '-1'); }
    errorEl.focus({ preventScroll: false });
  } else {
    /* Credentials error: username field has aria-invalid="true" in FTL */
    if (typeof wtA11y !== 'undefined') { wtA11y.focusFirstError(document); }
  }
}());
```

> **Why an inner IIFE for the error block?** To avoid leaking `errorEl`/`isLocked` into the outer scope that already uses `passwordEl` and similar. The outer IIFE remains for the password show/hide and submit handler. The inner block is self-contained.

---

### Story 2.1 Patterns to Preserve

Story 2.1 established these patterns in `login.ftl`. Do NOT break them:

| Pattern | Location in login.ftl | Must preserve |
|---|---|---|
| `autofocus` on `#username` | Input attribute | Yes — defers `wtA11y.initPageFocus()` from `<h1>` |
| `autocomplete="username webauthn"` | Username input | Yes — passkey enablement for Story 3.2 |
| `autocomplete="current-password"` | Password input | Yes |
| `bfcache pageshow` reset handler | Inline IIFE | Yes — re-enables submit button on back-navigation |
| `wt_last_auth_method` localStorage write | Inline IIFE | Yes — Stories 2.3/2.4 will read this |
| `novalidate` on `<form>` | Form attribute | Yes — disables native browser validation, letting Keycloak handle it |
| `role="alert"` + `aria-live="polite"` + `aria-atomic="true"` | Error div | Yes — preserve all three |
| `onerror` fallback on brand `<img>` | `wt-login-brand` img | Yes |
| `.wt-methods` empty structural slot | After form | Yes — 2.3/2.4 inject here |

---

### FTL: Exact Changes to `login.ftl`

#### Remove from current location (lines 31–38):
```html
        <!-- Error region — Story 2.2 wires ${message} content into here -->
        <div
          class="wt-login-error"
          id="login-error"
          role="alert"
          aria-live="polite"
          aria-atomic="true"
        ><#if message?has_content && message.type == "error">${kcSanitize(message.summary)?no_esc}</#if></div>
```

#### Replace `<h1>` line with FTL vars + `<h1>`:
```ftl
        <#assign showError = message?has_content && (message.type == "error" || message.type == "warning")>
        <#assign isLocked = showError && (message.summary?lower_case?contains("temporarily locked") || message.summary?lower_case?contains("too many"))>
        <#assign showFieldError = showError && !isLocked>

        <h1 class="wt-login-title">Login to <strong>Agent Account</strong></h1>
```

#### Modify username field wrapper:
```html
          <div class="wt-field<#if showFieldError> wt-field--error</#if>">
            <label class="wt-field__label" for="username">Username</label>
            <input
              class="wt-field__input"
              type="text"
              id="username"
              name="username"
              autocomplete="username webauthn"
              required
              autofocus
              <#if showFieldError>aria-invalid="true" aria-describedby="login-error"</#if>
            >
          </div>
```

#### Modify password field wrapper:
```html
          <div class="wt-field<#if showFieldError> wt-field--error</#if>">
            <label class="wt-field__label" for="password">Password</label>
            <div class="wt-field__input-wrap">
              <input
                class="wt-field__input"
                type="password"
                id="password"
                name="password"
                autocomplete="current-password"
                required
                <#if showFieldError>aria-invalid="true" aria-describedby="login-error"</#if>
              >
              <!-- show/hide toggle button — unchanged from Story 2.1 -->
```

#### Insert after `</form>`, before `<div class="wt-methods">`:
```ftl
        <#if showError>
        <div
          class="wt-login-error<#if isLocked> wt-login-error--locked</#if>"
          id="login-error"
          role="alert"
          aria-live="polite"
          aria-atomic="true"
        >${kcSanitize(message.summary)?no_esc}</div>
        </#if>
```

---

### Build System

```bash
cd ~/WorkSpace/azguards-whatsapp/azguards-keycloak-custom-theme
mvn package
# Must produce: BUILD SUCCESS
```

No integration tests for theme changes. Visual verification requires deploying to a running Keycloak instance.

---

### What Is Explicitly Out of Scope

- "Sign in with SSO" secondary link — Story 2.3
- "Email me a sign-in link" (magic link) — Story 2.4
- Client-side validation (empty-field detection) — not in scope; `novalidate` intentionally disables browser validation
- Per-field `wt-field__error` text elements — not needed here; the form-level error region covers the credential scenario
- Refactoring `review-invitations.ftl`, `select-tenant.ftl`, other FTL templates — out of scope
- Any Java/SPI changes — no Java changes in this story

### Deferred Items (do not address)

These are tracked in `_bmad-output/implementation-artifacts/deferred-work.md`:
- Bootstrap CDN still present on reset/update/select/review templates — Epics 4/5
- `label { color: var(--wt-muted) !important; }` in style.css overrides the field labels — tracked but not in scope now
- `axe-core` scan — deferred from Story 1.5

---

### References

- FR-L-9 (inline errors below form): `epics.md` — Story 2.2 AC
- FR-L-10 (calm lockout copy): `epics.md` — Story 2.2 AC
- NFR-A-3 (aria-live + aria-describedby): `epics.md` — NFR-A-3
- NFR-A-4 (non-color error signal): `epics.md` — NFR-A-4
- UX-DR3 (field error state: danger border + bg): `DESIGN.md` — Components: Text field
- UX-DR7 (inline error component): `DESIGN.md` — Components: Inline error
- UX-DR14 (focus management on error): `EXPERIENCE.md` — Interaction Primitives
- `.wt-field--error` + `.wt-field__error` styles: `components.css:162-192`
- `.wt-banner` pattern (for lockout variant): `components.css:329-363`
- `wtA11y.focusFirstError()` API: `script.js:330-341`
- `wtA11y.annotateFieldError()` API: `script.js:353-368`
- `kcSanitize` FTL pattern: confirmed in `review-invitations.ftl` (existing)
- `wt-login-error:empty` CSS placeholder: `style.css:125`
- `--wt-danger-bg`, `--wt-danger-border`, `--wt-danger-text` tokens: `tokens.css:29-33`
- Keycloak lockout message key: `accountTemporarilyDisabledMessage` (Keycloak 26.x base theme)
- Previous story (2.1) error region structure: `2-1-refactored-login-screen.md` — Task 3 / Dev Notes: "Error region placeholder"
- Story 2.1 bfcache pattern: `2-1-refactored-login-screen.md` — Review Findings (patch applied)

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

- Task 1: Added 3 i18n overrides to `messages_en.properties` — `invalidUserMessage`, `invalidPasswordMessage`, `accountTemporarilyDisabledMessage`. No duplicates.
- Task 2: Added `.wt-login-error` (danger styling + ⚠ icon) and `.wt-login-error--locked` (calm banner, no icon) to `style.css`. Zero hardcoded hex values.
- Task 3: Added FTL variables (`showError`, `isLocked`, `showFieldError`) before `<h1>` in `login.ftl`; moved error region from above `<form>` to below `</form>` (before `.wt-methods`); wrapped with `<#if showError>`; applied `wt-field--error`, `aria-invalid`, `aria-describedby` conditionally on both fields.
- Task 4: Added focus-management IIFE to inline script — credentials error focuses first `aria-invalid` field via `wtA11y.focusFirstError`, lockout focuses `#login-error` directly. All `wtA11y` calls guarded with `typeof` check.
- Task 5: `mvn package` → BUILD SUCCESS. No hardcoded hex values in new CSS.
- All 7 ACs satisfied. Build passes. No regressions (theme-only project, no existing tests).

### File List

- `src/main/resources/theme/azguards-whatsapp/login/messages/messages_en.properties` (azguards-keycloak-custom-theme)
- `src/main/resources/theme/azguards-whatsapp/login/resources/css/style.css` (azguards-keycloak-custom-theme)
- `src/main/resources/theme/azguards-whatsapp/login/login.ftl` (azguards-keycloak-custom-theme)

## Change Log

- 2026-06-13: Implemented Story 2.2 — inline auth error region + calm lockout messaging. Added i18n overrides, `.wt-login-error`/`--locked` CSS, FTL error wiring with field error states, and focus management IIFE. All 5 tasks complete, `mvn package` → BUILD SUCCESS.

## Review Findings

_Code review 2026-06-14 (Blind Hunter + Edge Case Hunter + Acceptance Auditor). All 7 ACs verified MET by the Acceptance Auditor. Findings below are robustness/UX concerns, not AC violations._

**Decision needed (resolved 2026-06-14 → patched):**

- [x] [Review][Decision→Patch] Lockout detection couples logic to English copy — `isLocked` matched substrings `"temporarily locked"` / `"too many"` against the *resolved, localized* `message.summary` (login.ftl:30). **Fixed:** detection now reads a null-safe `errorText = (message.summary!"")?lower_case`, with a prominent comment documenting the copy↔matcher coupling and the i18n constraint (Keycloak FTL does not expose the message *key*, so summary-text matching is the only in-template lever — robustness now bounded by documentation + null-safety). (source: blind+edge)
- [x] [Review][Decision→Patch] `warning`-type messages over-apply field-error styling — `showFieldError = showError && !isLocked` decorated fields for any non-locked warning (login.ftl:31). **Fixed:** now `showFieldError = showError && message.type == "error" && !isLocked`, so only genuine credential errors (type `error`) mark fields `wt-field--error` + `aria-invalid`; non-credential warnings render the message region only. (source: blind+edge+auditor)

**Patch (applied 2026-06-14):**

- [x] [Review][Patch] Null-safe `message.summary` before `?lower_case` [login.ftl:30] — **Fixed** via the shared `errorText = (message.summary!"")?lower_case` assignment (folded into the lockout-detection patch above). (source: blind+edge)
- [x] [Review][Patch] Add trailing newline to messages_en.properties [messages_en.properties:4] — **Fixed:** trailing newline appended. (source: blind+auditor)

_All four resolved/applied. `mvn package` re-run after changes → BUILD SUCCESS (FTL revalidated)._

**Deferred (pre-existing / minor / spec-prescribed):**

- [x] [Review][Defer] Decorative ⚠ icon exposed to assistive tech [style.css:127] — `speak: never` is an obsolete no-op; the `::before` glyph may be announced by screen readers. Needs a real `aria-hidden` element to silence — deferred, spec-prescribed CSS.
- [x] [Review][Defer] Lockout message double-announced [login.ftl:104,189] — `role="alert"` auto-announces on insert and the JS also focuses the element, causing a second read-out — deferred, minor a11y.
- [x] [Review][Defer] `info`/`success` message types not rendered [login.ftl:29] — `showError` only handles error/warning; info/success (logout, reset confirmations) never display — deferred, pre-existing (old template also dropped them), out of story scope.
- [x] [Review][Defer] No fallback focus when `wtA11y` is undefined [login.ftl:192] — if script.js fails to load, the credentials-error branch moves no focus; the locked branch is self-contained — deferred, minor graceful-degradation gap.
- [x] [Review][Defer] Inconsistent error-summary contract [login.ftl + script.js:332] — `focusFirstError` targets `.wt-form__error-summary`, login uses `.wt-login-error`; works via the `[aria-invalid]` fallback but surprises future maintainers — deferred, naming inconsistency only.
- [x] [Review][Defer] bfcache restore doesn't re-run error focus [login.ftl:185] — the parse-time IIFE doesn't re-fire on `pageshow`/back-navigation, so a restored locked page won't re-focus the banner — deferred, minor edge case.

**Dismissed as noise (5):** aria-describedby pointing to an element below the form (valid — `aria-describedby` is DOM-order independent); `.wt-login-error:empty` called "dead code" (intentional defense-in-depth, still reachable on empty summary); the `:empty` vs `display:flex` "source-order override" claim (false — `:empty` has higher specificity and wins); `message.type` casing not normalized (Keycloak always emits lowercase type); `kcSanitize(...)?no_esc` flagged as XSS (correct standard Keycloak pattern).
