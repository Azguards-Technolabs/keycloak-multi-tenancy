---
baseline_commit: 7f51fae39e426e282ef127975a848f14e9d9bcf3
---

# Story 1.4: Feedback & List Component Styles

Status: review

## Story

As a front-end engineer,
I want the inline-error, method-row, toast, banner, skeleton-row, and interstitial components styled on the tokens,
so that feature epics can compose them without re-inventing styles.

> **Working Repository:** `azguards-keycloak-custom-theme` (`~/WorkSpace/azguards-whatsapp/azguards-keycloak-custom-theme`)
> Pure CSS/JS component story — no Java SPI changes, no `keycloak-multi-tenancy` work.

## Acceptance Criteria

1. **Inline-error icon fixed (`.wt-field__error`):** The deferred Story 1.3 ⚠ emoji is replaced with `content: '\26A0\FE0E'` (Unicode text variant, not emoji) + `speak: never`. The component shows `danger-text` color for the message and `danger` color for the icon; it appears directly beneath the offending field and is never a modal/toast. (UX-DR7, deferred from 1.3)

2. **Method-row component styled:** `.wt-method-row` is a full-width `button`-role element, `muted` text at rest, transitions to `ink` text + `hover-tint` background on hover, `min-height: 44px` (≥ FR-L-11), `label` font size/weight (14px/500), no visible border at rest. Focus: 2px `focus-ring` outline + 2px offset. `prefers-reduced-motion` eliminates the transition. (UX-DR8, NFR-A-2)

3. **Toast component styled + JS dismiss wired:** `.wt-toast` uses `surface` background, `1px border` hairline, `radius-card` rounding (12px), `shadow-card` elevation. `.wt-toast--success` modifier adds a 3px `solid var(--wt-success)` left border and `success-text` message color. `.wt-toast__close` is a 44×44px target button. `.wt-toast--hidden` hides the toast. A self-contained JS IIFE appended to `script.js` provides `window.wtToastInit(el, delay)` — auto-dismisses after `delay` ms (default 5000) and wires the close button. FTL usage requires `role="status" aria-live="polite"` on the container (documented below). (UX-DR9, NFR-A)

4. **Banner component styled:** `.wt-banner` is `surface` background with a `3px solid var(--wt-border-strong)` left rule (not a full border), `radius-field` rounding on the right corners only, inline within the card. Never blocks the primary action (sits above or below content, not overlaid). Provides `.wt-banner__message` and `.wt-banner__action` (primary-colored underline link). (UX-DR10)

5. **Skeleton-row component styled:** `.wt-skeleton-avatar` (40px circle) and `.wt-skeleton-line` use a `linear-gradient` shimmer animation (`@keyframes wt-shimmer`). Under `prefers-reduced-motion: reduce`, the animation is removed and fills are static `border` color. ARIA attributes (`aria-hidden="true"` on rows, `aria-busy="true"` on region) are FTL responsibility — documented below. (UX-DR11, NFR-A-6)

6. **Interstitial component styled:** `.wt-interstitial` is a flex column (center/center) with a 32px CSS spinner (`@keyframes wt-spin` — already in `components.css` from Story 1.3, do NOT redefine) and a `.wt-interstitial__caption`. Under `prefers-reduced-motion: reduce`, the spinner is hidden and `.wt-interstitial__motion-text` is shown as the static text fallback. (UX-DR12, NFR-A-6)

7. **Success tokens present in `tokens.css`:** `--wt-success` and `--wt-success-text` exist for light and dark. If missing, add them (values from DESIGN.md below). (Prerequisite for AC #3)

8. **`--wt-radius-card` token verified:** The 12px card-radius token (`--wt-radius-card`) used by `.wt-toast` exists in `tokens.css`. If only `--wt-radius-card-lg` (16px) exists, add `--wt-radius-card: 12px` to `:root`. (Prerequisite for AC #3)

9. **Zero new hardcoded hex in `components.css`:** All values use `var(--wt-*)` or `color-mix()`. Confirmed by grep audit.

10. **`mvn package` passes:** The theme JAR builds without errors after all changes.

## Tasks / Subtasks

- [x] **Task 1: Verify/add missing tokens in `tokens.css`** (AC: #7, #8)
  - [x] Grep: `grep -n "wt-success" src/main/resources/theme/azguards-whatsapp/login/resources/css/tokens.css`
  - [x] If `--wt-success` is missing, add to `:root` (light) after `--wt-danger-bg`:
    ```css
    --wt-success:       #16A34A;   /* success green, icons/fills */
    --wt-success-text:  #15803D;   /* success text, AA on surface */
    ```
  - [x] Add to `@media (prefers-color-scheme: dark) :root` AND `[data-theme="dark"]` blocks:
    ```css
    --wt-success:       #34D399;
    --wt-success-text:  #6EE7B7;
    ```
  - [x] Grep: `grep -n "wt-radius-card[^-]" src/main/resources/theme/azguards-whatsapp/login/resources/css/tokens.css`
  - [x] If `--wt-radius-card` (without `-lg`) is missing, add to `:root`: `--wt-radius-card: 12px;`

- [x] **Task 2: Fix inline-error icon in `components.css`** (AC: #1)
  - [x] Locate `.wt-field__error::before` in `components.css`
  - [x] Replace `content: '⚠'` with the text-presentation variant:
    ```css
    .wt-field__error::before {
      content: '\26A0\FE0E';   /* ⚠ forced text variant — not emoji */
      flex-shrink: 0;
      color: var(--wt-danger);
      font-size: 13px;
      speak: never;            /* decorative — message text is the AT signal */
    }
    ```
  - [x] Verify `.wt-field__error` still has `display: none` at rest and `display: flex` when `.wt-field--error` is active (unchanged from 1.3)

- [x] **Task 3: Add method-row styles to `components.css`** (AC: #2)
  - [x] Append after the `.wt-card` block:
    ```css
    /* ── Method Row ─────────────────────────────────── */
    .wt-method-row {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: var(--wt-space-2);
      width: 100%;
      min-height: 44px;
      padding: var(--wt-space-2) var(--wt-space-4);
      background-color: transparent;
      border: none;
      border-radius: var(--wt-radius-field);
      font-family: var(--wt-font-family), sans-serif;
      font-size: var(--wt-text-label-size);
      font-weight: var(--wt-text-label-weight);
      line-height: var(--wt-text-label-line);
      color: var(--wt-muted);
      text-decoration: none;
      cursor: pointer;
      transition: color var(--wt-duration) var(--wt-easing),
                  background-color var(--wt-duration) var(--wt-easing);
      box-sizing: border-box;
    }
    .wt-method-row:hover:not(:disabled) {
      color: var(--wt-ink);
      background-color: var(--wt-hover-tint);
    }
    .wt-method-row:focus-visible {
      outline: var(--wt-focus-width) solid var(--wt-focus-ring);
      outline-offset: var(--wt-focus-offset);
      color: var(--wt-ink);
    }
    .wt-method-row:disabled {
      opacity: 0.4;
      cursor: not-allowed;
    }
    @media (prefers-reduced-motion: reduce) {
      .wt-method-row { transition: none; }
    }
    ```

- [x] **Task 4: Add toast styles to `components.css`** (AC: #3)
  - [x] Append after the method-row block:
    ```css
    /* ── Toast ──────────────────────────────────────── */
    .wt-toast {
      display: flex;
      align-items: flex-start;
      gap: var(--wt-space-3);
      padding: var(--wt-space-3) var(--wt-space-4);
      background-color: var(--wt-surface);
      border: 1px solid var(--wt-border);
      border-radius: var(--wt-radius-card);
      box-shadow: var(--wt-shadow-card);
      max-width: var(--wt-card-max-width);
      width: 100%;
      box-sizing: border-box;
      position: relative;
    }
    .wt-toast--success {
      border-left: 3px solid var(--wt-success);
    }
    .wt-toast--hidden {
      display: none;
    }
    .wt-toast__body {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: var(--wt-space-1);
    }
    .wt-toast__message {
      font-size: var(--wt-text-body-size);
      line-height: var(--wt-text-body-line);
      color: var(--wt-ink);
    }
    .wt-toast--success .wt-toast__message {
      color: var(--wt-success-text);
    }
    .wt-toast__action {
      display: inline;
      background: none;
      border: none;
      padding: 0;
      font-family: var(--wt-font-family), sans-serif;
      font-size: var(--wt-text-caption-size);
      color: var(--wt-muted);
      text-decoration: underline;
      text-underline-offset: 2px;
      cursor: pointer;
    }
    .wt-toast__action:hover { color: var(--wt-ink); }
    .wt-toast__action:focus-visible {
      outline: var(--wt-focus-width) solid var(--wt-focus-ring);
      outline-offset: var(--wt-focus-offset);
    }
    .wt-toast__close {
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      width: 44px;
      height: 44px;
      margin: calc(var(--wt-space-3) * -1) calc(var(--wt-space-4) * -1) 0 0;
      background: none;
      border: none;
      border-radius: var(--wt-radius-field);
      color: var(--wt-muted);
      font-size: 18px;
      line-height: 1;
      cursor: pointer;
    }
    .wt-toast__close:hover { color: var(--wt-ink); }
    .wt-toast__close:focus-visible {
      outline: var(--wt-focus-width) solid var(--wt-focus-ring);
      outline-offset: var(--wt-focus-offset);
    }
    ```

- [x] **Task 5: Add toast JS utility to `script.js`** (AC: #3)
  - [x] First, check if `script.js` already has a module pattern or IIFE wrapper — append inside/after the existing structure, do not break existing code
  - [x] Check if `script.js` is located at `src/main/resources/theme/azguards-whatsapp/login/resources/js/script.js`
  - [x] Append to the END of `script.js`:
    ```javascript
    /* wt-toast: auto-dismiss + close button */
    (function () {
      function wtToastInit(el, delay) {
        if (!el) return;
        var ms = (delay !== undefined && delay !== null) ? delay : 5000;
        var timer = ms > 0 ? setTimeout(function () {
          el.classList.add('wt-toast--hidden');
        }, ms) : null;
        var closeBtn = el.querySelector('.wt-toast__close');
        if (closeBtn) {
          closeBtn.addEventListener('click', function () {
            if (timer) clearTimeout(timer);
            el.classList.add('wt-toast--hidden');
          });
        }
      }
      /* Auto-init toasts with data-auto-dismiss attribute */
      document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('.wt-toast[data-auto-dismiss]').forEach(function (el) {
          var delay = parseInt(el.getAttribute('data-auto-dismiss'), 10);
          wtToastInit(el, isNaN(delay) ? 5000 : delay);
        });
      });
      window.wtToastInit = wtToastInit;
    }());
    ```
  - [x] Verify no `console.log`/`console.debug` in the new code (NFR-S-1)

- [x] **Task 6: Add banner styles to `components.css`** (AC: #4)
  - [x] Append after the toast block:
    ```css
    /* ── Banner ─────────────────────────────────────── */
    .wt-banner {
      display: flex;
      align-items: flex-start;
      gap: var(--wt-space-3);
      padding: var(--wt-space-3) var(--wt-space-4);
      background-color: var(--wt-surface);
      border-left: 3px solid var(--wt-border-strong);
      border-radius: 0 var(--wt-radius-field) var(--wt-radius-field) 0;
      box-sizing: border-box;
    }
    .wt-banner__message {
      flex: 1;
      font-size: var(--wt-text-body-size);
      line-height: var(--wt-text-body-line);
      color: var(--wt-ink);
    }
    .wt-banner__action {
      display: inline;
      background: none;
      border: none;
      padding: 0;
      font-family: var(--wt-font-family), sans-serif;
      font-size: var(--wt-text-label-size);
      font-weight: var(--wt-text-label-weight);
      color: var(--wt-primary);
      text-decoration: underline;
      text-underline-offset: 2px;
      cursor: pointer;
    }
    .wt-banner__action:hover { color: var(--wt-primary-hover); }
    .wt-banner__action:focus-visible {
      outline: var(--wt-focus-width) solid var(--wt-focus-ring);
      outline-offset: var(--wt-focus-offset);
    }
    ```

- [x] **Task 7: Add skeleton-row styles to `components.css`** (AC: #5)
  - [x] Append after the banner block:
    ```css
    /* ── Skeleton Row ───────────────────────────────── */
    @keyframes wt-shimmer {
      0%   { background-position: -400px 0; }
      100% { background-position:  400px 0; }
    }
    .wt-skeleton-row {
      display: flex;
      align-items: center;
      gap: var(--wt-space-3);
      padding: var(--wt-space-3) 0;
      /* FTL must add: aria-hidden="true" */
    }
    .wt-skeleton-avatar {
      width: 40px;
      height: 40px;
      border-radius: 50%;
      background-color: var(--wt-border);
      background-image: linear-gradient(
        90deg,
        var(--wt-border)     0px,
        var(--wt-hover-tint) 80px,
        var(--wt-border)     160px
      );
      background-size: 800px 40px;
      animation: wt-shimmer 1.4s linear infinite;
      flex-shrink: 0;
    }
    .wt-skeleton-lines {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: var(--wt-space-2);
    }
    .wt-skeleton-line {
      height: 12px;
      border-radius: var(--wt-radius-field);
      background-color: var(--wt-border);
      background-image: linear-gradient(
        90deg,
        var(--wt-border)     0px,
        var(--wt-hover-tint) 80px,
        var(--wt-border)     160px
      );
      background-size: 800px 12px;
      animation: wt-shimmer 1.4s linear infinite;
    }
    .wt-skeleton-line--short { width: 60%; }
    @media (prefers-reduced-motion: reduce) {
      .wt-skeleton-avatar,
      .wt-skeleton-line {
        animation: none;
        background-image: none;
      }
    }
    ```

- [x] **Task 8: Add interstitial styles to `components.css`** (AC: #6)
  - [x] Append after the skeleton-row block:
    ```css
    /* ── Interstitial ───────────────────────────────── */
    /* wt-spin keyframe already defined above (from .wt-btn--submitting) — do NOT redefine */
    .wt-interstitial {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: var(--wt-space-4);
      padding: var(--wt-space-8) var(--wt-space-4);
      min-height: 200px;
      text-align: center;
    }
    .wt-interstitial__spinner {
      width: 32px;
      height: 32px;
      border: 3px solid var(--wt-border);
      border-top-color: var(--wt-primary);
      border-radius: 50%;
      animation: wt-spin 0.8s linear infinite;
    }
    .wt-interstitial__caption {
      font-size: var(--wt-text-body-size);
      line-height: var(--wt-text-body-line);
      color: var(--wt-muted);
    }
    .wt-interstitial__motion-text {
      display: none;
      font-size: var(--wt-text-body-size);
      line-height: var(--wt-text-body-line);
      color: var(--wt-muted);
    }
    @media (prefers-reduced-motion: reduce) {
      .wt-interstitial__spinner  { display: none; }
      .wt-interstitial__motion-text { display: block; }
    }
    ```
  - [x] **Critical:** `@keyframes wt-spin` is already in `components.css` (added in Story 1.3 for `.wt-btn--submitting`). Do NOT add a second definition — it already exists.

- [x] **Task 9: Build verification** (AC: #9, #10)
  - [x] Run `mvn package` in `azguards-keycloak-custom-theme` — must produce BUILD SUCCESS
  - [x] Grep audit: `grep -n "#[0-9a-fA-F]\{3,\}" src/main/resources/theme/azguards-whatsapp/login/resources/css/components.css` — check that any results are NOT new additions from this story (pre-existing `rgba(0,0,0,…)` in shadows are exempt; zero new hex literals introduced)
  - [x] Grep audit: `grep -n "console\." src/main/resources/theme/azguards-whatsapp/login/resources/js/script.js` — should return zero (NFR-S-1)
  - [x] Confirm `--wt-success` is used in toast block (not a hardcoded hex green)

## Dev Notes

### Working Repository Reminder

**ALL work in this story is in `azguards-keycloak-custom-theme`** (`~/WorkSpace/azguards-whatsapp/azguards-keycloak-custom-theme`). Do NOT touch the `keycloak-multi-tenancy` Java repo.

Files to modify (all in `src/main/resources/theme/azguards-whatsapp/login/`):

```
resources/css/
  tokens.css          ← MODIFY IF NEEDED (Task 1 — add missing tokens)
  components.css      ← MODIFY (Tasks 2–8 — all appended to existing file from Story 1.3)
resources/js/
  script.js           ← MODIFY (Task 5 — append toast IIFE to end of file)
```

**Do NOT modify any FTL templates** — no new CSS `<link>` tags needed (all 5 templates already link `components.css` from Story 1.3).

### What This Story Does NOT Do

- **Does NOT apply new classes to FTL templates** — just like Story 1.3, classes are forward-defined only; Epic 2+ applies them
- **Does NOT remove Bootstrap or Font Awesome** — Story 1.6 scope
- **Does NOT change any existing `.wt-*` classes from Story 1.3** — only adds new ones

### CRITICAL: `@keyframes wt-spin` Already Exists

Story 1.3 defined `@keyframes wt-spin { to { transform: rotate(360deg); } }` inside `components.css` for `.wt-btn--submitting`. The `.wt-interstitial__spinner` reuses this keyframe. **Do NOT add a second `@keyframes wt-spin` declaration** — the browser deduplicates them but it is a lint issue and signals a mis-read of existing code.

To verify before writing Task 8: `grep -n "wt-spin" src/main/resources/theme/azguards-whatsapp/login/resources/css/components.css` should show the existing definition.

### ARIA Requirements (FTL Responsibility — Not Settable by CSS)

When Epic 2+ applies these components in FTL templates, the following ARIA attributes are required:

| Component | Required HTML attributes |
|---|---|
| `.wt-toast` | `role="status"` and `aria-live="polite"` on the container; `data-auto-dismiss="5000"` for auto-init |
| `.wt-skeleton-row` | `aria-hidden="true"` on each row element |
| Skeleton region | `aria-busy="true"` on the `<ul>`/`<div role="list">` while loading; removed when real rows render |
| `.wt-field__error` | Field input gets `aria-invalid="true"` and `aria-describedby="{error-id}"`; the error element gets `id="{error-id}"` and `aria-live="polite"` |
| `.wt-interstitial` | `role="status"` or `aria-live="polite"` on the container so the reduced-motion text is announced |

### Inline-error Icon Fix (Task 2 Detail)

Story 1.3 code review (deferred-work.md) flagged:
> `.wt-field__error::before { content: '⚠' }` — renders as color emoji on macOS/iOS and as tofu on minimal font stacks; unreliably exposed to AT.

The fix `'\26A0\FE0E'` appends Unicode Variation Selector-15 (U+FE0E) which forces text presentation over emoji presentation. Combined with `speak: never`, the pseudo-element glyph is decorative and silent to AT — the error message text (`.wt-field__error` text content) carries the full meaning, which is already correctly announced via `aria-live="polite"` (FTL responsibility).

If, after testing, macOS/iOS still renders the emoji variant, the fallback is an inline SVG as a `background-image` data URI (non-breaking change — just swap the pseudo-element `content` for a `background-image: url("data:image/svg+xml,...")` approach). Do not escalate to this unless the `\FE0E` fix fails visually.

### Toast: Inline vs. Fixed Positioning

The toast is styled for **inline placement within the card** (`position: relative`). This is intentional for the Keycloak FTL context — FreeMarker templates render into a single card container with no layered stacking context. A fixed-position floating toast would require z-index management against Keycloak's own layout elements. When Epic 5 places the invite-acceptance toast, it sits inside the auth card below the auto-accepted content. This is the correct behavior.

### Toast JS Placement in `script.js`

Story 1.3 established that `script.js` contains selectors like `#submitBtn`, `button.proceed-button[type="submit"]`, `.accept-button`, `.reject-button`. The toast IIFE should be **appended to the END** of `script.js` — do not insert it mid-file. The IIFE is self-contained and does not conflict with existing selectors.

If `script.js` uses a `DOMContentLoaded` listener at the top level (outside a wrapper), confirm the toast IIFE's own `DOMContentLoaded` listener does not duplicate it — the IIFE listener is separate and additive; multiple `DOMContentLoaded` listeners are allowed.

### Token Verification Reference (from DESIGN.md + Story 1.3)

Expected values — verify against `tokens.css` before use. Do NOT hardcode; always use `var(--wt-*)`.

| Token | Light | Dark | Notes |
|---|---|---|---|
| `--wt-success` | `#16A34A` | `#34D399` | Add if missing (Task 1) |
| `--wt-success-text` | `#15803D` | `#6EE7B7` | Add if missing (Task 1) |
| `--wt-radius-card` | `12px` | (same) | Add if missing — only `--wt-radius-card-lg: 16px` confirmed in 1.3 |
| `--wt-border-strong` | `#94A3B8` | `#47655E` | Confirmed present (Story 1.3) |
| `--wt-border` | `#E2E8F0` | `#1F2A44` | Confirmed present |
| `--wt-surface` | `#FFFFFF` | `#111A2E` | Confirmed present |
| `--wt-muted` | `#64748B` | `#94A3B8` | Confirmed present |
| `--wt-hover-tint` | `#F8FAFC` | `#0F1A30` | Confirmed present |
| `--wt-shadow-card` | `0 4px 6px rgba(0,0,0,0.04)` | `0 4px 16px rgba(0,0,0,0.4)` | Confirmed present |
| `--wt-space-1..8` | `4px`..`32px` | (same) | Confirmed present |
| `--wt-text-body-size` | `15px` | (same) | Confirmed present |
| `--wt-text-label-size` | `14px` | (same) | Confirmed present |
| `--wt-text-caption-size` | `12px` | (same) | Confirmed present |
| `--wt-focus-width` | `2px` | (same) | Confirmed present |
| `--wt-focus-offset` | `2px` | (same) | Confirmed present |
| `--wt-focus-ring` | `#0F766E` | `#2DD4BF` | Confirmed present |
| `--wt-primary` | `#0F766E` | `#0F766E` | Confirmed present |
| `--wt-primary-hover` | `#115E54` | `#0B5F58` | Confirmed present |
| `--wt-radius-field` | `10px` | (same) | Confirmed present |
| `--wt-duration` | `160ms` | (same) | Confirmed present |
| `--wt-easing` | `ease` | (same) | Confirmed present |
| `--wt-font-family` | (system stack) | (same) | Confirmed present |
| `--wt-ink` | `#0F172A` | `#E6EDF7` | Confirmed present |

### Deferred Items from Story 1.3 NOT in Scope Here

The following remain deferred — do NOT address in this story:
- **Bootstrap utility classes not token-aware in dark mode** — Story 1.6
- **Password-strength labels using Bootstrap classes** — update-password polish
- **`--wt-info` equals `--wt-primary`** — token-palette review
- **`updatePassword.css` generic class names** — namespacing follow-up
- **`--wt-danger-border` inconsistency** — token-consistency cleanup
- **Jenkinsfile CodeArtifact / AWS CLI issues** — separate CI PR
- **`.wt-btn--submitting` display:block + inline-flex mixing** — low risk, out of scope

### Build System

```bash
# From ~/WorkSpace/azguards-whatsapp/azguards-keycloak-custom-theme
mvn package
# JAR in target/azguards-keycloak-custom-theme-1.0.15.jar (version may have incremented)
# No automated tests in this repo — verification is visual + grep audit
```

### Previous Story Intelligence (Story 1.3 Key Learnings)

- `components.css` is at `src/main/resources/theme/azguards-whatsapp/login/resources/css/components.css` — file already exists, append to it
- `@keyframes wt-spin` is already defined — do NOT redefine
- The five FTL templates already link `components.css` — no link changes needed
- `--pf-v5-c-login__container--MaxWidth` in `selectTenant.css` is a PatternFly variable — leave untouched
- `--error-container-height: 96px` in `reviewTenant.css` is read by `script.js:274` — do not remove
- `mvn package` produces `target/azguards-keycloak-custom-theme-*.jar` — BUILD SUCCESS is the baseline
- Zero hardcoded hex rule: all new CSS must use only `var(--wt-*)` or `color-mix()`
- All new CSS blocks follow the `.wt-` namespace convention

### References

- Inline-error spec: [Source: `_bmad-output/planning-artifacts/ux-designs/ux-keycloak-multi-tenancy-2026-06-11/DESIGN.md` — Components: Inline error]
- Method-row spec: [Source: DESIGN.md — Components: Method row]
- Toast spec: [Source: DESIGN.md — Components: Toast]
- Banner spec: [Source: DESIGN.md — Components: Banner]
- Skeleton-row spec: [Source: DESIGN.md — Components: Skeleton row]
- Interstitial spec: [Source: DESIGN.md — Components: Interstitial]
- UX-DR7: [Source: `_bmad-output/planning-artifacts/epics.md` — UX Design Requirements: UX-DR7 inline-error]
- UX-DR8: [Source: epics.md — UX-DR8 method-row]
- UX-DR9: [Source: epics.md — UX-DR9 toast]
- UX-DR10: [Source: epics.md — UX-DR10 banner]
- UX-DR11: [Source: epics.md — UX-DR11 skeleton-row]
- UX-DR12: [Source: epics.md — UX-DR12 interstitial]
- NFR-A-1..6, FR-L-11: [Source: epics.md — Requirements Inventory]
- Deferred icon/token issues: [Source: `_bmad-output/implementation-artifacts/deferred-work.md` — Deferred from story-1.3]
- Token values + component patterns: [Source: `_bmad-output/implementation-artifacts/1-3-core-form-component-styles.md` — Dev Notes]
- Colors (light/dark), success tokens: [Source: DESIGN.md YAML front matter]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

None.

### Completion Notes List

- Task 1: All tokens already present (`--wt-success`, `--wt-success-text` light+dark, `--wt-radius-card: 12px`) — no changes needed to tokens.css.
- Task 2: Fixed `.wt-field__error::before` icon from emoji `'⚠'` to text-variant `'\26A0\FE0E'` + `speak: never`. `.wt-field__error` display logic unchanged.
- Tasks 3–8: Appended method-row, toast, banner, skeleton-row, and interstitial CSS blocks to `components.css`. `@keyframes wt-spin` reused from Story 1.3 (not redefined). `@keyframes wt-shimmer` added once.
- Task 5: Toast IIFE appended to end of `script.js` exposing `window.wtToastInit`. Auto-init wires any `.wt-toast[data-auto-dismiss]` elements on DOMContentLoaded.
- Task 9: `mvn package` = BUILD SUCCESS. Zero new hardcoded hex in `components.css`. No `console.*` in new JS code. `--wt-success` used as token in toast block.

### File List

- `src/main/resources/theme/azguards-whatsapp/login/resources/css/components.css` (modified — Task 2: fixed inline-error icon; Tasks 3–8: appended method-row, toast, banner, skeleton-row, interstitial styles)
- `src/main/resources/theme/azguards-whatsapp/login/resources/js/script.js` (modified — Task 5: appended toast IIFE)

## Change Log

- Implemented Story 1.4 feedback & list component styles: fixed inline-error icon, added method-row, toast (+ JS dismiss), banner, skeleton-row, and interstitial CSS components to `components.css`; appended `wtToastInit` IIFE to `script.js`. All tokens token-only (no new hardcoded hex). BUILD SUCCESS. (Date: 2026-06-12)
