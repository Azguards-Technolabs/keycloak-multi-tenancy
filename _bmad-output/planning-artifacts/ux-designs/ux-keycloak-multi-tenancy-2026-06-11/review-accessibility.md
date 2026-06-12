# Accessibility Review — keycloak-multi-tenancy login & onboarding

**Target:** WCAG 2.1 AA · **Scope:** auth flows (login, registration, magic-link, passkey, workspace picker, invite)
**Reviewed:** DESIGN.md, EXPERIENCE.md · **Date:** 2026-06-11

Ratios below computed from the documented hex tokens (sRGB relative luminance, WCAG formula). "Normal text" = 4.5:1; "large text / UI component" = 3:1.

---

## Color contrast — verified per pair

| Pair | Mode | Ratio | Normal (4.5) | UI/large (3.0) | Verdict |
|---|---|---|---|---|---|
| White on `primary` #3B82F6 | dark | **3.68:1** | FAIL | pass | **Blocker** |
| White on `primary` #2563EB | light | 5.17:1 | pass | pass | OK |
| White on `primary-hover` #1D4ED8 | light | 6.7:1 | pass | pass | OK |
| `success` #16A34A on `surface` #FFFFFF | light | **3.3:1** | FAIL | pass | Should-fix |
| `danger` #DC2626 on `danger-bg` #FEF2F2 | light | **4.41:1** | FAIL (marginal) | pass | Should-fix |
| `muted` #64748B on `surface` #FFFFFF | light | 4.76:1 | pass | pass | OK |
| `muted` #64748B on `bg` #F8FAFC | light | 4.55:1 | pass (marginal) | pass | OK — watch |
| `ink` #0F172A on `surface` | light | 17.85:1 | pass | pass | OK |
| `danger` #DC2626 text on `surface` | light | 4.83:1 | pass | pass | OK |
| `primary` #2563EB link on `surface` | light | 5.17:1 | pass | pass | OK |
| `border` #E2E8F0 on `surface` | light | **1.23:1** | n/a | FAIL | Should-fix (see below) |
| `muted` #94A3B8 on `surface` #111A2E | dark | 6.76:1 | pass | pass | OK |
| `muted` #94A3B8 on `bg` #0B1220 | dark | 7.3:1 | pass | pass | OK |
| `ink` #E6EDF7 on `surface` | dark | 14.72:1 | pass | pass | OK |
| `danger` #F87171 on `danger-bg` #2A1416 | dark | 6.27:1 | pass | pass | OK |
| `success` #34D399 on `surface` | dark | 9.02:1 | pass | pass | OK |
| `primary` #3B82F6 link on `surface` | dark | 4.71:1 | pass | pass | OK |
| `border` #1F2A44 on `surface` #111A2E | dark | **1.22:1** | n/a | FAIL | Should-fix (see below) |

### Findings

- **[BLOCKER] Dark-mode primary button text fails AA — 1.4.3 Contrast (Minimum).**
  White `on-primary` #FFFFFF on dark `primary` #3B82F6 = **3.68:1**, below 4.5:1 for the "Continue" button label (normal-weight 14px). DESIGN.md (Colors §) explicitly claims "primary button text (white on `primary`) ≥ 4.5:1 in **both** modes" — that claim is false for dark mode. The "Continue" CTA is the single most-used control in every flow. Fix: darken dark `primary` to ~#2563EB-class (which gives 5.17:1) for the button fill, or use the documented `primary-hover` value, or bump label weight to ≥600 and ≥18.66px to qualify as large text (3:1). Note per-tenant theming can override `primary` and is only constrained to "never override contrast guarantees" — but the *default* dark token already breaks the guarantee, so the floor itself must be fixed, and tenant-override validation must enforce 4.5:1 against `on-primary`.

- **[SHOULD-FIX] Light `success` on `surface` = 3.3:1 — 1.4.3.** Used for the "You've joined {Workspace}" confirmation toast and "accepted invite" text. Fails for normal text. Acceptable only if rendered as large/bold text or as an icon+background (non-text). If `success` is used for body-size confirmation copy, darken it (e.g. toward #15803D ≈ 4.5:1+). Dark `success` is fine (9.02:1).

- **[SHOULD-FIX] Light `danger` on `danger-bg` = 4.41:1 — 1.4.3 (marginal fail).** The inline-error pattern puts `danger` text on a `danger-bg` field fill. 4.41 rounds under 4.5 and fails strict AA. Either render error text on `surface` (4.83:1, passes) rather than on the tinted fill, or darken `danger-bg`/`danger`. Confirm which surface the error message text actually sits on — DESIGN.md says the field gets `danger-bg` fill and the message sits "below" it; if "below" means on `surface`, this passes. Make that explicit.

- **[SHOULD-FIX] Field/card border contrast = 1.23:1 (light) / 1.22:1 (dark) — 1.4.11 Non-text Contrast.** The hairline `border` on `surface` is far below the 3:1 required for the visual boundary of an input control or its states. A text field whose only at-rest boundary is this hairline is not perceivable to low-vision users. This is acceptable *only* because the design also reserves the `primary` focus ring (2px) for focus — but the **unfocused / resting** field boundary and the workspace-card boundary still need a 3:1-capable indicator (or a label/placeholder structure that doesn't depend on the border). Verify resting-state field perceivability; consider a darker border token for inputs specifically.

- **[NIT] `muted` on `bg` (light) = 4.55:1** is a hair above the line. Any future lightening of `bg` or `muted` will tip it under. Lock these tokens or add a CI contrast check.

---

## Focus management — multi-step email-first flow

- **[SHOULD-FIX] No documented focus transfer between steps — 2.4.3 Focus Order / 3.2.x.** The flow "slides" from email → "Let's set up your account" → "Name your workspace" → picker (EXPERIENCE.md IA + Priya flow). Each transition must move focus to the new step's `<h1>` or first field (programmatic `focus()` after the view swaps), or keyboard/SR users are stranded at a stale focus point or sent back to page top. Spec is silent on this. Define: on each step change, move focus to the new heading (tabindex="-1") and announce the step.

- **[SHOULD-FIX] Magic-link "Check your email" state — focus + return focus, 2.4.3 / 4.1.3.** After Continue → send, the view becomes a confirmation state with "resend" and "use a different method." Two gaps: (1) focus must move into this new state (e.g. the heading), not remain on the now-hidden Continue button; (2) when the user returns to the tab after clicking the link, or when "resend" fires, the status ("We sent a new link") must be announced via `aria-live`. The returning-focus case (user comes back from email client) needs a defined landing focus. None specified.

- **[SHOULD-FIX] Passkey / WebAuthn prompt SR behavior — 4.1.3 Status Messages.** EXPERIENCE.md says unsupported devices "silently fall back" and failed prompts show "Try again or use another method." "Silently" is a red flag for SR users: the *visual* fallback is silent, but the state change (passkey offered → magic-link offered) must be announced via `aria-live="polite"`, and the trigger that invokes `navigator.credentials.get()` must have an accessible name describing what will happen. Failed-prompt error must also be in a live region, not just repainted. Specify.

- **[NIT] Provisioning interstitial "Getting your workspace ready…" — 4.1.3.** Good that a blank hang is avoided. Ensure the interstitial text is in an `aria-live="polite"` (or `role="status"`) region and that focus is parked sensibly (not lost when the interstitial replaces the form), and that completion (landing in workspace) is announced.

---

## Error handling

- **[GOOD] aria-live / aria-describedby / aria-invalid / non-color all specified.** Accessibility Floor §: errors via `aria-live="polite"`, field errors linked with `aria-describedby`, color never sole signal (icon + text + `aria-invalid`). This meets 1.4.1, 3.3.1, 3.3.3, 4.1.3 at the spec level. Verify in implementation that:
  - **[SHOULD-FIX] aria-live region exists *before* the error is injected.** A region inserted into the DOM at the same moment as the error text is often not announced. The polite region must be present and empty on load.
  - **[NIT] One error per field, `aria-describedby` points to the message id, and `aria-invalid="true"` toggles off on correction** — spec implies but doesn't state the cleanup.
  - **[SHOULD-FIX] Rate-limit / locked-account edge (Login).** "calm explanatory message + recovery path" must be in a live region and keyboard-reachable; confirm it isn't a transient toast that SR users miss. (4.1.3)

---

## Keyboard

- **[GOOD, verify] Full-row workspace cards as real buttons — 2.1.1 / 4.1.2.** EXPERIENCE.md states "full-row cards are real buttons" and "All actions reachable by keyboard." Correct intent. Verify implementation uses `<button>` (not a `<div onclick>` row) so the entire row is one focusable control with role=button, an accessible name combining workspace name + role, and Enter/Space activation. The logo/role/name composite must be a single accessible name, not three tab stops.

- **[SHOULD-FIX] Search field above the list — 2.4.3, 1.3.1.** When count > 5 the search field appears. Define: search input has a visible `<label>` (not placeholder-only), is in logical tab order before the result rows, and filtering results announces the new count via `aria-live` ("3 workspaces") so SR users know the list changed. Last-used pinned row needs a non-visual indicator of "last used" (text, not just position/visual).

- **[GOOD] Enter-to-submit** on the focused single-field step is specified (Interaction Primitives). Meets expectation. Ensure the field is inside a `<form>` so Enter submits natively rather than relying on a keydown handler.

---

## Targets, labels, motion, headings

- **[GOOD] Targets ≥44px.** `field-height: 46px` and button height 46px satisfy 2.5.5 / 2.5.8. **[NIT]** Verify the magic-link "resend" and "use a different method" text links, and the method-row items, also meet ≥44px touch height (text links often render ~20px). Method rows are `muted` until hover — ensure they are still ≥44px hit area and keyboard-focusable.

- **[GOOD] Labels not placeholder-only** ("Labels always present (not placeholder-only)"), meets 3.3.2. Note microcopy lists "e.g. Priya's Studio" as a *placeholder* for the workspace name — confirm the visible label "Name your workspace" is the `<label>`, with the example as placeholder/hint, not a replacement.

- **[GOOD] Reduced motion.** `prefers-reduced-motion` respected and "no spinner-only feedback … pair with text" — meets 2.3.3 and supports SR users. Ensure the Continue button's submitting state shows text ("Signing in…") alongside the spinner, and the "slide" between steps is disabled/instant under reduced-motion.

- **[GOOD] Single h1.** "single `<h1>` per screen" specified (1.3.1 / 2.4.6). Each step ("Sign in", "Let's set up your account", "Name your workspace", "Choose a workspace") must own exactly one h1, and focus moves to it on step change (see Focus Management).

---

## Auth-specific tokens & announcements

- **[SHOULD-FIX] `autocomplete` tokens — 1.3.5.** EXPERIENCE.md correctly sets email field `autocomplete="username webauthn"` (good — enables passkey conditional UI). Gaps to specify:
  - The **registration name field** needs `autocomplete="name"`.
  - The **password fallback field** (when used) needs `autocomplete="current-password"`.
  - The **magic-link / OTP entry** (if any code is entered manually) needs `autocomplete="one-time-code"` and `inputmode="numeric"` so SMS/email OTP autofill works and SR/mobile users get the numeric keypad. The spec mentions magic link but not whether a code is ever typed — clarify; if a code path exists, it must carry `one-time-code`.

- **[SHOULD-FIX] OTP / magic-link screen-reader friendliness — 1.3.1, 4.1.3.** The "Check your email" state should: read the email address it was sent to (so SR users confirm the target), expose "resend" with a clear name and announce throttle state ("Resend available in 30s" — not a silent disabled button; use `aria-disabled` + live text). If OTP digits are split into multiple boxes, that pattern is hostile to SR/autofill — prefer a single `one-time-code` input.

- **[SHOULD-FIX] Timeout / expiry announcements — 2.2.1, 4.1.3.** "That link has expired — we'll send a new one" and rate-limit/lockout must be announced (live region), not only visually swapped. If any screen has a session/link countdown, 2.2.1 requires a way to extend/turn off or at least adequate warning; the auto-resend-on-expiry pattern is a reasonable accommodation but the expiry itself must be perceivable to SR users at the moment it happens.

- **[NIT] Invite auto-accept confirmation toast — 4.1.3.** "You've joined {Workspace}" as a toast: ensure `role="status"` and that it isn't the *only* confirmation (it may auto-dismiss before an SR user reaches it). Pair with a persistent in-page confirmation or land on a heading that states the workspace.

---

## Summary by severity

**Blocker (1)**
1. Dark-mode primary button white-on-#3B82F6 = 3.68:1 — fails 1.4.3 and contradicts the doc's stated "≥4.5:1 both modes." Fixes the most-used CTA. Also enforce contrast on per-tenant `primary` overrides.

**Should-fix (11)**
- Light `success` on surface 3.3:1 (1.4.3, if used as body text)
- Light `danger` on `danger-bg` 4.41:1 (1.4.3, marginal — clarify which surface error text sits on)
- Field/card border 1.2:1 non-text contrast (1.4.11 — resting-state perceivability)
- Focus transfer between flow steps undefined (2.4.3)
- Magic-link state focus + return-focus + resend announcement (2.4.3 / 4.1.3)
- Passkey "silent" fallback must be announced; trigger needs accessible name (4.1.3 / 4.1.2)
- aria-live region must pre-exist before error injection (4.1.3)
- Rate-limit/lockout message must be live + keyboard-reachable (4.1.3)
- Search field: visible label + result-count announcement + last-used non-visual marker (1.3.1 / 4.1.3)
- Autocomplete tokens incomplete (name / current-password / one-time-code) (1.3.5)
- OTP/timeout/expiry announcements and OTP input pattern (1.3.1 / 2.2.1 / 4.1.3)

**Nit (5)**
- `muted` on `bg` 4.55:1 is marginal — lock tokens / add CI contrast check
- Provisioning interstitial: role="status" + focus parking
- Text-link/method-row ≥44px hit area + focusability
- Reduced-motion: submitting state needs text, step slide disabled
- Auto-accept toast: role="status" + persistent fallback

**Good / spec already covers:** aria-live + aria-describedby + aria-invalid + non-color error signals; targets ≥44px on fields/buttons; labels not placeholder-only; single h1; reduced-motion with text pairing; full-row cards intended as real buttons; Enter-to-submit; email field `autocomplete="username webauthn"`. Most are spec-level commitments — verify at implementation.
