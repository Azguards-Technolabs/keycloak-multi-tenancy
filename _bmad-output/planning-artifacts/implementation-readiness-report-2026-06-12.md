---
stepsCompleted: [1, 2, 3, 4, 5, 6]
date: '2026-06-12'
project_name: 'WhataTalk — Login & Onboarding UX Redesign'
documentsAssessed:
  - _bmad-output/planning-artifacts/prds/prd-keycloak-multi-tenancy-2026-06-11/prd.md
  - _bmad-output/planning-artifacts/prds/prd-keycloak-multi-tenancy-2026-06-11/addendum.md
  - _bmad-output/planning-artifacts/architecture.md
  - _bmad-output/planning-artifacts/epics.md
  - _bmad-output/planning-artifacts/ux-designs/ux-keycloak-multi-tenancy-2026-06-11/DESIGN.md
  - _bmad-output/planning-artifacts/ux-designs/ux-keycloak-multi-tenancy-2026-06-11/EXPERIENCE.md
---

# Implementation Readiness Assessment Report

**Date:** 2026-06-12
**Project:** WhataTalk — Login & Onboarding UX Redesign

## 1. Document Inventory

| Type | Document | Format | Status |
|------|----------|--------|--------|
| PRD | `prds/prd-keycloak-multi-tenancy-2026-06-11/prd.md` (+ `addendum.md`) | Folder-based | ✅ Found |
| Architecture | `architecture.md` | Whole | ✅ Found |
| Epics & Stories | `epics.md` | Whole | ✅ Found |
| UX Design | `ux-designs/.../DESIGN.md` + `EXPERIENCE.md` | Two-part spine | ✅ Found |

**Duplicates:** None.
**Missing required documents:** None.
**Note:** A separate `docs/architecture.md` exists as the brownfield project scan (supporting context only); the architecture *decision* document at `planning-artifacts/architecture.md` is the one assessed.

## 2. PRD Analysis

### Functional Requirements (33)

**Login (FR-L, 12):**
- FR-L-1: Username + password as primary credential inputs.
- FR-L-2: Autocomplete tokens — username `username webauthn`, password `current-password`.
- FR-L-3: Passkey "Use your passkey" affordance as primary option above password when registered.
- FR-L-4: Passkey affordance silently absent when unsupported/unregistered — no dead-end.
- FR-L-5: "Sign in with SSO" secondary link → restyled dynamic alias entry (not on primary screen).
- FR-L-6: "Email me a sign-in link" shown only when username maps to `emailVerified = true`.
- FR-L-7: "Forgot Password" link.
- FR-L-8: "Create New Business" secondary link → existing (out-of-scope) flow.
- FR-L-9: Inline auth errors below form ("That username or password doesn't match"); no modals.
- FR-L-10: Calm lockout/rate-limit message with recovery path.
- FR-L-11: Interactive elements ≥ 44×44 px.
- FR-L-12: Login screen title "Login to Agent Account."

**Account Selection (FR-AS, 8):**
- FR-AS-1: Skip selection screen when exactly one Account.
- FR-AS-2: Present picker when ≥ 2 Accounts.
- FR-AS-3: Row = logo/initials + Account name + role (ADMIN/AGENT).
- FR-AS-4: Most-recently-used Account pinned with "Last used" label.
- FR-AS-5: Search when > 4 Accounts (name only, case-insensitive, prefix-first); no-match state.
- FR-AS-6: Skeleton placeholder rows while loading.
- FR-AS-7: Zero Accounts → Create New Business.
- FR-AS-8: Picker title "Select Account."

**Invitation Acceptance (FR-INV, 8):**
- FR-INV-1: Invite-link click sets `emailVerified = true`.
- FR-INV-2: Auto-accept exactly one pending invitation (no list).
- FR-INV-3: Auto-accept toast "{Inviter} invited you to join {Account}" + "Not you? Decline".
- FR-INV-4: Multi-invite picker (accept/decline each independently).
- FR-INV-5: User-Service call non-blocking and asynchronous; does not gate entry.
- FR-INV-6: On failure, admit Agent + background retry; quiet retry banner; no blocking error.
- FR-INV-7: No blank screen during acceptance/provisioning.
- FR-INV-8: "Not you? Decline" revokes acceptance, removes membership, logs out.

**Passkeys (FR-PK, 5):**
- FR-PK-1: Passkey registration tied to username credential.
- FR-PK-2: Passkey auth as first-class login option when registered.
- FR-PK-3: Optional post-login enrollment prompt ("Sign in faster next time…").
- FR-PK-4: Requires Keycloak 26.4+ runtime.
- FR-PK-5: Graceful fallback to password on passkey failure/cancel (no error for passkey step).

### Non-Functional Requirements (18)

- **Accessibility (NFR-A-1..6):** WCAG 2.1 AA (hard gate); visible 2px focus rings; aria-live + aria-describedby errors; non-color error signal; visible labels; prefers-reduced-motion.
- **Performance (NFR-P-1..2):** Login interactive < 2 s; picker render/skeleton < 500 ms.
- **Theming (NFR-T-1..4):** Semantic tokens (no hardcoded hex); teal `#0F766E` + centered mint card; light + dark; system font only.
- **Browser/Device (NFR-B-1..3):** Current+previous Chrome/Firefox/Safari/Edge; ≥ 320 px mobile; WebAuthn graceful degradation.
- **Security/Reliability (NFR-S-1..3):** No console.* in prod JS; no duplicate i18n keys; User-Service off critical login path.

### Additional Requirements (Technical Addendum + Architecture)

- **KC upgrade 26.0.7 → 26.4.x** (enabling prerequisite; full replacement + rollback) and extension recompile/staging validation.
- WebAuthn SPI integration; new `MagicLinkAuthenticator`; invite-link verification endpoint; auto-accept logic in `review-tenant-invitations`; async User-Service refactor + retry flag + FTL-readable retry signal.
- Shared CSS design-token theme; preserve token/session contracts (`active_tenant`, mappers, tenant-switch re-mint); extend Zipkin tracing + test harness.
- **Out of scope (do not modify):** register.ftl + registration flow, login-oauth-grant.ftl, email templates, admin tenant switcher, username generation.

### PRD Completeness Assessment

The PRD is **strong and implementation-ready**: requirements are uniquely numbered, written as testable capabilities, and complemented by a technical addendum that maps each FR to a concrete backend/template change, microcopy, and a hygiene checklist. Scope boundaries are explicit. **Eight open questions (OQ-1..8)** are tracked, of which OQ-6 and OQ-7 are resolved. The remaining open items (notably **OQ-3** User-Service SLA → retry timing, and **OQ-8** passkey prompt frequency) are assumptions, not blockers, but should be confirmed before the affected stories (5.4, 3.4) enter development. Several success-metric baselines are unmeasured (OQ-1, OQ-2) — a measurement gap, not a planning gap.

## 3. Epic Coverage Validation

### Coverage Matrix (Functional Requirements → Story)

| FR | Requirement (abbrev.) | Story | Status |
|----|-----------------------|-------|--------|
| FR-L-1 | Username + password primary inputs | 2.1 | ✓ Covered |
| FR-L-2 | Autocomplete tokens | 2.1 | ✓ Covered |
| FR-L-3 | Passkey affordance above password | 3.1 | ✓ Covered |
| FR-L-4 | Passkey affordance silently absent | 3.2 | ✓ Covered |
| FR-L-5 | "Sign in with SSO" secondary link | 2.3 | ✓ Covered |
| FR-L-6 | Conditional "Email me a sign-in link" | 2.4 | ✓ Covered |
| FR-L-7 | "Forgot Password" link | 2.1 | ✓ Covered |
| FR-L-8 | "Create New Business" link | 2.1 | ✓ Covered |
| FR-L-9 | Inline auth errors | 2.2 | ✓ Covered |
| FR-L-10 | Calm lockout messaging | 2.2 | ✓ Covered |
| FR-L-11 | ≥ 44×44 px targets | 1.3 (+ 2.1) | ✓ Covered |
| FR-L-12 | Title "Login to Agent Account" | 2.1 | ✓ Covered |
| FR-AS-1 | Skip picker when 1 Account | 4.1 | ✓ Covered |
| FR-AS-2 | Picker when ≥ 2 Accounts | 4.1 | ✓ Covered |
| FR-AS-3 | Row = logo/name/role | 4.2 | ✓ Covered |
| FR-AS-4 | Last-used pin | 4.2 | ✓ Covered |
| FR-AS-5 | Conditional search + no-match | 4.3 | ✓ Covered |
| FR-AS-6 | Skeleton rows | 4.4 | ✓ Covered |
| FR-AS-7 | Zero Accounts → Create Business | 4.1 | ✓ Covered |
| FR-AS-8 | Title "Select Account" | 4.2 | ✓ Covered |
| FR-INV-1 | Invite-link sets emailVerified | 5.1 | ✓ Covered |
| FR-INV-2 | Auto-accept single invite | 5.2 | ✓ Covered |
| FR-INV-3 | Auto-accept toast + decline | 5.2 | ✓ Covered |
| FR-INV-4 | Multi-invite picker | 5.3 | ✓ Covered |
| FR-INV-5 | Non-blocking async User-Service | 5.4 | ✓ Covered |
| FR-INV-6 | Background retry + quiet banner | 5.4 | ✓ Covered |
| FR-INV-7 | No blank screens | 5.2, 5.4 | ✓ Covered |
| FR-INV-8 | Decline revokes + logs out | 5.2 | ✓ Covered |
| FR-PK-1 | Username-bound passkey registration | 3.3 | ✓ Covered |
| FR-PK-2 | Passkey auth first-class option | 3.1 | ✓ Covered |
| FR-PK-3 | Post-login enrollment prompt | 3.4 | ✓ Covered |
| FR-PK-4 | Requires KC 26.4+ | 1.1 | ✓ Covered |
| FR-PK-5 | Graceful fallback to password | 3.2 | ✓ Covered |

### NFR Coverage (summary)

All 18 NFRs are covered: accessibility (NFR-A-1..6) and theming/browser/hygiene (NFR-T, NFR-B, NFR-S-1/2) are established in **Epic 1** (Stories 1.2–1.6) and verified per-screen in feature epics; NFR-P-1 → 2.1, NFR-P-2 → 4.4, NFR-S-3 → 5.4.

### Missing Requirements

**None.** Every PRD FR maps to at least one story with addressing acceptance criteria. No story implements a capability absent from the PRD (the only additions — the design-token system, components, and platform upgrade — trace to the Architecture, Addendum, and UX specs, which are sanctioned inputs).

### Coverage Statistics

- Total PRD FRs: **33**
- FRs covered in epics: **33**
- **FR coverage: 100%**
- NFR coverage: 18 / 18 (100%)
- Additional requirements (AR-1..13 + OOS guardrail): fully mapped

## 4. UX Alignment Assessment

### UX Document Status

**Found** — two-part spine: `DESIGN.md` (visual identity, tokens, components) + `EXPERIENCE.md` (flows, states, interaction, a11y floor). Both are marked `status: final`, `revision: 2 (reconciled to live WhataTalk product)`.

### UX ↔ PRD Alignment

- ✅ **Journeys match in substance:** returning single-Account login (passkey-first), multi-Account selection, and invited-agent auto-accept all appear in both PRD §4 and EXPERIENCE.md "Key Flows."
- ✅ **Vocabulary consistent:** Account (= `TenantModel`), Agent, ADMIN/AGENT, "Create New Business" — both docs forbid "workspace."
- ✅ **Scope boundaries match:** Create New Business, social login, WhatsApp auth, email templates, admin switcher all out of scope in both.
- ✅ **Microcopy mostly aligned** to the Addendum's canonical table (title, SSO, passkey prompt, picker title, decline action).

### UX ↔ Architecture Alignment

- ✅ FreeMarker-only presentation + shared CSS design-token theme: UX assumes exactly this; Architecture provides it (AR-10, cross-cutting "Design-token theming system").
- ✅ Passkeys require KC 26.4+: UX foundation explicitly notes "target 26.4+"; Architecture makes the upgrade the enabling first story.
- ✅ Non-blocking invite acceptance: UX state pattern demands it; Architecture's resilience/async concern + AR-8 deliver it.
- ✅ Performance (NFR-P): system-font-only + skeleton states support the < 2 s / < 500 ms targets.
- ✅ No UI component in the UX spine lacks an architectural home — all 11 components map to the token/theme system and FTL templates.

### Alignment Issues / Inconsistencies Found

| # | Severity | Issue | Detail | Recommendation |
|---|----------|-------|--------|----------------|
| UX-1 | 🟡 Medium | **Stale `primary` color in DESIGN.md body table** | The DESIGN.md "Colors" markdown table lists `primary #2563EB / #3B82F6` (blue), but the same file's frontmatter `colors.light.primary` is `#0F766E` (teal), the contrast note says "WhataTalk teal `#0F766E`", and PRD NFR-T-2 + the Addendum token set both mandate teal. The body table appears un-updated from a pre-reconciliation revision. | **Canonical = teal `#0F766E`.** Correct the DESIGN.md Colors table so a dev implementing Story 1.2 can't accidentally pick blue. Captured in Story 1.2 ACs (tokens reference DESIGN.md frontmatter values). |
| UX-2 | 🟢 Low | **Invite toast copy differs across docs** | PRD FR-INV-3 + Addendum: "{Inviter} invited you to join {Account}". EXPERIENCE.md success state: "You've joined {Account}". | Pick one canonical string. **PRD wins by default** (names the inviter — higher activation value). Story 5.2 already uses the PRD string; confirm. |
| UX-3 | 🟢 Low | **Persona names swapped between PRD and UX** | PRD §4: Sam = multi-Account, Dana = invited. EXPERIENCE.md: Sam = invited, Dana = multi-Account (five). The *journeys* are identical; only the names are crossed. | Cosmetic — no implementation impact. Align persona names in a future doc pass to avoid confusion when referencing journeys. |

### Warnings

None blocking. UX-1 (color) should be resolved before Story 1.2 enters development so the token file is authored against the correct brand value — it is the single source other screens inherit.

## 5. Epic Quality Review

Validated against the create-epics-and-stories best practices: user value, epic independence, story sizing, forward-dependency prohibition, AC quality, and brownfield fit.

### Best-Practices Compliance Checklist

| Epic | User value | Independent | Stories sized | No forward deps | Entities just-in-time | Clear ACs | FR traceable |
|------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 — Foundation | ⚠️ (foundation) | ✅ | 🟡 | ✅ | ✅ | ✅ | ✅ |
| 2 — Login | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 3 — Passkeys | ✅ | ✅ (uses E1+E2) | ✅ | ❌ (see EQ-1) | ✅ | ✅ | ✅ |
| 4 — Account Selection | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 5 — Invitation | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

### 🔴 Critical Violations

None.

### 🟠 Major Issues

- **EQ-1 — Epic 3 internal story order creates a forward dependency.** Story 3.1 (*Passkey-first authentication*) is sequenced before Story 3.3 (*Username-bound passkey registration*), but a passkey must be **registered before it can be used to authenticate** — 3.1's preconditions and end-to-end test depend on 3.3's output. This violates the "no story depends on a later story" rule.
  - **Recommendation:** Reorder Epic 3 to **registration → authentication → fallback → enrollment prompt**, i.e. 3.1 Registration, 3.2 Passkey-first auth, 3.3 Graceful fallback, 3.4 post-login enrollment prompt. (Enrollment prompt 3.4 then naturally feeds the registration story.) Alternatively, merge auth+registration into one story if a dev session can hold both. **Resolve before sprint planning.**

- **EQ-2 — Epic 1 is a foundation epic without standalone end-user value.** All of Epic 1's coverage is NFR/AR/UX-DR; no FR-* lands here, and the design-system stories deliver value only once a feature epic consumes them. By the strict "every epic delivers user value" rule this is a deviation.
  - **Why it's accepted (documented rationale):** (a) Architecture explicitly mandates the Keycloak 26.4.x upgrade as the *first implementation story* (Story 1.1) — it is a hard prerequisite, not optional scaffolding; (b) the shared CSS token/component system is a genuine cross-cutting dependency — pushing it into each feature epic would re-author the same files repeatedly, which is the *file-churn anti-pattern* the standards warn against. Consolidation was explicitly considered and chosen. Each Epic 1 story still has a concretely testable outcome (build/load, token resolution, axe-core scan, lint).
  - **Recommendation:** Accept as-is; keep Epic 1 strictly to shared foundation (no feature behavior leaking in).

### 🟡 Minor Concerns

- **EQ-3 — Story 1.4 bundles six component styles** (inline-error, method-row, toast, banner, skeleton-row, interstitial). Sized for one session as CSS-only work, but if it runs long, split feedback components (error/method-row) from list/transient components (toast/banner/skeleton/interstitial) into 1.4a / 1.4b.
- **EQ-4 — Cross-epic coupling on `login.ftl`.** FR-L-3/FR-L-4 (passkey affordance on the login screen) are owned by Epic 3 though the screen is Epic 2's. Not a forward dependency (login works without passkeys), but the dev implementing Epic 3 will edit Epic 2's template. Already justified under the file-churn analysis (WebAuthn coherence); flagged so sequencing keeps Epic 2 before Epic 3.
- **EQ-5 — Epic 4 Story 4.1's "picker-present" branch leans on 4.2.** The single-Account skip path is fully testable in 4.1; the ≥2-Account branch routes to a picker that 4.2 builds out. 4.1 should land a minimal picker stub so it's independently verifiable, with 4.2 enriching rows. Minor.

### Dependency & Brownfield Notes

- **Epic independence:** confirmed — Epics 2/4/5 each function on Epic 1 alone; Epic 3 legitimately builds on Epic 1 + Epic 2 (allowed: N may use N-1). No circular dependencies.
- **No starter template:** correct for a brownfield fork; Architecture says none applies and instead mandates the upgrade-first story — honored by Story 1.1.
- **Brownfield integration stories present:** staging validation + version-compatibility (1.1), token/session contract preservation (4.1/AR-11), async refactor of an existing sync call (5.4).
- **Entities just-in-time:** no upfront schema; existing JPA entities preserved; WebAuthn reuses Keycloak's credential store.

## 6. Summary and Recommendations

### Overall Readiness Status

**READY — pending two quick pre-sprint corrections.**

The planning set is comprehensive and internally consistent: **100% FR coverage (33/33)**, all 18 NFRs and 13 ARs mapped, PRD ↔ UX ↔ Architecture ↔ Epics in strong alignment, clean epic independence, and no critical violations. Two items should be fixed before Sprint Planning (both small); the rest are minor or accepted-with-rationale.

### Critical Issues Requiring Immediate Action

None blocking. No 🔴 critical violations were found.

### Issues to Address Before Sprint Planning (recommended)

1. **EQ-1 (Major) — Reorder Epic 3 stories.** Put passkey **registration before authentication** (registration → auth → fallback → enrollment prompt). Current order has authentication depending on a not-yet-built registration story. ~5-minute edit to `epics.md`.
2. **UX-1 (Medium) — Fix the stale `primary` color in DESIGN.md.** The Colors body table says blue `#2563EB`; canonical brand is teal `#0F766E` (frontmatter + Addendum + PRD NFR-T-2). Correct it so Story 1.2 authors the token file against the right value.

### Lower-Priority Items (can proceed; resolve in-flight)

3. **UX-2 (Low)** — Lock the invite-toast string ("{Inviter} invited you to join {Account}" per PRD wins). Story 5.2 already uses it.
4. **OQ-3 / OQ-8 (PRD open questions)** — Confirm User-Service retry/backoff timing (Story 5.4) and passkey-prompt frequency (Story 3.4) before those stories enter dev. Flagged inline in `epics.md`.
5. **EQ-3 / EQ-5 (Minor)** — Watch Story 1.4 sizing (six components); have Story 4.1 stub a minimal picker so it's independently testable.
6. **UX-3 (Cosmetic)** — Persona names are swapped between PRD and UX (journeys identical); align in a future doc pass.

### Recommended Next Steps

1. Apply fixes **EQ-1** and **UX-1** (both quick).
2. Confirm **OQ-3** and **OQ-8** with the relevant owners (Backend / Product-UX).
3. Proceed to **Sprint Planning** (`bmad-sprint-planning`) to sequence the 22 stories — recommended order **Epic 1 → 2 → 3 → 4 → 5**, with Story 1.1 (Keycloak upgrade) gating everything.

### Final Note

This assessment reviewed 6 documents across 5 dimensions and identified **9 findings** (0 critical, 2 major, 1 medium, 6 minor/cosmetic/open-question). None block planning. Address EQ-1 and UX-1, confirm the two open questions, and the plan is implementation-ready as-is.

---

*Assessment by: Paige (Technical Writer / PM-readiness role) · BMad `bmad-check-implementation-readiness` · 2026-06-12*
