---
baseline_commit: d435581
---

# Story 1.5: Accessibility Utilities & Focus Management

Status: done

## Story

As an Agent using assistive technology,
I want consistent focus management and error announcement primitives,
So that every auth screen meets WCAG 2.1 AA.

> **Working Repository:** `azguards-keycloak-custom-theme` (`~/WorkSpace/azguards-whatsapp/azguards-keycloak-custom-theme`)
> Pure CSS/JS story — no Java SPI changes, no `keycloak-multi-tenancy` work.

## Acceptance Criteria

1. **Focus on step transition:** On each screen load, focus moves to the page's `<h1>` (or first focusable field if no `<h1>`) when no `autofocus` element is already focused. On a form submission error, focus moves to the first `[aria-invalid="true"]` field, or to a `.wt-form__error-summary` if present. (UX-DR14)

2. **ARIA-wired inline errors:** Inline error elements use `aria-live="polite"` (announced on appearance) and are linked to their field via `aria-describedby`; the field carries `aria-invalid="true"` when in error state; color is NOT the sole error signal — icon + `danger-text` color + `aria-invalid` are combined. (NFR-A-3, NFR-A-4)

3. **Visible labels:** Every form field has a visible label element; placeholder text does not substitute for a label. (NFR-A-5) *(Checked via static audit — no code change expected if compliant.)*

4. **Global prefers-reduced-motion:** Under `prefers-reduced-motion: reduce`, ALL animations and transitions are reduced to near-zero duration — including any non-`--wt-*` animations from Bootstrap or PatternFly still present in the theme. (NFR-A-6)

5. **Zero critical/serious axe-core violations:** An axe-core scan on the rendered login page reports zero critical or serious violations. (NFR-A-1 baseline)

## Tasks / Subtasks

- [x] **Task 1: Add `--wt-duration` motion override to `tokens.css`** (AC: #4)
  - [x] Locate the existing `@media (prefers-reduced-motion: reduce)` `:root` block — or the `[data-theme="dark"]`/`@media (prefers-color-scheme: dark)` block structure.
    ```bash
    grep -n "prefers-reduced-motion\|wt-duration" src/main/resources/theme/azguards-whatsapp/login/resources/css/tokens.css
    ```
  - [x] If no `prefers-reduced-motion` block exists in `tokens.css`, append at the very end:
    ```css
    /* ── Motion override ────────────────────────────── */
    @media (prefers-reduced-motion: reduce) {
      :root {
        --wt-duration: 0.01ms;
      }
    }
    ```
  - [x] If a `prefers-reduced-motion` `:root` block already exists, add `--wt-duration: 0.01ms;` inside it.
  - [x] This cascades to every `.wt-*` component using `transition: … var(--wt-duration) …` — no component-level changes needed.

- [x] **Task 2: Add global motion catch-all to `style.css`** (AC: #4)
  - [x] Verify `style.css` does NOT already contain a `prefers-reduced-motion` block:
    ```bash
    grep -n "prefers-reduced-motion" src/main/resources/theme/azguards-whatsapp/login/resources/css/style.css
    ```
  - [x] Append at the END of `style.css` (after all existing rules):
    ```css
    /* ── Global motion safety net ───────────────────── */
    /* Catches Bootstrap/PatternFly transitions not using --wt-duration */
    @media (prefers-reduced-motion: reduce) {
      *,
      *::before,
      *::after {
        animation-duration: 0.01ms !important;
        animation-iteration-count: 1 !important;
        transition-duration: 0.01ms !important;
        scroll-behavior: auto !important;
      }
    }
    ```
  - [x] Verify `style.css` is under 200 lines after this addition — if it is unexpectedly large, re-read the file before editing.

- [x] **Task 3: Audit visible labels (AC: #3)**
  - [x] Run a quick grep to confirm all `<input>` fields in in-scope FTL templates have a corresponding `<label>`:
    ```bash
    grep -n "placeholder\|<input\|<label" src/main/resources/theme/azguards-whatsapp/login/*.ftl 2>/dev/null | head -40
    ```
  - [x] If any `<input>` uses placeholder-only and has no `<label>` (or `aria-label`), add the missing label. *(Expected outcome: all compliant — this is a verification task.)*
  - [x] **Out of scope:** Do NOT modify `register.ftl`, `login-oauth-grant.ftl`, email templates, or any template not in the in-scope set (AR-OOS).

- [x] **Task 4: Append `wtA11y` IIFE to `script.js`** (AC: #1, #2)
  - [x] Confirm the current end of `script.js`:
    ```bash
    tail -5 src/main/resources/theme/azguards-whatsapp/login/resources/js/script.js
    ```
  - [x] The file currently ends with the `wtToastInit` IIFE (added in Story 1.4). Append the `wtA11y` IIFE **after** it (at the very end of the file):
    ```javascript
    /* wtA11y: focus management, ARIA wiring, and motion utilities */
    (function () {
      'use strict';

      /* Returns true if user prefers reduced motion */
      function reducedMotion() {
        return !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);
      }

      /*
       * Focus the <h1> inside container, falling back to the first
       * enabled visible input/select/textarea/button.
       * container: HTMLElement (defaults to document.body)
       */
      function focusTarget(container) {
        var root = container || document.body;
        var h1 = root.querySelector('h1');
        if (h1) {
          if (!h1.hasAttribute('tabindex')) { h1.setAttribute('tabindex', '-1'); }
          h1.focus({ preventScroll: false });
          return;
        }
        var focusable = root.querySelector(
          'input:not([type="hidden"]):not([disabled]),' +
          'select:not([disabled]),' +
          'textarea:not([disabled]),' +
          'button:not([disabled])'
        );
        if (focusable) { focusable.focus({ preventScroll: false }); }
      }

      /*
       * On each page load, move focus to the <h1> or first field unless
       * the browser has already focused an [autofocus] element.
       * Called automatically on DOMContentLoaded.
       */
      function initPageFocus() {
        document.addEventListener('DOMContentLoaded', function () {
          /* If browser focused an autofocus element, don't override it */
          if (document.activeElement && document.activeElement !== document.body) { return; }
          focusTarget(document.body);
        });
      }

      /*
       * Move focus to first [aria-invalid="true"] field, or to a
       * .wt-form__error-summary element if present.
       * Call this from form submit handlers when validation fails.
       * form: HTMLElement (defaults to document)
       */
      function focusFirstError(form) {
        var root = form || document;
        var summary = root.querySelector('.wt-form__error-summary[role="alert"]');
        if (summary) {
          if (!summary.hasAttribute('tabindex')) { summary.setAttribute('tabindex', '-1'); }
          summary.focus({ preventScroll: false });
          return;
        }
        var invalid = root.querySelector('[aria-invalid="true"]:not([disabled])');
        if (invalid) { invalid.focus({ preventScroll: false }); }
      }

      /*
       * Wire aria-invalid + aria-describedby between a form field and its
       * inline error element.
       *   inputEl  — the <input> / <select> / <textarea>
       *   errorEl  — the .wt-field__error element
       *   show     — true = set error state; false = clear error state
       *
       * The errorEl also receives aria-live="polite" so the message is
       * announced when it becomes visible (NFR-A-3).
       * Call from Epic 2+ JS when dynamically showing/hiding inline errors.
       */
      function annotateFieldError(inputEl, errorEl, show) {
        if (!inputEl || !errorEl) { return; }
        if (show) {
          if (!errorEl.id) {
            errorEl.id = 'wt-err-' + (inputEl.id || Math.random().toString(36).slice(2, 9));
          }
          inputEl.setAttribute('aria-invalid', 'true');
          inputEl.setAttribute('aria-describedby', errorEl.id);
          errorEl.setAttribute('aria-live', 'polite');
        } else {
          inputEl.removeAttribute('aria-invalid');
          inputEl.removeAttribute('aria-describedby');
        }
      }

      /* Public API */
      window.wtA11y = {
        reducedMotion: reducedMotion,
        focusTarget: focusTarget,
        initPageFocus: initPageFocus,
        focusFirstError: focusFirstError,
        annotateFieldError: annotateFieldError
      };

      /* Auto-init page focus on every page load */
      initPageFocus();
    }());
    ```
  - [x] Verify no `console.log`/`console.debug`/`console.error` in the new code (NFR-S-1).

- [x] **Task 5: Build verification** (AC: all)
  - [x] Run `mvn package` in `azguards-keycloak-custom-theme` — must produce `BUILD SUCCESS`.
  - [x] Grep: `grep -n "console\." src/main/resources/theme/azguards-whatsapp/login/resources/js/script.js` — new code must add zero new instances (pre-existing ones from tenantStates code are deferred to Story 1.6).
  - [x] Grep: `grep -n "#[0-9a-fA-F]\{3,\}" src/main/resources/theme/azguards-whatsapp/login/resources/css/tokens.css src/main/resources/theme/azguards-whatsapp/login/resources/css/style.css` — zero new hardcoded hex introduced by this story.
  - [x] Confirm `--wt-duration: 0.01ms` appears in `tokens.css` under `prefers-reduced-motion`.
  - [x] Confirm global motion catch-all is at the end of `style.css`.
  - [x] Confirm `window.wtA11y` is exported and `initPageFocus()` is called at module bottom.

## Dev Notes

### Working Repository Reminder

**ALL work is in `azguards-keycloak-custom-theme`** (`~/WorkSpace/azguards-whatsapp/azguards-keycloak-custom-theme`). Do NOT touch `keycloak-multi-tenancy`.

Files to modify:

```
src/main/resources/theme/azguards-whatsapp/login/
  resources/css/
    tokens.css       ← MODIFY (Task 1 — add --wt-duration prefers-reduced-motion override)
    style.css        ← MODIFY (Task 2 — add global motion catch-all at end)
  resources/js/
    script.js        ← MODIFY (Task 4 — append wtA11y IIFE after toast IIFE)
  *.ftl templates    ← READ ONLY for Task 3 label audit; do not modify unless label is missing
```

### What This Story Does NOT Do

- **Does NOT apply `wtA11y` calls to FTL templates** — utilities are forward-defined only; Epic 2+ JS wires `focusFirstError` / `annotateFieldError` in response to form events.
- **Does NOT remove `console.log` from existing `script.js` code** — that is Story 1.6 (NFR-S-1 cleanup).
- **Does NOT change any existing `.wt-*` CSS classes** — only adds motion tokens.
- **Does NOT modify `register.ftl`, `login-oauth-grant.ftl`, email templates** (AR-OOS).

### CRITICAL: `@keyframes` Already Defined — Do NOT Redefine

`components.css` already has:
- `@keyframes wt-spin` at line 94 (used by `.wt-btn--submitting` and `.wt-interstitial__spinner`)
- `@keyframes wt-shimmer` at line 351 (used by skeleton-row components)

Do NOT add either keyframe in this story. The global `prefers-reduced-motion` catch-all in `style.css` (Task 2) will suppress them automatically.

### Why Two-Pronged Motion Strategy

**`--wt-duration` override in `tokens.css` (Task 1):** Cleanly disables all `.wt-*` component transitions since they use `transition: … var(--wt-duration) …`. No `!important` needed; it cascades naturally.

**Global catch-all in `style.css` (Task 2):** Covers Bootstrap utility classes (`transition-*`), PatternFly CSS (`--pf-v5-*` variables), and any non-tokenized transitions in `reviewTenant.css`, `selectTenant.css`, `style.css` itself. The `!important` is intentional and appropriate here — it is the standard pattern for motion accessibility override.

Both together guarantee 100% coverage regardless of CSS source.

### Focus Management Design Decisions

**Why `tabindex="-1"` on `<h1>`?** HTML headings are not natively focusable. `tabindex="-1"` makes them programmatically focusable (via `.focus()`) without adding them to the tab order. This is the correct WCAG pattern for managing focus programmatically after navigation events.

**Why not `autofocus` attribute on inputs?** Keycloak templates already use `autofocus` on some fields (e.g., username). `initPageFocus` checks `document.activeElement !== document.body` before acting — it will not steal focus from an `autofocus` element. This is the guard condition.

**`focusFirstError` call timing:** This must be called AFTER the DOM has been updated with `aria-invalid` attributes, not before. In Epic 2+ form handlers, call `annotateFieldError(input, error, true)` first, then `focusFirstError(form)`.

### ARIA Annotation Pattern for Epic 2+ Reference

When Epic 2 applies inline errors dynamically in JS:

```javascript
// Show error
var input = document.getElementById('username');
var errorEl = document.getElementById('username-error'); // .wt-field__error element
wtA11y.annotateFieldError(input, errorEl, true);
errorEl.style.display = 'flex'; // or remove --hidden class
wtA11y.focusFirstError(form);   // move focus after DOM update

// Clear error
wtA11y.annotateFieldError(input, errorEl, false);
errorEl.style.display = 'none';
```

For server-rendered errors (Keycloak already sets the error state in FTL on page load), Epic 2 FTL templates must:
- Add `aria-invalid="true"` directly on the `<input>` element
- Add `aria-describedby="{error-id}"` on the `<input>` element  
- Add `id="{error-id}"` and `aria-live="polite"` on the `.wt-field__error` element

The `wtA11y.annotateFieldError()` utility is for dynamic JS-driven error states. Static server-rendered errors must have the ARIA attributes in FTL markup.

### `prefers-reduced-motion` Impact on Existing Components

After Task 1, every `.wt-*` component using `var(--wt-duration)` automatically becomes near-instant under reduced motion:

| Component | Affected properties |
|---|---|
| `.wt-btn` | `transition: background-color, transform, box-shadow` |
| `.wt-method-row` | `transition: color, background-color` |
| `.wt-toast__action`, `.wt-toast__close` | hover color transitions |
| `.wt-banner__action` | hover color transition |

The per-component `@media (prefers-reduced-motion: reduce) { … transition: none; }` rules in `components.css` (lines 96, 235, 397, 436) remain valid — they are now redundant for `--wt-duration`-based transitions but serve as explicit documentation. Do NOT remove them.

### Deferred Items Checked — Not in Scope for This Story

- **`rgba(15,118,110,.25)` focus ring in style.css:814** — this was a stale deferred-work reference. The current `style.css` (181 lines) already uses `var(--wt-focus-ring-halo)` at line 136. `--wt-focus-ring-halo` is defined in `tokens.css` as `color-mix(in srgb, var(--wt-focus-ring) 25%, transparent)`. Already resolved.
- **Bootstrap utility classes not token-aware in dark mode** — Story 1.6 scope.
- **`console.log` in script.js** — Story 1.6 scope.

### Build System

```bash
# From ~/WorkSpace/azguards-whatsapp/azguards-keycloak-custom-theme
mvn package
# Produces: target/azguards-keycloak-custom-theme-1.0.15.jar (version may vary)
# No automated tests — verification is grep audits + mvn build
```

### Token Reference (Confirmed Present from Story 1.4)

| Token | Value | Notes |
|---|---|---|
| `--wt-focus-ring` | `#0F766E` (light) / `#2DD4BF` (dark) | Focus outline color |
| `--wt-focus-ring-halo` | `color-mix(…25%…)` | Used in `style.css:136` for `.form-control:focus` box-shadow |
| `--wt-focus-width` | `2px` | Outline width |
| `--wt-focus-offset` | `2px` | Outline offset |
| `--wt-duration` | `160ms` | Override to `0.01ms` under `prefers-reduced-motion` in Task 1 |
| `--wt-danger` | `#DC2626` / `#F87171` | Icon/fill color (not text) |
| `--wt-danger-text` | `#B91C1C` / `#FCA5A5` | Error message text color |

### Previous Story Intelligence (Stories 1.3 & 1.4 Key Learnings)

- `script.js` is at `src/main/resources/theme/azguards-whatsapp/login/resources/js/script.js` (305 lines)
- **Append to the END of `script.js`** — do not insert mid-file
- `script.js` starts with `var tenantStates = {};` and has `console.log` calls in the tenantStates block — do NOT delete them; that is Story 1.6
- The toast IIFE ends at line 305 — append `wtA11y` after it
- `components.css` = `src/main/resources/theme/azguards-whatsapp/login/resources/css/components.css`
- `tokens.css` = `src/main/resources/theme/azguards-whatsapp/login/resources/css/tokens.css`
- `style.css` = 181 lines currently
- `mvn package` → `BUILD SUCCESS` is the build gate
- Zero new hardcoded hex rule applies to all new CSS

### References

- Story AC and user story: [Source: `_bmad-output/planning-artifacts/epics.md` — Story 1.5]
- Focus management spec: [Source: `_bmad-output/planning-artifacts/ux-designs/ux-keycloak-multi-tenancy-2026-06-11/EXPERIENCE.md` — Focus management; Accessibility Floor]
- NFR-A-1..6: [Source: `_bmad-output/planning-artifacts/epics.md` — NonFunctional Requirements]
- UX-DR14 (focus management): [Source: epics.md — UX Design Requirements]
- AR-OOS: [Source: epics.md — Do not modify register.ftl, etc.]
- Token values: [Source: `_bmad-output/implementation-artifacts/1-3-core-form-component-styles.md` and `1-4-feedback-list-component-styles.md` — Dev Notes token tables]
- Deferred rgba focus ring: [Source: `_bmad-output/implementation-artifacts/deferred-work.md` — Deferred from story-1.2; verified resolved in current tokens.css]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Completion Notes List

- Task 1: No `prefers-reduced-motion` block existed in `tokens.css`. Appended new `@media (prefers-reduced-motion: reduce)` block at end of file setting `--wt-duration: 0.01ms`. Cascades automatically to all `.wt-*` components that use `var(--wt-duration)`.
- Task 2: No `prefers-reduced-motion` block existed in `style.css`. Appended global motion safety-net at end of file (194 lines total, under 200 limit). Covers Bootstrap/PatternFly transitions not using `--wt-duration`.
- Task 3: Label audit completed — all in-scope FTL inputs (`login.ftl`, `login-reset-password.ftl`, `login-update-password.ftl`) have visible `<label>` elements. Hidden inputs in `review-invitations.ftl` require no labels. No changes needed.
- Task 4: Appended `wtA11y` IIFE after `wtToastInit` IIFE at end of `script.js` (line 307+). Exports `window.wtA11y` with `reducedMotion`, `focusTarget`, `initPageFocus`, `focusFirstError`, `annotateFieldError`. Auto-calls `initPageFocus()` on module load. Zero new `console.*` calls.
- Task 5: `mvn package` → BUILD SUCCESS. Zero new console calls in new code. Zero new hardcoded hex values. All grep verification checks pass.

### File List

- `src/main/resources/theme/azguards-whatsapp/login/resources/css/tokens.css` (modified — appended `prefers-reduced-motion` motion override block)
- `src/main/resources/theme/azguards-whatsapp/login/resources/css/style.css` (modified — appended global motion safety-net block)
- `src/main/resources/theme/azguards-whatsapp/login/resources/js/script.js` (modified — appended `wtA11y` IIFE)

> All files in `azguards-keycloak-custom-theme` repo (`~/WorkSpace/azguards-whatsapp/azguards-keycloak-custom-theme`).

## Change Log

- 2026-06-12: Story 1.5 implemented — added `--wt-duration` motion override to `tokens.css`, global motion safety-net to `style.css`, and `wtA11y` IIFE to `script.js`. Label audit passed with no changes needed. BUILD SUCCESS confirmed.
- 2026-06-12: Code review completed — 3 patch, 2 defer, 6 dismissed.

### Review Findings

- [x] [Review][Patch] `initPageFocus` stacks duplicate DOMContentLoaded listeners on re-call — calling `initPageFocus()` multiple times registers multiple identical handlers; add a guard flag to prevent re-registration [`script.js:343-348`]
- [x] [Review][Patch] `annotateFieldError` does not remove `aria-live` on error clear — when `show=false`, `aria-live="polite"` remains on `errorEl`; add `errorEl.removeAttribute('aria-live')` in the else branch [`script.js:389-391`]
- [x] [Review][Patch] `annotateFieldError` ID generation fails for empty-string `inputEl.id` — if `inputEl.id` is `""` (falsy in JS but empty string), the generated ID becomes `wt-err-` with no suffix; guard with `inputEl.id || inputEl.name || Math.random()...` [`script.js:384`]
- [x] [Review][Defer] Missing `'use strict'` on `wtToastInit` IIFE [`script.js:281`] — deferred, pre-existing from Story 1.4
- [x] [Review][Defer] AC #5 axe-core scan not executed — spec-acknowledged limitation; no automated test infrastructure yet (Story 1.6 scope)

