# Consistency & Feasibility Review — keycloak-multi-tenancy login & onboarding

Reviewed: DESIGN.md, EXPERIENCE.md (both 2026-06-11) against docs/auth-flows.md (current `anarsultanov/keycloak-multi-tenancy` extension on KC 26.0.7).
Date: 2026-06-11. Reviewer pass: internal consistency, decision consistency, feasibility.

Severity legend: **[blocker]** must resolve before build · **[should-fix]** resolve before sign-off · **[nit]** polish.

---

## 1. Internal consistency — EXPERIENCE `{token}` refs vs DESIGN

EXPERIENCE references DESIGN tokens via `{path}`. Cross-checked every reference:

| EXPERIENCE reference | Defined in DESIGN? | Verdict |
|---|---|---|
| `{components.text-field}` | frontmatter `components: text-field` + body "Text field" | OK |
| `{components.button}` | `components: button` + body "Button" | OK |
| `{components.method-row}` | `components: method-row` + body "Method row" | OK |
| `{components.workspace-card}` | `components: workspace-card` + body "Workspace card" | OK |
| `{components.search-field}` | `components: search-field` + body "Search field" | OK |
| `{components.inline-error}` | `components: inline-error` + body "Inline error" | OK |
| `{colors.*.focus-ring}` | `colors.light.focus-ring` + `colors.dark.focus-ring` | OK |
| `{spacing.field-height}` | `spacing.field-height: "46px"` | OK |

All token references resolve. No phantom tokens.

### Findings

- **[nit] Unreferenced component `auth-card`.** DESIGN defines `components: auth-card` and a body spec, but EXPERIENCE never references `{components.auth-card}` (it is the implicit container of every flow, so this is acceptable — flagged only for completeness). Not drift, just an unreferenced token.
- **[nit] Naming drift: "workspace" (UX) vs "tenant" (backend).** DESIGN/EXPERIENCE consistently say "workspace"; the backend exclusively says "tenant" (`create-tenant`, `select-active-tenant`, `tenantName`, `active-tenant-id`, all token claims). This is an intentional user-facing rename and is internally consistent within the UX docs — but the eng team must hold the mapping "workspace == tenant" everywhere. Worth one explicit glossary line so FreeMarker message keys and JS field names don't fork.
- **[nit] Theming scope wording mismatch.** DESIGN frontmatter intro (line 65) says per-tenant theming overrides "`primary`, `logo`, and `surface`"; the Colors section (line 90) and the user's own spine say overrides "`primary` + `logo` only". Internal contradiction on whether `surface` is themable. Pick one. (Per the stated decision spine: `primary`+`logo` only — so line 65's inclusion of `surface` is the drift.)

---

## 2. Decision-consistency — spine vs decisions

| Decision | DESIGN/EXPERIENCE alignment | Verdict |
|---|---|---|
| No social sign-in | DESIGN Don'ts ("Add social-login buttons… out of scope"); EXPERIENCE Out-of-scope + anti-patterns | Consistent |
| Email-first | EXPERIENCE IA + "Email-first field" component | Consistent |
| Passkey-first, needs KC 26.4+, graceful degrade | EXPERIENCE Auth methods + Passkey state + DESIGN Do ("Degrade passkey UI gracefully") | Consistent |
| Auto-accept single invite, preserve external User-Service, decline path | EXPERIENCE auto-accept + non-blocking User-Service failure | **Partial — see finding** |
| Minimal first-run | EXPERIENCE Registration "minimal: name"; Notion inspiration | Consistent |
| Searchable picker | EXPERIENCE search-field threshold + Dana flow | **Threshold mismatch — see finding** |
| Per-tenant theming later, override primary+logo only | DESIGN Colors line 90 | Consistent (modulo §1 line-65 drift) |

### Findings

- **[should-fix] Decline path effectively disappears under auto-accept.** Decision says "auto-accept single invite **+ decline path**". EXPERIENCE's auto-accept (Sam flow, line 95; State line 68) drops a single-invite user straight in with no decline affordance shown anywhere. The current backend (`review-invitations.ftl`) offers per-card Accept/**Reject**. The UX never states how a user declines a sole invitation (e.g., they were invited by mistake). Auto-accept removing the only decline surface is a real product decision that should be explicit, not implicit. Confirm intended behavior and add the escape hatch (or document the deliberate omission).
- **[should-fix] Search-threshold inconsistency.** EXPERIENCE says two different things: IA/State imply search "when count is high" / "many"; the Component Patterns line 58 hard-codes "membership count > 5". Dana's flow says "five client workspaces" and shows search — at exactly 5 the `>5` rule would hide it. Off-by-one against the headline persona. Pick a threshold and make Dana satisfy it (>=5 or set Dana to 6+).

---

## 3. Feasibility vs current backend (auth-flows.md)

Mapping each proposed capability to existing extension primitives (required actions `review-tenant-invitations`/`create-tenant`/`select-active-tenant`; authenticators `login-with-sso`/IdP-membership; FreeMarker templates) vs NEW work / KC upgrade.

| Proposed UX | Maps to existing? | Gap |
|---|---|---|
| Email-first routing (known→method, unknown→register, domain→SSO) | **No.** Today is standard KC login form + a *separate* `login-with-sso` authenticator that asks the user to type an IdP alias. | **NEW** authenticator/flow: an email-first lookup step that branches. Email-domain→IdP discovery does not exist (current SSO = manual alias entry, Flow 4). Requires new SPI authenticator + domain→IdP mapping config. |
| Passkey-first | **No.** KC 26.0.7; "No passkeys/magic link" per cross-cutting table. | **KC UPGRADE to 26.4+** (already flagged in both docs). Graceful-degrade path is sound. |
| Magic link | **No.** Not present today. | **NEW.** Magic link is not native standard KC the way the docs imply; needs an email-OTP/magic-link authenticator (custom or extension/KC capability) + `EmailSender` wiring. Biggest under-acknowledged build item — EXPERIENCE treats magic link as a baseline fallback, but it does not exist in the backend at all. |
| Auto-accept single invite | **Partial.** `review-tenant-invitations` exists and already auto-skips create-tenant when an invite is accepted, but it always **renders** `review-invitations.ftl` with manual Accept/Reject; there is no auto-accept-without-UI path. | **MODIFY** `ReviewTenantInvitations.evaluateTriggers/requiredActionChallenge`: when exactly 1 pending invite, accept programmatically and skip the screen. Net-new logic on the most fragile flow (it calls the external User Service synchronously). |
| "Getting your workspace ready…" async provisioning interstitial | **No.** `CreateTenant.createTenant(...)` is synchronous inside the required action; no async/interstitial state exists. | **NEW** behavior. Either tenant creation becomes async (significant) or the interstitial is cosmetic over a sync call. Note: invitation acceptance also makes a **synchronous external User-Service REST call** (Flow 2) — the "non-blocking, retry in background" UX in EXPERIENCE (line 68) contradicts the current synchronous, failure-prone implementation. Making that call non-blocking is real backend work. |
| Email-domain SSO detection | **No.** Manual alias entry only (Flow 4). | **NEW** (same item as email-first routing). |
| Searchable, theme-driven, branded tenant picker | **Partial.** `select-active-tenant` + `select-tenant.ftl` exist; auto-select-on-1 / show-on->1 logic already matches EXPERIENCE. | **MODIFY** template: add search/filter, last-used pin, move off hardcoded `#2c2f33`/`#00b4d8` inline CSS to theme tokens. Last-used-pinned-top requires persisting/reading last-used tenant (session note `active-tenant-id` exists; "last used across sessions" may need a user attribute). |
| Remember last-used auth method + last-used workspace across sessions | **Partial.** `active-tenant-id` is a *session* note (Flow 3/5); not persisted across sessions by default. No store for "last auth method". | **NEW** persistence (user attributes) for cross-session memory. |
| Move all styling to theme tokens (no hardcoded hex) | **No.** Templates carry hardcoded dark CSS + shipped `console.log` debug. | **REWRITE** of all four FTL templates onto DESIGN tokens; strip debug JS, dedupe i18n keys. Pure-frontend, low-risk, but touches every screen. |
| Deferred email verification (Priya: "no verification gate") | **Conflicts.** `review-tenant-invitations` trigger *requires* `emailVerified` (Required Actions table row 1). | **[blocker] see finding** — deferring verification breaks the invite-review trigger precondition. |

### Biggest implementation gaps the eng team must know

- **[blocker] Deferred email verification collides with the invite-review trigger.** EXPERIENCE's minimal first-run (Priya, line 91; IA "deferred verification") lets users in *without* verifying email. But the backend's `review-tenant-invitations` required action only fires when `emailVerified` is true. An invited user who skips verification (Sam-style) would **never** hit the invitation flow, so auto-accept would silently not run. This is the single highest-risk feasibility gap: the two headline UX decisions (deferred verification + auto-accept invite) are mutually exclusive under the current trigger logic. Resolve before build — either keep lightweight verification for invitees, or change the trigger precondition.
- **[blocker] Email-first + magic-link + domain-SSO are net-new authenticator work, not template tweaks.** Three of the marquee experiences (email-first routing, magic link, email-domain IdP discovery) have **zero** backing in the current extension. The current SSO is manual-alias-entry (Flow 4). This is new SPI authenticator development plus config (domain→IdP map), not a reskin. Scope accordingly.
- **[should-fix] External User-Service call is synchronous today; UX assumes resilient/async.** Invitation acceptance calls `UserServiceRestClient` synchronously (Flow 2) and is the documented failure point. EXPERIENCE promises "let the user in, retry in background, quiet banner" (line 68). Delivering that requires making the external call non-blocking with a retry/sync mechanism — backend work, and it sits on the auto-accept path that EXPERIENCE wants invisible.
- **[should-fix] Async tenant provisioning interstitial has no backend.** `CreateTenant` is synchronous; "Getting your workspace ready…" implies async provisioning. Decide cosmetic-over-sync vs real async before committing the copy.
- **[nit] Cross-session "last used" memory needs a persistence store.** `active-tenant-id` is a session note; remembering last-used tenant/method across sessions needs user attributes.

---

## Summary table

| # | Finding | Severity |
|---|---|---|
| F1 | Deferred verification breaks `review-tenant-invitations` (`emailVerified` precondition) → auto-accept silently won't fire | blocker |
| F2 | Email-first routing + magic-link + email-domain SSO are net-new authenticator/SPI work (current SSO = manual alias) | blocker |
| F3 | External User-Service call is synchronous; UX assumes non-blocking/retry | should-fix |
| F4 | Decline path absent under single-invite auto-accept (decision says auto-accept **+ decline**) | should-fix |
| F5 | Search threshold `>5` contradicts "many"/Dana's exactly-5 persona (off-by-one) | should-fix |
| F6 | Async provisioning interstitial has no backend (`CreateTenant` is sync) | should-fix |
| F7 | Theming scope drift: DESIGN line 65 (`primary+logo+surface`) vs line 90 (`primary+logo` only) | nit |
| F8 | "workspace" vs "tenant" naming — needs explicit glossary so FTL/JS don't fork | nit |
| F9 | Cross-session last-used memory needs persistence (session note insufficient) | nit |

No phantom token references found; all `{token}` paths resolve to DESIGN definitions.
