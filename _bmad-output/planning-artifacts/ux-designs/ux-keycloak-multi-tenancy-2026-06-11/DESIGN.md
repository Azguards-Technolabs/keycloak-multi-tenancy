---
status: final
created: 2026-06-11
updated: 2026-06-11
revision: 2 (reconciled to live WhataTalk product — teal brand, username login, Account vocabulary)
project: keycloak-multi-tenancy
direction: Trust & Clarity (WhataTalk teal)
product: WhataTalk
colors:
  # WhataTalk teal brand. Exact brand hex TBD — confirm against WhataTalk assets;
  # values below are derived from the live login screenshots and AA-checked.
  light:
    bg: "#E6F2EE"              # mint page background (matches live login)
    surface: "#FFFFFF"
    ink: "#0F172A"
    muted: "#64748B"
    border: "#E2E8F0"          # decorative hairlines
    border-strong: "#94A3B8"   # input/control boundaries (≥3:1 for 1.4.11)
    primary: "#0F766E"         # WhataTalk teal; white text 4.65:1 (AA)
    primary-hover: "#115E54"
    on-primary: "#FFFFFF"
    danger: "#DC2626"          # icons/fills
    danger-text: "#B91C1C"     # error text (AA on surface/danger-bg)
    danger-bg: "#FEF2F2"
    success: "#16A34A"         # icons/fills
    success-text: "#15803D"    # success text (AA on surface)
    focus-ring: "#0F766E"
  dark:
    bg: "#0B1A17"              # dark teal-black
    surface: "#11221E"
    ink: "#E6EDEB"
    muted: "#94A3B8"
    border: "#1F3A34"          # decorative hairlines
    border-strong: "#47655E"   # input/control boundaries
    primary: "#0F766E"         # white text 4.65:1 (AA)
    primary-hover: "#0B5F58"
    on-primary: "#FFFFFF"
    danger: "#F87171"
    danger-text: "#FCA5A5"
    danger-bg: "#2A1416"
    success: "#34D399"
    success-text: "#6EE7B7"
    focus-ring: "#2DD4BF"      # lifted teal, ≥3:1 vs dark bg
typography:
  font-family: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
  scale:
    title: { size: "24px", weight: 600, line: "1.25" }
    subtitle: { size: "18px", weight: 600, line: "1.3" }
    body: { size: "15px", weight: 400, line: "1.5" }
    label: { size: "14px", weight: 500, line: "1.4" }
    caption: { size: "12px", weight: 400, line: "1.4" }
rounded:
  field: "10px"
  card: "12px"
  card-lg: "16px"
  pill: "999px"
  logo: "10px"
spacing:
  unit: "4px"
  scale: [4, 8, 12, 16, 24, 32, 48]
  field-height: "46px"
  card-max-width: "400px"
shadow:
  card-light: "0 4px 6px rgba(0,0,0,0.04)"
  card-dark: "0 4px 16px rgba(0,0,0,0.4)"
motion:
  duration: "160ms"
  easing: "ease"
  hover-tint-light: "#F8FAFC"   # ghost hover fill
  hover-tint-dark: "#0F1A30"
components:
  - button
  - text-field
  - workspace-card
  - search-field
  - auth-card
  - inline-error
  - method-row
  - toast
  - banner
  - skeleton-row
  - interstitial
---

# DESIGN.md — keycloak-multi-tenancy login & onboarding

> Visual identity spine for the Keycloak-served web auth screens. Direction: **Trust & Clarity**. Tokens are semantic and theme-aware (light + dark); per-tenant theming will later override the `primary`, `logo`, and `surface` tokens only. EXPERIENCE.md references these tokens by `{path.to.token}`. **This spine wins on conflict with any mock.**

## Brand & Style

Calm, spacious, dependable — **WhataTalk's teal brand**, modernized. The screens should feel like a secure front door: uncluttered, confident, never noisy. A single centered auth card on a soft mint page background (as the live product), one clear primary action per screen, generous whitespace, restrained color. Teal is the brand signal (primary action, focus); everything else is neutral. Trust is communicated through clarity and restraint, not decoration. No marketing flourish on the auth path; the moment is functional and respectful of the user's time. This evolves the current screens — same teal warmth, far more polish and consistency.

**Voice in visuals:** quiet confidence. One thing to do per screen. Color carries meaning (primary = the next step; danger = a problem), never ornament.

## Colors

Semantic, theme-aware. Authored light-first; dark flips surfaces and lifts primary for contrast.

| Token | Light | Dark | Use |
|---|---|---|---|
| `bg` | `#F8FAFC` | `#0B1220` | Page background |
| `surface` | `#FFFFFF` | `#111A2E` | Auth card, workspace cards |
| `ink` | `#0F172A` | `#E6EDF7` | Primary text |
| `muted` | `#64748B` | `#94A3B8` | Secondary text, hints |
| `border` | `#E2E8F0` | `#1F2A44` | Hairlines, field borders |
| `primary` | `#0F766E` | `#0F766E` | Primary action, focus ring, active (WhataTalk teal) |
| `primary-hover` | `#115E54` | `#0B5F58` | Primary hover |
| `danger` | `#DC2626` | `#F87171` | Errors |
| `danger-bg` | `#FEF2F2` | `#2A1416` | Error field/alert background |
| `success` | `#16A34A` | `#34D399` | Confirmation (accepted invite, etc.) |

**Contrast (verified):** `ink` on `surface` ≥ 12:1 both modes. `muted` on `surface` ≈ 4.7:1 (AA for body, not for ≥18px-only use). **Primary button** white-on-`primary` (WhataTalk teal `#0F766E`) = 4.65:1 both modes (AA). Error/success **text** use `danger-text`/`success-text` (AA); bright `danger`/`success` are for icons/fills only. Control boundaries use `border-strong` (≥3:1, WCAG 1.4.11); `border` is for decorative hairlines only.

**Glossary:** user-facing **"Account"** == backend **"tenant"** (`TenantModel`). Creating one is **"Create New Business."** Users are **"Agents"**; roles are **ADMIN / AGENT**. UI uses WhataTalk's existing vocabulary — never "workspace."

**Per-tenant theming overrides `primary` + `logo` ONLY** — never `ink`/`bg`/`border` or contrast guarantees. Any tenant `primary` must pass a 4.5:1-with-white gate at config time (auto-darken if it fails).

## Typography

System font stack (fast, native, no web-font load on the critical auth path). Five roles only — restraint over a large scale.

- **Title** 24/1.25, 600 — screen heading ("Sign in", "Create your workspace")
- **Subtitle** 18/1.3, 600 — section / workspace name
- **Body** 15/1.5, 400 — descriptions, helper text
- **Label** 14/1.4, 500 — field labels, button text
- **Caption** 12/1.4, 400 — fine print, status badges

## Layout & Spacing

4px base unit; scale `[4, 8, 12, 16, 24, 32, 48]`. Single centered **auth card**, max-width `400px`, on `bg`. Field height `46px`. Vertical rhythm inside the card: 16px between groups, 24px above the primary action. Generous breathing room — the card never feels cramped. Fully responsive: card fills width with 16px gutters on mobile, centers on larger viewports.

## Elevation & Depth

Near-flat. The auth card sits on `bg` with a 1px `border` hairline and a single soft shadow (`{shadow.card-light}` / `{shadow.card-dark}`). Workspace cards: `border-strong` boundary, no shadow at rest; a subtle border-color shift to `primary` on hover/focus over `{motion.duration}`. No layered or heavy shadows — depth is implied by the card-on-background relationship.

## Shapes

Soft, consistent rounding. Fields `10px`, cards `12px`, large containers `16px`, logos `10px`, status badges/pills `999px`. No sharp corners; no fully circular containers except avatars/status dots. Workspace logos are rounded squares with an initials fallback.

## Components

Behavioral specs live in EXPERIENCE.md; this is the visual contract.

- **Button** — full-width, height `46px`, radius `10px`, label 14/500. `primary`: `primary` bg, white text, `primary-hover` on hover. `ghost`: transparent, `border` hairline, `ink` text, `bg`-tint hover. Focus: 2px `focus-ring` outline, 2px offset.
- **Text field** — height `46px`, radius `10px`, 1px `border`; focus raises border to `primary` + focus ring. Label above (14/500). Error state: `danger` border, `danger-bg` fill, inline message below with a small `danger` icon.
- **Auth card** — `surface`, radius `16px`, hairline border + soft shadow, max-width `400px`, brand mark (rounded-square gradient `primary`) top-left or centered.
- **Account card** — row (as the live "Select Account" screen): avatar/logo (40px, initials fallback) · account name (subtitle) + **Roles: ADMIN/AGENT** (caption/muted) · full-row click target. `border-strong` boundary, `primary` border on hover/focus.
- **Search field** — text field variant with a leading search glyph; appears above the workspace list when count is high.
- **Method row** — secondary auth option (e.g. "Use a passkey", "Email me a magic link", "Sign in with SSO") rendered as a ghost/text row beneath the primary action, `muted` until hover.
- **Inline error** — `danger-text` + `danger` icon directly beneath the offending field; never a modal or toast for field validation.
- **Toast** — transient confirmation (e.g. "You've joined {Workspace}"); `surface`, hairline border, `success`/`success-text` accent, auto-dismiss + manual close; announced `aria-live="polite"`.
- **Banner** — persistent inline notice within the card (e.g. background-sync retry, "Not you? Decline"); `surface` with a `border-strong` left rule; never blocks the primary action.
- **Skeleton row** — loading placeholder for workspace cards: `border`-tinted blocks, `motion` shimmer (respect `prefers-reduced-motion` → static).
- **Interstitial** — full-card transient state ("Getting your workspace ready…"): centered spinner + caption; paired with text for reduced-motion users.

Elevation uses `shadow.card-*`; hover/transition uses `motion.*` — never raw literals in templates.

## Do's and Don'ts

**Do**
- Keep one primary action per screen.
- Use `primary` only for the single next step and focus.
- Preserve generous whitespace; let the card breathe.
- Degrade passkey UI gracefully to magic-link/password when unavailable.

**Don't**
- Add social-login buttons (out of scope by decision — no "Continue with Google" for now).
- Hardcode hex in templates — use semantic tokens (today's screens hardcode `#2c2f33`/`#00b4d8`; that is the anti-pattern this replaces).
- Stack multiple shadows or introduce a second accent color.
- Use color as the only signal for an error (pair with icon + text).
