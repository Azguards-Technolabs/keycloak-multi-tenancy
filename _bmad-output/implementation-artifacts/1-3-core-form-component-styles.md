---
baseline_commit: d435581d03c6479160ed85aea7464840a4874a39
---

# Story 1.3: Core Form Component Styles

Status: done

## Story

As a front-end engineer,
I want reusable button, text-field, and auth-card styles built on the tokens,
so that every auth screen shares one accessible, on-brand component vocabulary.

> **Working Repository:** `azguards-keycloak-custom-theme` (`~/WorkSpace/azguards-whatsapp/azguards-keycloak-custom-theme`)
> Pure CSS story — no Java SPI changes, no `keycloak-multi-tenancy` work.

## Acceptance Criteria

1. **Button component styled:** `.wt-btn--primary` is full-width, 46px tall, radius `10px` (`var(--wt-radius-field)`), `var(--wt-primary)` bg + white text, `var(--wt-primary-hover)` on hover, `160ms ease` transition. Focus: `2px var(--wt-focus-ring)` outline with `2px` offset. A `.wt-btn--submitting` modifier adds an inline CSS-only spinner after the label; together with `:disabled` it sets `opacity: 0.5` and blocks pointer events. (UX-DR2, NFR-A-2, FR-L-11)

2. **Ghost button variant:** `.wt-btn--ghost` is full-width, same height/radius, transparent bg, `1px var(--wt-border)` border, `var(--wt-ink)` text, `var(--wt-hover-tint)` fill on hover. Same focus ring as primary. (UX-DR2)

3. **Text-field component styled:** `.wt-field` is a wrapper `div` containing `label.wt-field__label` (14px/500) and `input.wt-field__input` (46px tall, radius `10px`, `1px var(--wt-border)` border, `var(--wt-surface)` bg). Focus on input raises border to `var(--wt-primary)` + 2px focus ring. `.wt-field--error` modifier applies `var(--wt-danger)` border, `var(--wt-danger-bg)` fill, and makes `.wt-field__error` (inline message with `⚠` icon + `var(--wt-danger-text)`) visible beneath the input. (UX-DR3, NFR-A-3, NFR-A-4)

4. **Auth-card component styled:** `.wt-card` centers in the viewport on `var(--wt-bg)`, max-width `400px`, `var(--wt-surface)` bg, radius `16px` (`var(--wt-radius-card-lg)`), `1px var(--wt-border)` hairline, `var(--wt-shadow-card)` soft shadow. On screens ≥ 320px the card fills available width with `16px` horizontal gutters. (UX-DR4, NFR-B-2)

5. **Touch targets ≥ 44×44px:** All interactive elements in the new components have a minimum hit area of `44×44px`. Button height is `46px` (satisfies height); full-width buttons satisfy width. Text-field input height `46px` satisfies both. (FR-L-11)

6. **Deferred 1.2 fixes in scope — surface-tint tokens:** Add `--wt-surface-tint-1` and `--wt-surface-tint-2` to `tokens.css` (light + dark variants). Update `style.css` `.background-overlay` and `selectTenant.css` gradient to use these tokens, restoring the layered teal-tinted depth that was flattened in Story 1.2.

7. **Deferred 1.2 fixes in scope — focus ring token:** Update `style.css` `.form-control:focus` to replace the hardcoded `rgba(15, 118, 110, 0.25)` box-shadow with the token-based equivalent using `color-mix(in srgb, var(--wt-focus-ring) 25%, transparent)`.

8. **Deferred 1.2 fixes in scope — shadow token:** Update `.card` in `style.css` to use `var(--wt-shadow-card)` instead of the literal `rgba(0, 0, 0, 0.1)` shadow.

9. **`components.css` linked in all FTL templates:** All five in-scope FTL templates (`login.ftl`, `login-reset-password.ftl`, `login-update-password.ftl`, `select-tenant.ftl`, `review-invitations.ftl`) include `<link rel="stylesheet" href="${url.resourcesPath}/css/components.css">` in the correct load order: Bootstrap (if present) → `tokens.css` → `components.css` → screen-specific CSS.

10. **`mvn package` passes:** The theme JAR builds without errors after these changes.

## Tasks / Subtasks

- [x] **Task 1: Add surface-tint + focus-ring-halo tokens to `tokens.css`** (AC: #6, #7)
  - [x] In `:root` (light mode), add after existing color tokens:
    ```css
    --wt-surface-tint-1: #E6F2EE;   /* teal-tinted overlay band, mint warm */
    --wt-surface-tint-2: #D7EFC9;   /* deeper teal-green tint, gradient stop */
    ```
  - [x] In `@media (prefers-color-scheme: dark) :root` and `[data-theme="dark"]`, add:
    ```css
    --wt-surface-tint-1: #0D2921;
    --wt-surface-tint-2: #0A2218;
    ```
  - [x] Add `--wt-focus-ring-halo` token for the semi-transparent focus-ring shadow:
    - Light: `color-mix(in srgb, var(--wt-focus-ring) 25%, transparent)`
    - Dark (inside both dark blocks): same formula — `color-mix` auto-uses the lifted `--wt-focus-ring` (#2DD4BF) in dark mode
    - Token definition in `:root`: `--wt-focus-ring-halo: color-mix(in srgb, var(--wt-focus-ring) 25%, transparent);`
    - No dark-mode override needed — `color-mix` is dynamic; it references the already-overridden `--wt-focus-ring`

- [x] **Task 2: Fix deferred style issues in `style.css`** (AC: #7, #8)
  - [x] Line ~13 (`.background-overlay`): change `background-color: var(--wt-bg)` → `background-color: var(--wt-surface-tint-1)`
  - [x] Line ~34 (`.card` box-shadow): change `box-shadow: 0 10px 25px rgba(0, 0, 0, 0.1)` → `box-shadow: var(--wt-shadow-card)` (uses the token defined in tokens.css)
  - [x] Line ~134 (`.form-control:focus` box-shadow): change `box-shadow: 0 0 0 0.2rem rgba(15, 118, 110, 0.25)` → `box-shadow: 0 0 0 var(--wt-focus-width) var(--wt-focus-ring-halo)`

- [x] **Task 3: Fix deferred gradient in `selectTenant.css`** (AC: #6)
  - [x] Find the mobile header band that was flattened to `var(--wt-bg)` (search for `.pf-v5-c-login__main::before` or the two-stop gradient section near line 250 in the pre-1.2 file)
  - [x] Restore the gradient using new tokens: `linear-gradient(180deg, var(--wt-surface-tint-1), var(--wt-surface-tint-2))`
  - [x] Check if `.tenant-card` background should be `var(--wt-surface-tint-1)` instead of `var(--wt-bg)` (it was `#e3e9dd` — maps closer to tint-2 but tint-1 is acceptable)
  - [x] Verify: no new hardcoded hex introduced

- [x] **Task 4: Create `components.css`** (AC: #1, #2, #3, #4, #5)
  - [x] Create `src/main/resources/theme/azguards-whatsapp/login/resources/css/components.css`
  - [x] **Button base (`.wt-btn`):**
    ```css
    .wt-btn {
      display: block;
      width: 100%;
      height: var(--wt-field-height);            /* 46px — satisfies FR-L-11 44px minimum */
      padding: 0 var(--wt-space-4);
      border-radius: var(--wt-radius-field);     /* 10px */
      font-family: var(--wt-font-family), sans-serif;
      font-size: var(--wt-text-label-size);      /* 14px */
      font-weight: var(--wt-text-label-weight);  /* 500 */
      line-height: var(--wt-text-label-line);
      text-align: center;
      cursor: pointer;
      border: none;
      transition: background-color var(--wt-duration) var(--wt-easing),
                  border-color var(--wt-duration) var(--wt-easing),
                  color var(--wt-duration) var(--wt-easing);
      text-decoration: none;
    }
    .wt-btn:focus-visible {
      outline: var(--wt-focus-width) solid var(--wt-focus-ring);
      outline-offset: var(--wt-focus-offset);
    }
    .wt-btn:disabled,
    .wt-btn--submitting {
      opacity: 0.5;
      cursor: not-allowed;
      pointer-events: none;
    }
    ```
  - [x] **Primary button (`.wt-btn--primary`):**
    ```css
    .wt-btn--primary {
      background-color: var(--wt-primary);
      color: var(--wt-on-primary);
      border: 1px solid var(--wt-primary);
    }
    .wt-btn--primary:hover:not(:disabled):not(.wt-btn--submitting) {
      background-color: var(--wt-primary-hover);
      border-color: var(--wt-primary-hover);
    }
    ```
  - [x] **Ghost button (`.wt-btn--ghost`):**
    ```css
    .wt-btn--ghost {
      background-color: transparent;
      color: var(--wt-ink);
      border: 1px solid var(--wt-border);
    }
    .wt-btn--ghost:hover:not(:disabled) {
      background-color: var(--wt-hover-tint);
      border-color: var(--wt-border-strong);
    }
    ```
  - [x] **Submitting/spinner state (`.wt-btn--submitting`):**
    ```css
    .wt-btn--submitting {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: var(--wt-space-2);
    }
    .wt-btn--submitting::after {
      content: '';
      display: inline-block;
      width: 14px;
      height: 14px;
      border: 2px solid transparent;
      border-top-color: currentColor;
      border-radius: 50%;
      animation: wt-spin var(--wt-duration) linear infinite;
      flex-shrink: 0;
    }
    @keyframes wt-spin { to { transform: rotate(360deg); } }
    @media (prefers-reduced-motion: reduce) {
      .wt-btn--submitting::after { animation: none; border-top-color: transparent; border-right-color: currentColor; }
    }
    ```
  - [x] **Text-field wrapper (`.wt-field`):**
    ```css
    .wt-field {
      display: flex;
      flex-direction: column;
      gap: var(--wt-space-1);
    }
    .wt-field__label {
      font-size: var(--wt-text-label-size);
      font-weight: var(--wt-text-label-weight);
      line-height: var(--wt-text-label-line);
      color: var(--wt-ink);
    }
    .wt-field__input {
      height: var(--wt-field-height);            /* 46px */
      padding: 0 var(--wt-space-3);
      border-radius: var(--wt-radius-field);     /* 10px */
      border: 1px solid var(--wt-border-strong);
      background-color: var(--wt-surface);
      color: var(--wt-ink);
      font-family: var(--wt-font-family), sans-serif;
      font-size: var(--wt-text-body-size);
      line-height: var(--wt-text-body-line);
      transition: border-color var(--wt-duration) var(--wt-easing),
                  background-color var(--wt-duration) var(--wt-easing);
      width: 100%;
      box-sizing: border-box;
    }
    .wt-field__input::placeholder { color: var(--wt-muted); }
    .wt-field__input:focus {
      outline: none;
      border-color: var(--wt-primary);
      box-shadow: 0 0 0 var(--wt-focus-width) var(--wt-focus-ring-halo);
    }
    ```
  - [x] **Text-field error state (`.wt-field--error`):**
    ```css
    .wt-field--error .wt-field__input {
      border-color: var(--wt-danger);
      background-color: var(--wt-danger-bg);
    }
    .wt-field--error .wt-field__input:focus {
      border-color: var(--wt-danger);
      box-shadow: 0 0 0 var(--wt-focus-width) color-mix(in srgb, var(--wt-danger) 25%, transparent);
    }
    .wt-field__error {
      display: none;
      align-items: center;
      gap: var(--wt-space-1);
      font-size: var(--wt-text-caption-size);    /* 12px */
      font-weight: var(--wt-text-label-weight);
      color: var(--wt-danger-text);
    }
    .wt-field__error::before {
      content: '⚠';
      flex-shrink: 0;
      color: var(--wt-danger);
      font-size: 13px;
    }
    .wt-field--error .wt-field__error { display: flex; }
    ```
    > Note: `aria-invalid="true"` and `aria-describedby` linking the error message must be set by the FTL template or JS — CSS alone cannot set ARIA attributes.
  - [x] **Auth card (`.wt-card`):**
    ```css
    .wt-card {
      background-color: var(--wt-surface);
      border-radius: var(--wt-radius-card-lg);   /* 16px */
      border: 1px solid var(--wt-border);
      box-shadow: var(--wt-shadow-card);
      max-width: var(--wt-card-max-width);        /* 400px */
      width: calc(100% - var(--wt-space-8));      /* 100% - 32px = 16px gutter each side */
      margin: 0 auto;
      padding: var(--wt-space-8);                 /* 32px internal padding */
      box-sizing: border-box;
    }
    @media (min-width: 432px) {                    /* 400px card + 32px gutters */
      .wt-card { width: var(--wt-card-max-width); }
    }
    ```

- [x] **Task 5: Link `components.css` in all FTL templates** (AC: #9)
  - [x] `login.ftl`: add `<link rel="stylesheet" href="${url.resourcesPath}/css/components.css">` after `tokens.css`, before `style.css`
  - [x] `login-reset-password.ftl`: same insertion after `tokens.css`
  - [x] `login-update-password.ftl`: same insertion after `tokens.css`, before `updatePassword.css`
  - [x] `select-tenant.ftl`: same insertion after `tokens.css`, before `selectTenant.css`
  - [x] `review-invitations.ftl`: same insertion after `tokens.css`, before `reviewTenant.css`
  - [x] Verify final load order in every template: Bootstrap (if present) → Font Awesome (if present) → `tokens.css` → `components.css` → screen-specific CSS

- [x] **Task 6: Build verification** (AC: #10)
  - [x] Run `mvn package` in `azguards-keycloak-custom-theme` — must produce BUILD SUCCESS
  - [x] Grep audit: `grep -r "rgba(15" src/main/resources/theme/azguards-whatsapp/login/resources/css/` — should return zero results (focus-ring fix verified)
  - [x] Grep audit: `grep -r "rgba(0, 0, 0" src/main/resources/theme/azguards-whatsapp/login/resources/css/` — `.background-overlay` inset shadow is excluded if it remains (that is a structural shadow, not a card shadow — see Do NOT section)
  - [x] Grep audit: zero new hardcoded hex in `components.css` (all values use `var(--wt-*)` tokens)
  - [x] Confirm all 5 FTL templates link `components.css` with correct order

### Review Findings

> Code review 2026-06-12 (adversarial: Blind Hunter + Edge Case Hunter + Acceptance Auditor).
> **Baseline note:** review baseline `d435581` predates stories 1.1/1.2/1.3 (HEAD == baseline; all work uncommitted), so the diff conflated three stories. Findings in `reviewTenant.css`, `Jenkinsfile`, and bulk `tokens.css`/`selectTenant.css` are largely earlier-story work and were re-scoped (deferred) accordingly. Acceptance Auditor confirmed AC1, AC2, AC3, AC5, AC6, AC7, AC8, AC9 PASS with verified token values.

- [x] [Review][Decision→Patch] AC4 partial — auth-card page background / viewport centering — **Resolved: option (a).** Added `body { background-color: var(--wt-bg); color: var(--wt-ink); }` to style.css — fixes the dark-mode white-gap and satisfies AC4 intent.
- [x] [Review][Decision→Patch] login-update-password.ftl refactor scope — **Resolved: option (a) keep + harden.** Refactor accepted; added null-guard on `password-new`/`password-confirm` in the `DOMContentLoaded` handler, and rendered server-side `messagesPerField` password errors into the validation-error container on load.
- [x] [Review][Patch] `.wt-btn--submitting` reduced-motion now shows a complete static ring (was a half-drawn arc) [components.css] — under `prefers-reduced-motion: reduce` set `border-color: currentColor; opacity: 0.6`.
- [x] [Review][Defer] Jenkinsfile: CodeArtifact auth token written to workspace `settings.xml`, removed only in `post.always` [Jenkinsfile] — token sits in plaintext for the full `mvn deploy`; leaks if build aborts hard or workspace is archived. — deferred, out of CSS-story scope (separate PR)
- [x] [Review][Defer] Jenkinsfile: AWS CLI `${HOME}/.local` PATH prepend vs conditional install is non-deterministic across cold/cached agents [Jenkinsfile] — deferred, out of scope
- [x] [Review][Defer] Bootstrap utility classes not token-aware in dark mode (`bg-light`, `alert-*`, `text-muted`, `btn-outline-secondary`) — deferred, Bootstrap cleanup is Story 1.6 scope (Dev Notes line 276)
- [x] [Review][Defer] Password-strength label uses Bootstrap `text-success`/`text-danger` not tokens [login-update-password.ftl] — diverges from palette in dark mode — deferred, update-password polish
- [x] [Review][Defer] `--wt-info` equals `--wt-primary` (#0F766E) [tokens.css] — strength "strong" visually indistinct from brand primary — deferred, token-palette review
- [x] [Review][Defer] `updatePassword.css` uses un-namespaced generic classes (`.requirement`, `.strength-fill`, `.validation-error`) vs `.wt-` convention — cross-page bleed risk — deferred, refactor follow-up
- [x] [Review][Defer] `--wt-danger-border` used inconsistently (ignored by `.wt-field--error`, used by `.validation-error`) — partly dead token [components.css/updatePassword.css] — deferred, token consistency
- [x] [Review][Defer] `.wt-field__error::before { content: '⚠' }` — emoji/tofu/AT-exposure risk for the sole error glyph [components.css] — deferred, component polish (class unused yet)
- [x] [Review][Defer] login-reset-password.ftl drops font-awesome while sibling password screens keep an icon font [login-reset-password.ftl] — inconsistent icon availability — deferred
- [x] [Review][Defer] `.wt-btn--submitting` mixes `display:block` (base) + `inline-flex` (modifier) with `width:100%` [components.css] — odd inline-level full-width box — deferred, low risk
- [x] [Review][Defer] `reviewTenant.css`/`selectTenant.css` retain literal `rgba(0,0,0,…)` box-shadows (Task-6 zero-hardcoded intent) — deferred, mostly pre-1.3 / structural shadows

## Dev Notes

### Working Repository Reminder

**ALL work in this story is in `azguards-keycloak-custom-theme`** (`~/WorkSpace/azguards-whatsapp/azguards-keycloak-custom-theme`). Do NOT touch the `keycloak-multi-tenancy` Java repo.

Theme resource root:
```
src/main/resources/theme/azguards-whatsapp/login/
├── resources/
│   └── css/
│       ├── tokens.css          ← MODIFY (Task 1 — add tokens)
│       ├── style.css           ← MODIFY (Task 2 — fix deferred items)
│       ├── selectTenant.css    ← MODIFY (Task 3 — restore gradient)
│       ├── reviewTenant.css    ← DO NOT TOUCH (no deferred items)
│       ├── updatePassword.css  ← DO NOT TOUCH
│       └── components.css      ← CREATE (Task 4)
├── login.ftl                   ← MODIFY head (Task 5)
├── login-reset-password.ftl    ← MODIFY head (Task 5)
├── login-update-password.ftl   ← MODIFY head (Task 5)
├── select-tenant.ftl           ← MODIFY head (Task 5)
└── review-invitations.ftl      ← MODIFY head (Task 5)
```

### What This Story Does NOT Do

- **Does NOT apply new component classes to existing FTL templates.** `login.ftl` still uses `.btn.btn-primary`, `.form-control`, `.card` (Bootstrap classes). These will be replaced in Epic 2 when screens are redesigned. This story only DEFINES the component classes for future use.
- **Does NOT add `[data-theme="light"]` escape hatch** — deferred to a future theming-toggle enhancement.
- **Does NOT add the `font-family` `, sans-serif` fallback** — the token `--wt-font-family` already embeds `Arial, sans-serif` at the end of the stack; it has inherent fallback. Low risk — deferred.
- **Does NOT modify the `.background-overlay` inset structural shadow** (`inset 0px -2px 4px rgba(0, 0, 0, 0.2)`) — this is a structural depth shadow, distinct from the card shadow tokens. Leave as-is.
- **Does NOT remove Bootstrap or Font Awesome** — Bootstrap cleanup is Story 1.6 scope.
- **Does NOT touch `reviewTenant.css`** — no deferred items from 1.2 belong to its scope for this story.

### Why New Component Classes (Not Refactoring Bootstrap Overrides)

Current screens use `.btn-primary`, `.form-control`, `.card` (Bootstrap classes overridden in `style.css`). The design system approach defines `.wt-btn--primary`, `.wt-field`, `.wt-card` as new classes that Epic 2+ will apply when redesigning screens. Building on top of Bootstrap's existing overrides would make the component vocabulary tightly coupled to Bootstrap. The new `.wt-*` classes are standalone and Bootstrap-free — Epic 2 will replace Bootstrap usage screen by screen.

### Existing Class Names to Preserve (Regression Prevention)

The following classes are used by FTL templates and must continue to work:
- `login.ftl`: `.btn.btn-primary.w-100.rounded-pill` (submit button), `.btn.btn-onboard.w-100.rounded-pill` (ghost/onboard), `.form-control` (username + password inputs), `.card` (auth card wrapper), `#submitBtn` (spinner JS targets this ID)
- `select-tenant.ftl`: `.tenant-card`, `.tenant-logo`, `.tenant-info`, `--pf-v5-c-login__container--MaxWidth` (PatternFly var, leave it)
- `review-invitations.ftl`: `.proceed-button`, `.accept-button`, `.reject-button`, `--error-container-height` (still needed by `script.js:274`)
- `script.js`: targets `#submitBtn`, `button.proceed-button[type="submit"]`, `.accept-button`, `.reject-button` — do NOT rename these selectors

### CSS Custom Property `color-mix()` Compatibility

`color-mix(in srgb, ...)` is supported in all evergreen browsers as of 2023 (Chrome 111+, Firefox 113+, Safari 16.2+). Keycloak 26.4.x is a server-side product; users access it via any modern browser. No polyfill needed for this project's context.

### Token Values Reference (from `tokens.css` after Story 1.2)

For reference when implementing the component CSS — do NOT hardcode these; always use `var(--wt-*)`:

| Token | Light value | Dark value |
|---|---|---|
| `--wt-primary` | `#0F766E` | `#0F766E` |
| `--wt-primary-hover` | `#115E54` | `#0B5F58` |
| `--wt-on-primary` | `#FFFFFF` | `#FFFFFF` |
| `--wt-danger` | `#DC2626` | `#F87171` |
| `--wt-danger-text` | `#B91C1C` | `#FCA5A5` |
| `--wt-danger-bg` | `#FEF2F2` | `#2A1416` |
| `--wt-focus-ring` | `#0F766E` | `#2DD4BF` |
| `--wt-focus-width` | `2px` | (same) |
| `--wt-focus-offset` | `2px` | (same) |
| `--wt-border` | `#E2E8F0` | `#1F2A44` |
| `--wt-border-strong` | `#94A3B8` | `#47655E` |
| `--wt-surface` | `#FFFFFF` | `#111A2E` |
| `--wt-shadow-card` | `0 4px 6px rgba(0,0,0,0.04)` | `0 4px 16px rgba(0,0,0,0.4)` |
| `--wt-field-height` | `46px` | (same) |
| `--wt-card-max-width` | `400px` | (same) |
| `--wt-radius-field` | `10px` | (same) |
| `--wt-radius-card-lg` | `16px` | (same) |
| `--wt-hover-tint` | `#F8FAFC` | `#0F1A30` |

**New tokens to add (Task 1):**
| Token | Light | Dark |
|---|---|---|
| `--wt-surface-tint-1` | `#E6F2EE` | `#0D2921` |
| `--wt-surface-tint-2` | `#D7EFC9` | `#0A2218` |
| `--wt-focus-ring-halo` | `color-mix(in srgb, var(--wt-focus-ring) 25%, transparent)` | (inherits, no override needed — color-mix is dynamic) |

### Style.css Current State (Post Story 1.2)

Lines relevant to this story's fixes:

```css
/* Line ~13: background-overlay — FIX background-color to --wt-surface-tint-1 */
.background-overlay {
  position: fixed; top: 0; left: 0;
  width: 100%; height: 50%;
  background-color: var(--wt-bg);       /* ← CHANGE to var(--wt-surface-tint-1) */
  z-index: -1;
  box-shadow: inset 0px -2px 4px rgba(0, 0, 0, 0.2);   /* ← DO NOT TOUCH — structural */
}

/* Line ~34: .card — FIX box-shadow to use token */
.card {
  margin: auto; padding: 20px;
  border-radius: 12px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.1);  /* ← CHANGE to var(--wt-shadow-card) */
  background-color: var(--wt-surface);
  border: none;
}

/* Line ~134: .form-control:focus — FIX rgba to token-based */
.form-control:focus {
  border-color: var(--wt-primary) !important;
  box-shadow: 0 0 0 0.2rem rgba(15, 118, 110, 0.25);  /* ← CHANGE: see below */
}
/* Target: box-shadow: 0 0 0 var(--wt-focus-width) var(--wt-focus-ring-halo); */
```

### SelectTenant.css — Gradient Restoration (Task 3)

The Story 1.2 tokenization replaced:
```css
/* OLD gradient (two-stop teal→green band) */
linear-gradient(180deg, #e0f0ee, #d7efc9)
```
with a flat `var(--wt-bg)`. Restore using:
```css
background: linear-gradient(180deg, var(--wt-surface-tint-1), var(--wt-surface-tint-2));
```

Find it by grepping for the selector that held the gradient (likely `.pf-v5-c-login__main::before` or a mobile-specific media query block near line 250 in the original file — current line number may differ post-1.2 edit).

Also check `.tenant-card` background — was `#e3e9dd` (a light teal tint), mapped to `var(--wt-bg)` in 1.2. Consider updating to `var(--wt-surface-tint-1)` for visual distinction between card and page. Only change if it was previously `--wt-bg` as a result of the 1.2 migration; don't break existing working visual.

### Build System for Theme Repo

```bash
# From ~/WorkSpace/azguards-whatsapp/azguards-keycloak-custom-theme
mvn package
# JAR lands in target/ — deploy to KC's providers/ and run kc.sh build
# No automated tests in this repo — verification is visual + grep audit
```

Current JAR version (post-1.2): `azguards-keycloak-custom-theme-1.0.15.jar`

### FTL Template Load Order (Target State After This Story)

```html
<!-- login.ftl example — all 5 templates follow this pattern -->
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css">
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.7.0/css/font-awesome.min.css">
<link rel="stylesheet" href="${url.resourcesPath}/css/tokens.css">
<link rel="stylesheet" href="${url.resourcesPath}/css/components.css">   <!-- ADD THIS -->
<link rel="stylesheet" href="${url.resourcesPath}/css/style.css">
```
> Note: `select-tenant.ftl` and `review-invitations.ftl` may not have Bootstrap; just ensure `tokens.css` → `components.css` → their screen CSS.

### Previous Story Intelligence (Stories 1.1, 1.2)

- **Story 1.1** was Java-only (`keycloak-multi-tenancy` repo) — no CSS learnings.
- **Story 1.2** established `tokens.css` with the full `--wt-*` token set, and confirmed:
  - `mvn package` produces a JAR in `target/` — JAR filename is `azguards-keycloak-custom-theme-1.0.15.jar`
  - Zero hardcoded hex rule: all new CSS must use only `var(--wt-*)` or `color-mix()`/`rgba()` applied to tokens
  - `--pf-v5-c-login__container--MaxWidth` in `selectTenant.css` is a PatternFly 5 variable — leave it unchanged
  - `--error-container-height: 96px` in `reviewTenant.css` is read by `script.js:274` — must not be removed
  - The theme in KC admin is registered as `azguards-whatsapp` under the login theme slot
  - `tokens.css` is now linked in all 5 FTL templates before screen-specific CSS (confirmed by grep)
  - Story 1.2 code review found 4 patches (all applied): `--error-container-height` regression, accepted-card color semantics, disabled button contrast, reject-button label contrast
  - `--wt-warning` and `--wt-info` were added to `tokens.css` for password-strength states — they are intentional; leave them

### Deferred Items NOT in Scope for This Story (Pass to 1.4/1.5)

- `font-family` fallback — low risk, punt to opportunistic fix in 1.4 or 1.5
- `[data-theme="light"]` escape hatch — future theming-toggle enhancement
- Dark-mode focus ring on `.background-overlay` inset shadow — structural shadow, not theme-switched

### Project Context

- Project: keycloak-multi-tenancy / WhataTalk login & onboarding screens
- Theme name registered in Keycloak: `azguards-whatsapp`
- KC version: 26.4.6
- These component classes (`.wt-btn`, `.wt-field`, `.wt-card`) are the design vocabulary for Epic 2+ screen redesigns. No FTL template uses them yet — they are forward-defined components.

### References

- Button spec: [Source: `_bmad-output/planning-artifacts/ux-designs/ux-keycloak-multi-tenancy-2026-06-11/DESIGN.md` — Components: Button]
- Text-field spec: [Source: DESIGN.md — Components: Text field]
- Auth-card spec: [Source: DESIGN.md — Components: Auth card]
- Layout/spacing: [Source: DESIGN.md — Layout & Spacing]
- UX-DR2/3/4/13: [Source: `_bmad-output/planning-artifacts/epics.md` — UX Design Requirements]
- NFR-A-2 (focus rings), NFR-A-3/4 (error ARIA), NFR-T-1 (no hardcoded hex), NFR-B-2 (mobile ≥320px): [Source: epics.md — Requirements Inventory]
- FR-L-11 (44×44px touch targets): [Source: epics.md]
- Story 1.3 AC: [Source: epics.md — Epic 1 Story 1.3]
- Surface tint deferred: [Source: `_bmad-output/implementation-artifacts/deferred-work.md`]
- Focus ring rgba deferred: [Source: `_bmad-output/implementation-artifacts/deferred-work.md`]
- Shadow token deferred: [Source: `_bmad-output/implementation-artifacts/deferred-work.md`]
- Story 1.2 learnings: [Source: `_bmad-output/implementation-artifacts/1-2-shared-css-design-token-theme-light-dark.md`]
- Current `style.css` (post-1.2): `src/main/resources/theme/azguards-whatsapp/login/resources/css/style.css`
- Current `tokens.css` (post-1.2): `src/main/resources/theme/azguards-whatsapp/login/resources/css/tokens.css`
- Current `selectTenant.css` (post-1.2): `src/main/resources/theme/azguards-whatsapp/login/resources/css/selectTenant.css`
- `login.ftl` (current): `src/main/resources/theme/azguards-whatsapp/login/login.ftl`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

None — all tasks completed without blockers.

### Completion Notes List

- **Task 1**: Added `--wt-surface-tint-1`, `--wt-surface-tint-2` (light + dark), and `--wt-focus-ring-halo` tokens to `tokens.css`. No dark override needed for `--wt-focus-ring-halo` as `color-mix` is dynamic and auto-resolves the overridden `--wt-focus-ring`.
- **Task 2**: Fixed 3 deferred items in `style.css`: `.background-overlay` bg uses `--wt-surface-tint-1`, `.card` shadow uses `var(--wt-shadow-card)`, `.form-control:focus` shadow uses token-based `--wt-focus-ring-halo`. Also updated `.tenant-card` bg from `--wt-bg` to `--wt-surface-tint-1` (was previously `#e3e9dd` teal tint pre-1.2).
- **Task 3**: Restored gradient on both `.background-overlay` rules in `selectTenant.css` (desktop + mobile media query) using `linear-gradient(180deg, var(--wt-surface-tint-1), var(--wt-surface-tint-2))`.
- **Task 4**: Created `components.css` with all `.wt-btn`, `.wt-btn--primary`, `.wt-btn--ghost`, `.wt-btn--submitting` (CSS spinner + `prefers-reduced-motion`), `.wt-field`, `.wt-field--error`, and `.wt-card` classes. Zero hardcoded hex; all values are `var(--wt-*)` tokens.
- **Task 5**: Inserted `components.css` link in correct load order in all 5 FTL templates.
- **Task 6**: `mvn package` → BUILD SUCCESS. `rgba(15` audit CLEAN. No hardcoded hex in `components.css`. All 5 FTL templates verified.

### File List

**Modified (azguards-keycloak-custom-theme repo):**
- `src/main/resources/theme/azguards-whatsapp/login/resources/css/tokens.css`
- `src/main/resources/theme/azguards-whatsapp/login/resources/css/style.css`
- `src/main/resources/theme/azguards-whatsapp/login/resources/css/selectTenant.css`
- `src/main/resources/theme/azguards-whatsapp/login/login.ftl`
- `src/main/resources/theme/azguards-whatsapp/login/login-reset-password.ftl`
- `src/main/resources/theme/azguards-whatsapp/login/login-update-password.ftl`
- `src/main/resources/theme/azguards-whatsapp/login/select-tenant.ftl`
- `src/main/resources/theme/azguards-whatsapp/login/review-invitations.ftl`

**Created (azguards-keycloak-custom-theme repo):**
- `src/main/resources/theme/azguards-whatsapp/login/resources/css/components.css`

**Modified (keycloak-multi-tenancy repo — story tracking only):**
- `_bmad-output/implementation-artifacts/1-3-core-form-component-styles.md`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`
