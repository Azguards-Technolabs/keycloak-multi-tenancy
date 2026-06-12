# UX Spec Rubric Review — keycloak-multi-tenancy login & onboarding

Reviewed: 2026-06-11 · Reviewer: rubric pass over DESIGN.md + EXPERIENCE.md
Context skimmed (not critiqued): `.decision-log.md`, market research report.

Severity legend: **BLOCKER** (must fix before build) · **SHOULD-FIX** (gap/risk that will bite) · **NIT** (polish).

---

## Verdict

Both spines are unusually complete and tightly cross-referenced. DESIGN.md covers every rubric section; EXPERIENCE.md closes its IA and names protagonists with climax beats. The defects are mostly missing-surface coverage and a few unbacked visual assertions — not structural failures. No true blockers in coherence; the closest-to-blocker items are missing token coverage for asserted visuals and two surfaces that are claimed in IA but have no state/voice treatment.

---

## DESIGN.md

### Coverage (rubric sections)
- Brand & Style — present, strong.
- Colors w/ contrast — present, light+dark, with a stated contrast floor.
- Typography — present, 5 roles.
- Layout & Spacing — present.
- Elevation — present.
- Shapes — present.
- Components — present (7 components, visual contract).
- Do's/Don'ts — present.

### Findings

**SHOULD-FIX — Visual decisions asserted without tokens (shadows, hover tints, motion).**
- Elevation shadows are written as raw rgba literals inline (`0 4px 6px rgba(0,0,0,0.04)` light / `0 4px 16px rgba(0,0,0,0.4)` dark) but there is **no `shadow` token** in the front-matter token block. This is exactly the "hardcode hex in templates" anti-pattern the Don'ts forbid, applied to shadow. Add `shadow.card` (light/dark) tokens.
- Button `ghost` "`bg`-tint hover" and text-field hover are described qualitatively with no token for the tint/opacity. Same for workspace-card "border-color shift to `primary` on hover." Define a token or state rule (e.g. hover bg = `primary` @ 8%) so it is reproducible.
- No motion/transition tokens despite EXPERIENCE relying on spinners, skeletons, interstitials, and `prefers-reduced-motion`. Add a `motion` duration/easing token (and a reduced-motion zero state) so the two spines agree on timing.

**SHOULD-FIX — Contrast claims are asserted, not verified per-token.**
The "≥7:1 / ≥8:1 / ≥4.5:1" floor is stated but no per-pair check is shown. Spot-check flags two pairs to verify before locking:
- `muted` `#64748B` on `surface` `#FFFFFF` (light) ≈ 4.7:1 — passes AA for body but is **well under the 7:1 the doc claims for body text**. Muted is "secondary text/hints," so AA is fine, but the blanket "body text ≥7:1" sentence over-promises for muted. Scope the 7:1 claim to `ink` only.
- `success` `#16A34A` on `surface` (light) for any text use ≈ 3.4:1 — fine as an icon/fill, **fails 4.5:1 as text**. Confirm success is never used as small text (the toast "You've joined {Workspace}").

**NIT — `primary-hover` dark (`#2563EB`) equals `primary` light.** Intentional theme-flip, but worth a one-line comment so a later editor doesn't "fix" the apparent duplication.

**NIT — `logo` token referenced but absent from color block.** Per-tenant override sentence and the `rounded.logo` exist, but there is no `logo` color/asset token defined; the override contract ("overrides `primary` + `logo`") points at an undefined token. Define what `logo` is (asset slot, not color) or rename.

**NIT — `focus-ring` duplicates `primary` in both themes.** Fine, but then the separate token is non-load-bearing; keep it (future-proofs per-tenant) but note it.

---

## EXPERIENCE.md

### Coverage (rubric sections)
- Foundation — present.
- IA — present, self-pruning, with explicit surface-closure statement.
- Voice/Tone — present, with concrete microcopy.
- Component Patterns (behavioral) — present, reference `{components.*}`.
- State Patterns (empty/loading/error/success/edge) — present per surface (mostly; see gaps).
- Interaction Primitives — present.
- Accessibility Floor — present, AA, concrete.
- Key Flows — 3 named protagonists (Priya/Sam/Dana), each with a labeled **Climax**.
- Anti-patterns — present.

### Findings

**SHOULD-FIX — Two IA surfaces have no State Patterns and no Voice coverage.**
IA lists six surfaces (Login, Registration, Create workspace, Invitation, Workspace picker, **SSO entry**) and the surface-closure paragraph names five journeys. But:
- **SSO entry** appears in IA ("SSO entry replaces the legacy alias screen") and Foundation (enterprise SSO secondary) yet has **no State Patterns row, no key flow, and no voice beyond the one link label.** What does the corporate-domain-detected screen look like? What is the loading/error state when the IdP redirect fails? This surface is asserted but not specified — closure is claimed but not actually closed for SSO.
- **Registration** has microcopy ("Let's set up your account") and a flow (Priya) but **no State Patterns entry** (the State Patterns list jumps Login → Magic link → Passkey → Create workspace → Invitation → Picker; registration's own error/edge states — e.g. name empty, email already mid-registration — are unspecified).

**SHOULD-FIX — Voice/tone says title is "Let's set up your account" but flow prose says it "slides to 'Let's set up your account' — just her name," while the workspace step microcopy is "Name your workspace" in Voice but rendered as "name your workspace" in the flow and the placeholder example differs ("Priya's Studio" vs Voice's "e.g. Priya's Studio").** Minor, but lock one canonical string per surface so FreeMarker doesn't drift.

**SHOULD-FIX — Decline/reject path is under-specified vs the decision log.**
Decision log explicitly says: "preserve the external User Service status update + decline path; surface decline as a secondary, non-blocking option." EXPERIENCE auto-accepts the single invite and drops Sam in — but **there is no decline affordance described anywhere** in States or Flows. Auto-accept with no visible decline contradicts the carried decision and removes user consent to join. Specify where decline lives post-auto-accept (e.g. the confirmation toast offers "Leave workspace").

**NIT — Provisioning interstitial vs "no spinner-only feedback for reduced-motion."**
"Getting your workspace ready…" interstitial is text-paired (good), but confirm the magic-link "Check your email" and picker skeleton states also satisfy the a11y rule that reduced-motion users get text, not motion-only. Skeleton rows are motion-ish; state the reduced-motion fallback.

**NIT — "remember last-used method/workspace across sessions" needs a storage/consent note.** Cross-session memory on an auth screen implies a cookie/local hint; flag privacy/clearing behavior (and that it must degrade gracefully on a fresh device — covered for Dana implicitly, but not stated).

---

## Cross-spine coherence

**SHOULD-FIX — Foundation auth-method ordering contradicts the final decision (no-social REVISION).**
- Decision log REVISION fixes the hierarchy as **"[1] Email field → Continue (PRIMARY), routes to passkey/magic-link/password; [2] SSO secondary."** i.e. the lead is **email-first**, and passkey is one of the *routed* methods.
- EXPERIENCE Foundation states auth methods as **"passkey-first + magic link + password fallback."** "Passkey-first" reads as the *entry* primitive, which conflicts with the email-first entry that the rest of the doc (IA, Component Patterns "Email-first field," Priya flow) actually describes. The body is correct; the Foundation label is the outlier. Reword Foundation to "email-first entry; passkey-preferred among methods" to match the decision and the rest of the spec.

**GOOD — No-social is honored.** DESIGN Don'ts explicitly forbid social buttons; EXPERIENCE Foundation lists social out of scope. Consistent with the REVISION.

**GOOD — email-first, auto-accept-single-invite, minimal first-run, searchable picker** all present and consistent with decisions. Passkey KC 26.4+ dependency + graceful degradation is stated in both Foundation and the picked-direction header.

**NIT — Token reference style is mostly consistent but not total.** EXPERIENCE references `{components.text-field}`, `{components.button}`, `{colors.*.focus-ring}`, `{spacing.field-height}` — good. But several visual assertions in EXPERIENCE name no token where one exists: "skeleton rows," "brief confirmation toast," "quiet banner," "interstitial" are components/surfaces with **no matching entry in DESIGN's component list** (toast, banner, skeleton, interstitial are all undefined visually). Either add these to DESIGN.components or down-scope them. This is the biggest cross-spine gap: EXPERIENCE invents 4 visual surfaces DESIGN never contracts.

**NIT — "searchable picker" threshold mismatch.** DESIGN says search field "appears above the workspace list when count is high"; EXPERIENCE pins it precisely at "> 5"; Dana flow says "many." Harmonize to the `> 5` number in all three.

---

## Summary table

| # | Severity | Spine | Finding |
|---|---|---|---|
| 1 | SHOULD-FIX | DESIGN | Shadows/hover-tints/motion asserted with no tokens (violates own Don't) |
| 2 | SHOULD-FIX | DESIGN | Contrast floor over-promised for `muted`/`success` as text |
| 3 | SHOULD-FIX | EXPERIENCE | SSO entry + Registration surfaces lack State Patterns / voice — IA closure incomplete |
| 4 | SHOULD-FIX | EXPERIENCE | Decline path dropped vs decision-log requirement (auto-accept w/o consent exit) |
| 5 | SHOULD-FIX | Cross | Foundation "passkey-first" contradicts decided "email-first" entry |
| 6 | NIT | Cross | EXPERIENCE invents toast/banner/skeleton/interstitial — no DESIGN component contract |
| 7 | NIT | Both | Microcopy + search-threshold strings drift between docs |
| 8 | NIT | DESIGN | `logo` / `focus-ring` tokens referenced/duplicated without definition note |
