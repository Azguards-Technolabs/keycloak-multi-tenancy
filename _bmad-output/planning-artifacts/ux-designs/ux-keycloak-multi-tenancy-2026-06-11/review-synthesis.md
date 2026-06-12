# Reviewer Panel — Synthesis & Resolutions (2026-06-11)

Three lenses ran: `review-rubric.md`, `review-accessibility.md`, `review-consistency.md`.

## Resolved in the spines
| # | Severity | Finding | Resolution |
|---|---|---|---|
| 1 | blocker (a11y 1.4.3) | Dark primary button white text 3.68:1 | Dark `primary` → `#2563EB` (5.17:1 white text); dark `focus-ring` → `#60A5FA` (≥3:1 vs dark bg). Per-tenant `primary` overrides must pass a 4.5:1 contrast gate. |
| 2 | blocker (feasibility) | Deferred verification vs invite trigger needs `emailVerified` | **Clicking the emailed invite link marks `emailVerified=true`** → invited users (Sam) always hit the invite flow. Deferred verification applies to **self-serve only** (Priya). Documented as backend requirement. |
| 3 | blocker (feasibility) | Email-first routing, magic link, domain-SSO = net-new | Added **Backend Deltas** section to EXPERIENCE flagging net-new SPI/authenticator work + KC 26.4+ for passkeys, so eng scopes it. |
| 4 | should-fix | `success`/`danger` text contrast (light) | Added `success-text` `#15803D` and `danger-text` `#B91C1C` (AA for text); bright `success`/`danger` reserved for icons/fills. |
| 5 | should-fix | No decline path on single-invite auto-accept | Auto-accept lands the user in, but with an explicit **"Not you? Decline"** action in the confirmation banner → calls the existing external decline path. |
| 6 | should-fix | SSO + Registration unspecified (IA not closed) | Added State Patterns rows + an SSO IdP-redirect-failure state and Registration states. |
| 7 | should-fix | Foundation "passkey-first" contradicts email-first | Reworded: **email-first entry; passkey-first among methods**. |
| 8 | should-fix | Sync external User-Service vs "non-blocking" | Flagged in Backend Deltas as required work to make acceptance non-blocking. |
| 9 | should-fix (a11y) | Autocomplete + focus management gaps | Added `name`/`current-password`/`one-time-code` autocomplete; specified focus transfer on step change + return-from-email focus + SR announcement of passkey fallback. |
| 10 | should-fix (a11y 1.4.11) | Hairline border 1.2:1 < 3:1 for control boundaries | Added `border-strong` (`#94A3B8` / dark `#475569`) for input/control boundaries; decorative dividers stay hairline (1.4.11 exempt). |
| 11 | nit | Search threshold `>5` vs Dana's 5 | Threshold lowered to **> 4**. |
| 12 | nit | Theming scope drift (surface vs primary+logo) | Aligned: per-tenant overrides **`primary` + `logo` only**. |
| 13 | nit | Toast/banner/skeleton/interstitial uncontracted | Added to DESIGN component list. |
| 14 | nit | workspace == tenant ambiguity | Added glossary line. |
| 15 | should-fix | Shadow/motion as raw literals | Added `shadow` + `motion` tokens to DESIGN. |

All `{token}` references resolved cleanly (no phantom tokens) per consistency review.
