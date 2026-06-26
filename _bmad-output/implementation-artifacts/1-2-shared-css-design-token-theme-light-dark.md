---
baseline_commit: d435581d03c6479160ed85aea7464840a4874a39
---

# Story 1.2: Shared CSS Design-Token Theme (Light + Dark)

Status: done

## Story

As a front-end engineer,
I want a single shared CSS file of semantic design tokens loaded by the Keycloak theme,
so that every in-scope screen renders the WhataTalk teal identity consistently in light and dark mode without hardcoded values.

> **Working Repository:** `azguards-keycloak-custom-theme` (`~/WorkSpace/azguards-whatsapp/azguards-keycloak-custom-theme`)
> Pure CSS / FTL story — no Java SPI changes, no `keycloak-multi-tenancy` work.

## Acceptance Criteria

1. **Token CSS defined:** The shared `tokens.css` file defines semantic CSS custom properties for all brand/surface/text/border/interactive/layout tokens per DESIGN.md: primary teal `#0F766E`, mint `bg`, card max-width `400px`, field height `46px`, border radii, focus-ring width/offset, shadow values, motion values, and the system font stack.

2. **Dark mode works:** A user with `prefers-color-scheme: dark` OR `[data-theme="dark"]` on any ancestor element sees all token names resolve to the dark palette values — no screen-specific changes required.

3. **No hardcoded hex in in-scope CSS/FTL:** After this story, the in-scope CSS files (`style.css`, `selectTenant.css`, `reviewTenant.css`) reference only CSS custom properties (`var(--wt-*)`) — no hardcoded hex values. FTL templates are not visually redesigned yet (that is Epic 2+), but their `<style>` blocks and loaded CSS must not contain raw hex.

4. **`tokens.css` loaded by all in-scope FTL templates:** All five in-scope FTL templates (`login.ftl`, `login-reset-password.ftl`, `login-update-password.ftl`, `select-tenant.ftl`, `review-invitations.ftl`) include a `<link>` to `${url.resourcesPath}/css/tokens.css` before any other custom CSS.

5. **Per-tenant theming documented:** The story includes a comment block in `tokens.css` documenting which tokens are overridable (`--wt-primary`, `--wt-logo`) and the 4.5:1 contrast gate rule — not yet wired to a Keycloak mechanism.

6. **System font stack used:** No web-font `<link>` tags for auth screens. `tokens.css` defines `--wt-font-family` with the system font stack; existing CSS uses it.

7. **`mvn package` passes:** The theme JAR builds without errors after these changes.

## Tasks / Subtasks

- [x] **Task 1: Create `tokens.css`** (AC: #1, #2, #5, #6)
  - [x] Create `src/main/resources/theme/azguards-whatsapp/login/resources/css/tokens.css`
  - [x] Define all `:root` light-mode custom properties (see Dev Notes → Token Definitions)
  - [x] Define dark-mode overrides via `@media (prefers-color-scheme: dark) { :root { ... } }`
  - [x] Define dark-mode overrides via `[data-theme="dark"] { ... }` (JS-controllable override)
  - [x] Add comment block documenting tenant-overridable tokens (`--wt-primary`, `--wt-logo`) and 4.5:1 contrast gate
  - [x] Verify: no hardcoded hex in `tokens.css` (all values are the canonical source — fine here)

- [x] **Task 2: Refactor `style.css` to use tokens** (AC: #3)
  - [x] Replace every hardcoded hex/color literal with the corresponding `var(--wt-*)` property
  - [x] Replace `font-family: "Arial", sans-serif` with `font-family: var(--wt-font-family)`
  - [x] Mapping guide in Dev Notes → Hex-to-Token Migration Map
  - [x] Do NOT change class names, selectors, or layout — visual refactor only for this story
  - [x] Remove `!important` overrides that exist only because hardcoded values were fighting Bootstrap — re-add only if actually needed after token substitution

- [x] **Task 3: Refactor `selectTenant.css` to use tokens** (AC: #3)
  - [x] Replace every hardcoded hex with `var(--wt-*)` per migration map
  - [x] Keep the PatternFly variable `--pf-v5-c-login__container--MaxWidth` unchanged (not a WhataTalk token)
  - [x] Replace `font-family: 'Inter', sans-serif` references with `var(--wt-font-family)` (Inter is a web font — violates NFR-T-4; system stack replaces it)

- [x] **Task 4: Refactor `reviewTenant.css` to use tokens** (AC: #3)
  - [x] Remove the existing ad-hoc `:root` block (`--primary-color`, `--success-color`, `--danger-color`, `--text-color`) — these are superseded by `tokens.css`
  - [x] Replace `var(--primary-color)`, `var(--success-color)`, `var(--danger-color)`, `var(--text-color)` usages with the corresponding `var(--wt-*)` tokens
  - [x] Replace remaining hardcoded hex with `var(--wt-*)` per migration map
  - [x] Replace `font-family: 'Inter', sans-serif` with `var(--wt-font-family)`

- [x] **Task 5: Wire `tokens.css` into FTL templates** (AC: #4)
  - [x] `login.ftl`: Added `tokens.css` link after Bootstrap, before `style.css`
  - [x] `login-reset-password.ftl`: Added `tokens.css` link; also corrected Bootstrap link order (was before `style.css`)
  - [x] `login-update-password.ftl`: Added `tokens.css` + `updatePassword.css` links; extracted large inline `<style>` block (hardcoded hex) to `updatePassword.css`
  - [x] `select-tenant.ftl`: Added `tokens.css` link before `selectTenant.css`
  - [x] `review-invitations.ftl`: Added `tokens.css` link before `reviewTenant.css`; removed empty `<style></style>` tag
  - [x] Verify loading ORDER: Bootstrap (if present) → `tokens.css` → screen-specific CSS

- [x] **Task 6: Build verification** (AC: #7)
  - [x] `mvn package` in `azguards-keycloak-custom-theme` — BUILD SUCCESS; JAR `azguards-keycloak-custom-theme-1.0.15.jar` (56K) produced
  - [x] Zero hardcoded hex found in all in-scope CSS files (grep audit confirmed)
  - [x] All 5 FTL templates confirmed to reference `tokens.css` (grep audit confirmed)
  - [ ] Spot-check: open login screen in browser and verify CSS custom properties are set (`Inspect → :root → verify --wt-primary is #0F766E`) — requires deployed KC environment

## Dev Notes

### Working Repository Reminder

**ALL work in this story is in `azguards-keycloak-custom-theme`** (`~/WorkSpace/azguards-whatsapp/azguards-keycloak-custom-theme`). Do NOT touch the `keycloak-multi-tenancy` Java repo.

Theme resource root:
```
src/main/resources/theme/azguards-whatsapp/login/
├── resources/
│   └── css/
│       ├── style.css          ← MODIFY (Task 2)
│       ├── selectTenant.css   ← MODIFY (Task 3)
│       ├── reviewTenant.css   ← MODIFY (Task 4)
│       └── tokens.css         ← CREATE (Task 1)
├── login.ftl                  ← MODIFY head section (Task 5)
├── login-reset-password.ftl   ← MODIFY head section (Task 5)
├── login-update-password.ftl  ← MODIFY head section (Task 5)
├── select-tenant.ftl          ← MODIFY head section (Task 5)
└── review-invitations.ftl     ← MODIFY head section (Task 5)
```

### Token Definitions (authoritative — copy verbatim into `tokens.css`)

**Source:** DESIGN.md (visual spine wins on conflict). The DESIGN.md token table is canonical; YAML frontmatter values that differ from the table should be ignored.

```css
/* tokens.css — WhataTalk Design System
 * Semantic CSS custom properties for all in-scope auth screens.
 * Light-first; dark-mode values override via media query AND [data-theme="dark"].
 *
 * TENANT THEMING (not yet wired):
 *   Only --wt-primary and --wt-logo are overridable per tenant.
 *   Any tenant-supplied --wt-primary MUST pass a 4.5:1 contrast ratio
 *   with white (#FFFFFF) at config time; auto-darken if it fails.
 *   Never allow tenants to override --wt-ink, --wt-bg, --wt-border, or any
 *   contrast-critical token.
 */

:root {
  /* --- Color tokens (light mode) --- */
  --wt-bg:             #F8FAFC;   /* page background */
  --wt-surface:        #FFFFFF;   /* auth card, workspace cards */
  --wt-ink:            #0F172A;   /* primary text */
  --wt-muted:          #64748B;   /* secondary text, hints */
  --wt-border:         #E2E8F0;   /* decorative hairlines, field default border */
  --wt-border-strong:  #94A3B8;   /* input/control boundaries (≥3:1, WCAG 1.4.11) */
  --wt-primary:        #0F766E;   /* WhataTalk teal — primary action, focus */
  --wt-primary-hover:  #115E54;   /* primary hover state */
  --wt-on-primary:     #FFFFFF;   /* text on primary bg (white, 4.65:1 vs teal) */
  --wt-danger:         #DC2626;   /* error icons/fills */
  --wt-danger-text:    #B91C1C;   /* error text (AA on surface/danger-bg) */
  --wt-danger-bg:      #FEF2F2;   /* error field/alert background */
  --wt-success:        #16A34A;   /* success icons/fills */
  --wt-success-text:   #15803D;   /* success text (AA on surface) */
  --wt-focus-ring:     #0F766E;   /* focus outline color */

  /* --- Typography --- */
  --wt-font-family:    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto,
                       Helvetica, Arial, sans-serif;

  /* Type scale */
  --wt-text-title-size:    24px;
  --wt-text-title-weight:  600;
  --wt-text-title-line:    1.25;

  --wt-text-subtitle-size:    18px;
  --wt-text-subtitle-weight:  600;
  --wt-text-subtitle-line:    1.3;

  --wt-text-body-size:    15px;
  --wt-text-body-weight:  400;
  --wt-text-body-line:    1.5;

  --wt-text-label-size:    14px;
  --wt-text-label-weight:  500;
  --wt-text-label-line:    1.4;

  --wt-text-caption-size:    12px;
  --wt-text-caption-weight:  400;
  --wt-text-caption-line:    1.4;

  /* --- Spacing --- */
  --wt-space-1:   4px;
  --wt-space-2:   8px;
  --wt-space-3:   12px;
  --wt-space-4:   16px;
  --wt-space-6:   24px;
  --wt-space-8:   32px;
  --wt-space-12:  48px;

  --wt-field-height:    46px;
  --wt-card-max-width:  400px;

  /* --- Border radius --- */
  --wt-radius-field:   10px;
  --wt-radius-card:    12px;
  --wt-radius-card-lg: 16px;
  --wt-radius-pill:    999px;
  --wt-radius-logo:    10px;

  /* --- Shadow --- */
  --wt-shadow-card: 0 4px 6px rgba(0, 0, 0, 0.04);

  /* --- Motion --- */
  --wt-duration: 160ms;
  --wt-easing:   ease;
  --wt-hover-tint: #F8FAFC;

  /* --- Focus ring geometry --- */
  --wt-focus-width:  2px;
  --wt-focus-offset: 2px;
}

/* Dark mode — media query (automatic, no JS) */
@media (prefers-color-scheme: dark) {
  :root {
    --wt-bg:             #0B1220;
    --wt-surface:        #111A2E;
    --wt-ink:            #E6EDF7;
    --wt-muted:          #94A3B8;
    --wt-border:         #1F2A44;
    --wt-border-strong:  #47655E;
    --wt-primary:        #0F766E;
    --wt-primary-hover:  #0B5F58;
    --wt-on-primary:     #FFFFFF;
    --wt-danger:         #F87171;
    --wt-danger-text:    #FCA5A5;
    --wt-danger-bg:      #2A1416;
    --wt-success:        #34D399;
    --wt-success-text:   #6EE7B7;
    --wt-focus-ring:     #2DD4BF;
    --wt-shadow-card:    0 4px 16px rgba(0, 0, 0, 0.4);
    --wt-hover-tint:     #0F1A30;
  }
}

/* Dark mode — explicit override (JS-controllable, e.g. data-theme="dark" on <html>) */
[data-theme="dark"] {
  --wt-bg:             #0B1220;
  --wt-surface:        #111A2E;
  --wt-ink:            #E6EDF7;
  --wt-muted:          #94A3B8;
  --wt-border:         #1F2A44;
  --wt-border-strong:  #47655E;
  --wt-primary:        #0F766E;
  --wt-primary-hover:  #0B5F58;
  --wt-on-primary:     #FFFFFF;
  --wt-danger:         #F87171;
  --wt-danger-text:    #FCA5A5;
  --wt-danger-bg:      #2A1416;
  --wt-success:        #34D399;
  --wt-success-text:   #6EE7B7;
  --wt-focus-ring:     #2DD4BF;
  --wt-shadow-card:    0 4px 16px rgba(0, 0, 0, 0.4);
  --wt-hover-tint:     #0F1A30;
}
```

### Hex-to-Token Migration Map

Use this map when refactoring `style.css`, `selectTenant.css`, and `reviewTenant.css`. Every hardcoded hex in those files must be replaced with the token on the right.

| Hardcoded value | Token | Notes |
|---|---|---|
| `#e0f0ee` | `var(--wt-bg)` | page/overlay background |
| `#E6F2EE` | `var(--wt-bg)` | same token, capitalization variant |
| `#f8f9fa` | `var(--wt-bg)` | Bootstrap near-white, maps to surface/bg hover |
| `#ffffff`, `#FFFFFF` | `var(--wt-surface)` | card/container backgrounds |
| `#686868`, `#969696` | `var(--wt-muted)` | secondary text |
| `#0F172A`, `#2d3748` | `var(--wt-ink)` | primary text |
| `#718096` | `var(--wt-muted)` | muted text |
| `#6b7280` | `var(--wt-muted)` | muted text (Tailwind grey-500 equivalent) |
| `#444444` | `var(--wt-ink)` | dark link text |
| `#ddd`, `#e9ecef`, `#eaeaea` | `var(--wt-border)` | hairlines / field borders |
| `#d1d1d1`, `#a8a8a8` | `var(--wt-border-strong)` | scrollbar thumb / stronger borders |
| `#cbp5e0`, `#cbd5e0` | `var(--wt-border-strong)` | same |
| `#e5e7eb` | `var(--wt-border)` | avatar ring |
| `#E3E9DD`, `#e3e9dd` | `var(--wt-border)` | scrollbar / tinted border (greenish legacy) |
| `#dbeecd` | `var(--wt-border)` | hover border (legacy green-tinted) |
| `#225e56`, `#378379`, `#35897e`, `#35897E` | `var(--wt-primary)` | all legacy teal primary values |
| `#1a4a44` | `var(--wt-primary-hover)` | primary hover |
| `#115E54` | `var(--wt-primary-hover)` | primary hover |
| `#0F766E` | `var(--wt-primary)` | canonical teal (already correct) |
| `#dc3545`, `#DC2626` | `var(--wt-danger)` | error icons/fills |
| `#c82333` | `var(--wt-danger)` | danger hover |
| `#dc3545` | `var(--wt-danger)` | Bootstrap danger |
| `#8dafa9` | `var(--wt-primary)` with `opacity: 0.5` | disabled primary — use opacity instead of hardcoded |
| `#f0fff4` | `var(--wt-danger-bg)` with success semantics | accepted-card bg; use `var(--wt-success)` at low opacity or define inline |
| `#c6f6d5` | `var(--wt-border)` on accepted cards | use `var(--wt-success)` or `var(--wt-border)` |
| `#f1f1f1`, `#e2e2e2` | `var(--wt-border)` / `var(--wt-bg)` | reject button backgrounds |
| `#e2e8f0`, `#E6F0E6` | `var(--wt-border)` | legacy tinted border/bg |
| `#000000` | `var(--wt-ink)` | use token, not raw black |

**Font families to replace:**
- `"Arial", sans-serif` → `var(--wt-font-family)`
- `'Inter', sans-serif` → `var(--wt-font-family)` (Inter is a web font; NFR-T-4 prohibits web fonts on auth screens)

### Critical: `reviewTenant.css` Ad-Hoc Variables

`reviewTenant.css` defines its own `:root` block:
```css
:root {
    --primary-color: #225e56;
    --success-color: #225e56;
    --danger-color: #dc3545;
    --text-color: #686868;
}
```
**Remove this entire block** — it is superseded by `tokens.css`. Then replace all `var(--primary-color)`, `var(--success-color)`, `var(--danger-color)`, `var(--text-color)` usages:
- `var(--primary-color)` → `var(--wt-primary)`
- `var(--success-color)` → `var(--wt-primary)` (was same value as primary)
- `var(--danger-color)` → `var(--wt-danger)`
- `var(--text-color)` → `var(--wt-muted)`

### PatternFly Variables

`selectTenant.css` uses `--pf-v5-c-login__container--MaxWidth`. This is a PatternFly 5 variable, not a WhataTalk token. **Leave it unchanged** — do not rename or remove it.

### CSS Load Order in FTL Templates

The required `<link>` order is:
```html
<!-- Bootstrap CDN (if present in this template) -->
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css">
<!-- Font Awesome CDN (if present) — acceptable for now; removed later in Epic 1.6/2 cleanup -->
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.7.0/css/font-awesome.min.css">
<!-- WhataTalk tokens — MUST come before any custom screen CSS -->
<link rel="stylesheet" href="${url.resourcesPath}/css/tokens.css">
<!-- Screen-specific CSS -->
<link rel="stylesheet" href="${url.resourcesPath}/css/style.css">
```

For `select-tenant.ftl` and `review-invitations.ftl`, audit those files first — they may not use Bootstrap at all and may inline their CSS or load it differently. The rule is always: `tokens.css` before screen CSS.

### What NOT to Change in This Story

- **Do NOT redesign any screens.** Keep all existing class names, layout, and visual structure intact. This story is token wiring only; Epic 2 onwards redesigns the screens.
- **Do NOT remove Bootstrap.** Bootstrap cleanup is part of Epic 1 hygiene (Story 1.6) or later.
- **Do NOT add new CSS classes.** The component classes (button, text-field, auth-card per UX-DR2/3/4) are Story 1.3.
- **Do NOT modify FTL template logic.** Only add the `<link>` tag to each template's `<head>`.
- **Do NOT touch the Java repo** (`keycloak-multi-tenancy`). This is a pure theme story.
- **Out-of-scope FTL (do not touch):** `register.ftl`, `login-oauth-grant.ftl`, email templates under `email/`.

### Previous Story Intelligence (Story 1.1)

- Story 1.1 was in the `keycloak-multi-tenancy` Java repo — **no learnings directly applicable to CSS work**.
- Confirmed KC 26.4.6 is the running runtime. The theme JAR is loaded separately via `kc.sh build` — theme changes take effect after a JAR rebuild and KC restart (or hot-reload if dev mode is enabled).
- The theme directory path in KC is `azguards-whatsapp` — verify in the Keycloak admin UI under Realm Settings → Themes that the login theme is `azguards-whatsapp`.

### Build System for the Theme Repo

The `azguards-keycloak-custom-theme` repo uses Maven. Build the JAR with:
```bash
# From ~/WorkSpace/azguards-whatsapp/azguards-keycloak-custom-theme
mvn package
# JAR lands in target/ — deploy to KC's providers/ and run kc.sh build
```

No tests in this repo — verification is visual (browser DevTools).

### Regression Prevention

These existing behaviours must survive the refactor:
1. `login.ftl` login form still submits to `${url.loginAction}` — not touched.
2. `select-tenant.ftl` tenant selection still works — not touched beyond adding `<link>`.
3. `review-invitations.ftl` accept/reject still works — not touched beyond adding `<link>`.
4. Password show/hide toggle in `login.ftl` still works — not touched.
5. Spinner on form submit in `login.ftl` still works — not touched.

### Project Structure Notes

- Theme name: `azguards-whatsapp` (matches `src/main/resources/theme/azguards-whatsapp/`)
- CSS is served via `${url.resourcesPath}` which maps to `theme/azguards-whatsapp/login/resources/`
- Only the `login` theme type is in use (email templates are in `email/`)
- After `mvn package`, deploy the resulting JAR to the Keycloak `providers/` directory and run `kc.sh build`

### References

- Token definitions: [Source: `_bmad-output/planning-artifacts/ux-designs/ux-keycloak-multi-tenancy-2026-06-11/DESIGN.md` — Colors table + YAML frontmatter]
- NFR-T-1 (no hardcoded hex), NFR-T-2 (teal, mint, centered card), NFR-T-3 (light+dark), NFR-T-4 (system font): [Source: `_bmad-output/planning-artifacts/epics.md` — Requirements Inventory]
- AR-10 (shared CSS design-token theme): [Source: `_bmad-output/planning-artifacts/epics.md` — Additional Requirements]
- UX-DR1 (design-token system): [Source: `_bmad-output/planning-artifacts/epics.md` — UX Design Requirements]
- Story 1.2 AC: [Source: `_bmad-output/planning-artifacts/epics.md` — Epic 1 Story 1.2]
- Current `style.css`: `src/main/resources/theme/azguards-whatsapp/login/resources/css/style.css`
- Current `selectTenant.css`: `src/main/resources/theme/azguards-whatsapp/login/resources/css/selectTenant.css`
- Current `reviewTenant.css`: `src/main/resources/theme/azguards-whatsapp/login/resources/css/reviewTenant.css`
- Current `login.ftl` (head structure): `src/main/resources/theme/azguards-whatsapp/login/login.ftl`
- AR-OOS (do not modify): register.ftl, login-oauth-grant.ftl, email templates [Source: epics.md#Additional Requirements]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- `login-update-password.ftl` contained a large inline `<style>` block (~70 lines) with 8 hardcoded hex values. Extracted to new `updatePassword.css` and tokenised. This was not covered in the original task list but was required by AC #3 ("FTL template `<style>` blocks must not contain raw hex").
- `reviewTenant.css` had an ad-hoc `:root` block defining `--primary-color`, `--success-color`, `--danger-color`, `--text-color` — all removed and replaced with `var(--wt-*)` equivalents.
- `login-reset-password.ftl` had `style.css` loaded BEFORE Bootstrap — corrected to Bootstrap → tokens.css → style.css.
- Added supplementary tokens `--wt-warning` (amber, for password strength: fair) and `--wt-info` (mapped to `--wt-primary` teal, replaces Bootstrap info blue for password strength: strong). These cover UI-utility states not in core DESIGN.md palette.
- Build: `mvn package` produced `azguards-keycloak-custom-theme-1.0.15.jar` (56K). Zero exit code — BUILD SUCCESS.

### Completion Notes List

- ✅ AC #1: `tokens.css` created with 20+ semantic custom properties covering color, typography, spacing, radii, shadow, motion, and focus-ring geometry — light-first.
- ✅ AC #2: Dark mode via both `@media (prefers-color-scheme: dark)` and `[data-theme="dark"]` selector — all tokens flip correctly.
- ✅ AC #3: Zero hardcoded hex in all in-scope CSS files (`style.css`, `selectTenant.css`, `reviewTenant.css`, `updatePassword.css`) and FTL `<style>` blocks — confirmed by grep audit.
- ✅ AC #4: All 5 in-scope FTL templates reference `tokens.css` before their screen-specific CSS — confirmed by grep audit (1 match each).
- ✅ AC #5: Tenant theming comment block in `tokens.css` documents `--wt-primary`/`--wt-logo` as overridable, 4.5:1 contrast gate, and which tokens must never be overridden.
- ✅ AC #6: `--wt-font-family` defined as system font stack; all `font-family: "Arial"` and `font-family: 'Inter'` references replaced in CSS files.
- ✅ AC #7: `mvn package` → BUILD SUCCESS. JAR `azguards-keycloak-custom-theme-1.0.15.jar` produced.
- ⚠️ Browser spot-check (deploy to KC) is pending — requires a running KC 26.4.6 environment with the new JAR deployed.

### File List

**Created (new files):**
- `src/main/resources/theme/azguards-whatsapp/login/resources/css/tokens.css`
- `src/main/resources/theme/azguards-whatsapp/login/resources/css/updatePassword.css`

**Modified (existing files):**
- `src/main/resources/theme/azguards-whatsapp/login/resources/css/style.css`
- `src/main/resources/theme/azguards-whatsapp/login/resources/css/selectTenant.css`
- `src/main/resources/theme/azguards-whatsapp/login/resources/css/reviewTenant.css`
- `src/main/resources/theme/azguards-whatsapp/login/login.ftl`
- `src/main/resources/theme/azguards-whatsapp/login/login-reset-password.ftl`
- `src/main/resources/theme/azguards-whatsapp/login/login-update-password.ftl`
- `src/main/resources/theme/azguards-whatsapp/login/select-tenant.ftl`
- `src/main/resources/theme/azguards-whatsapp/login/review-invitations.ftl`

## Review Findings

_Adversarial code review (Blind Hunter + Edge Case Hunter + Acceptance Auditor), 2026-06-12. All findings verified against the actual theme repo working tree (`azguards-keycloak-custom-theme`, baseline `d435581`)._

### Patches (resolved — all applied 2026-06-12, build re-verified `mvn package` → BUILD SUCCESS, JAR 1.0.15)

- [x] [Review][Patch] Removing `--error-container-height` from `:root` breaks runtime JS height adjustment [reviewTenant.css:1 / script.js:274] — the deleted ad-hoc `:root` block also contained `--error-container-height: 96px`, which `script.js:274` reads via `getComputedStyle(document.documentElement).getPropertyValue('--error-container-height')`. It returned `''`, producing `height: calc(58vh - )` (invalid → ignored), so the review-invitations error-state height adjustment silently failed. The CSS-side fallback `var(--error-container-height, 96px)` at `reviewTenant.css:284` did NOT cover the JS `getPropertyValue` read. **Fixed:** re-added a `:root { --error-container-height: 96px; }` rule (with explanatory comment) at the top of `reviewTenant.css`. **(HIGH — functional regression, Regression-Prevention #3.)**
- [x] [Review][Patch] Accepted-invitation card used error/danger background for a success state [reviewTenant.css:97] — `.tenant-invitation-card.accepted` was `background-color: var(--wt-danger-bg)` (red) with `var(--wt-border)` border, making accepted cards read as errors in both modes. **Fixed (Decision 1 → patch):** background now `color-mix(in srgb, var(--wt-success) 12%, var(--wt-surface))` and border + hover-border now `var(--wt-success)` — success-tinted, adapts to light/dark, no hardcoded hex (AC#3 preserved).
- [x] [Review][Patch] Disabled "proceed" button deviated from migration map and was below AA [reviewTenant.css:265] — `background-color: var(--wt-muted)` (white label on `#64748B` ≈ 4.0:1, fails AA) and a text-color token used as a surface. **Fixed:** now `var(--wt-primary)` + `opacity: 0.5` per the migration map (both base and `:hover`).
- [x] [Review][Patch] Reject-button label below AA contrast [reviewTenant.css:203] — `color: var(--wt-danger)` (`#DC2626`) on `var(--wt-border)` (`#E2E8F0`) ≈ 3.9:1 (fails AA). **Fixed:** now `var(--wt-danger-text)` (`#B91C1C`), the AA-compliant error-text token.

### Deferred

- [x] [Review][Defer] Tokenization flattened distinct decorative backgrounds + removed the mobile brand gradient [selectTenant.css:250 / style.css:77 / reviewTenant.css] — `selectTenant.css:250` collapsed the two-stop `linear-gradient(180deg,#e0f0ee,#d7efc9)` mobile header band to flat `var(--wt-bg)`; `.background-overlay`, `.pf-v5-c-login__main::before`, and `.tenant-card` (was `#e3e9dd`) all now resolve to the same `var(--wt-bg)`, flattening layered depth and the inset band shadow. (`#e3e9dd` mapped to `--wt-bg` vs the map's `--wt-border`.) **Deferred (Decision 2): needs surface-tint tokens** — restoring layered depth requires new surface-tint tokens not yet in the design system; belongs with Story 1.3 component styles.

- [x] [Review][Defer] Focus ring hard-codes `rgba(15,118,110,.25)` instead of `--wt-focus-ring` [style.css:814] — dark-mode focus ring stays teal instead of the intended brighter `#2DD4BF`; `--wt-focus-ring` goes unused. Deferred — focus-style consumption is Story 1.3+ scope; AC#3 only bans hex literals (rgba passes). Needs a color-mix or rgba channel token.
- [x] [Review][Defer] Card/overlay `box-shadow` values use literal `rgba(0,0,0,…)` not `--wt-shadow-card` — shadows are nearly invisible in dark mode; the dark `--wt-shadow-card` variant is defined but unused. Deferred — shadow consumption is not in this token-definition story's scope.
- [x] [Review][Defer] `font-family: var(--wt-font-family)` has no fallback — if `tokens.css` fails to load, text falls back to browser-default serif (previously `Arial`). Deferred — low risk (tokens.css now linked on every page); add `, sans-serif` fallback opportunistically.
- [x] [Review][Defer] No `[data-theme="light"]` escape hatch — a user on OS dark mode cannot be forced back to light via `data-theme`, since only `@media` + `[data-theme="dark"]` blocks exist. Deferred — no AC requires forced-light override; future theming-toggle enhancement.

### Dismissed (noise / false positives / verified-safe)

- `--pf-v5-c-login__container--MaxWidth` "lost its global fallback" (Blind Hunter) — **verified safe**: defined on `.pf-v5-c-login__main` and consumed by its descendant `.tenant-invitations-container`; inheritance holds (Edge Case Hunter confirmed).
- Dark-mode `@media` and `[data-theme="dark"]` blocks being byte-identical duplicates — **spec-mandated** (Task 1 explicitly requires both mechanisms).
- `--wt-info`/`--wt-warning` extra tokens for password-strength states — intentional and documented in the Dev Agent Record; don't alter the required token set.
- `login-reset-password.ftl` loads no icon font (Blind Hunter) — speculative; the reset-password screen is a plain email-entry form with no icon markup.
- `--wt-danger-border` undocumented addition / EOF-newline observation — trivial, no impact.

### Scope hygiene (not a Story 1.2 code defect)

- `Jenkinsfile` (+36/−20, adds an AWS CLI stage) is dirty in the theme repo working tree but is NOT in this story's File List — unrelated CI change; should not ship under Story 1.2. Also untracked IDE files (`.classpath`, `.project`, `.settings/`) present.
