# PRD Quality Review — WhataTalk Login & Onboarding UX Redesign

## Overall verdict

The PRD is well-structured and substantially above average for a brownfield UX redesign: FRs are numbered, UJs are protagonist-driven, NFRs have real bounds, and the addendum bridges to engineering without over-specifying the PRD itself. The three risks that could block handoff are: (1) the "Decline" path on auto-accepted invites has no FR backing it, leaving UX and engineering to invent behavior; (2) the lockout/rate-limit message is explicitly `[TBD]` in the microcopy table but FR-L-10 says "calm explanatory message with recovery path" — the tension is unresolved and OQ-3 only partially covers it; (3) no counter-metrics exist for any Success Metric, which is a launch-tier gap for a chain-top PRD feeding architecture.

---

## 1. Decision-readiness — adequate

The PRD makes real decisions (dynamic SSO alias preserved, auto-accept single-invite, async User-Service call, system-generated usernames kept). Trade-offs are named where they exist (reliability vs. simplicity on the async path; passkey upgrade dependency). Open Questions are genuinely open — OQ-1 through OQ-5 and OQ-8 are actionable with named owners and due dates.

### Findings

- **high** Missing decision on "Not you? Decline" consequence (§4.3 UJ, §5.3 FR-INV-3) — The UJ shows a "Not you? Decline" action in the toast. FR-INV-3 mandates the copy and a "secondary action." No FR specifies what happens when the agent taps Decline: is the auto-accepted invite revoked? Is the agent logged out? Is a support form shown? This is a product decision, not an implementation detail. *Fix:* Add FR-INV-8 specifying the Decline outcome (revoke acceptance + route to where, or log out + show contact info).

- **high** Lockout/rate-limit message is TBD in the canonical microcopy (Addendum §6) while FR-L-10 commits to "calm explanatory message that includes a recovery path." These two statements contradict each other. OQ-3 asks about User-Service SLA, not lockout copy, so it does not resolve this. *Fix:* Either write the lockout copy in the addendum microcopy table and remove `[TBD]`, or add an explicit Open Question with an owner and due date.

- **medium** OQ-8 (passkey prompt frequency) is open but FR-PK-3 says "After a successful password-based login, the system presents an optional prompt ... The agent may dismiss it." — without resolving whether dismissal is permanent. If OQ-8 is unresolved, FR-PK-3 is underspecified. *Fix:* Mark FR-PK-3 with a `[ASSUMPTION: shown once per device; dismiss is permanent]` and link it to OQ-8 so the implementation assumption is visible.

---

## 2. Substance over theater — strong

Personas drive genuine UX decisions: Priya's single-account skip (FR-AS-1), Sam's "last used" pin and search threshold (FR-AS-4, FR-AS-5), Dana's auto-accept path (FR-INV-2). The vision in §1 is product-specific — the synchronous User-Service call, the dark-theme hex values, the passkey adoption stat are concrete, not generic. NFR bounds are real: 44×44px touch targets, 2px focus rings, 500ms skeleton SLA, ≥320px viewport, 2s TTI.

### Findings

- **medium** "Passkey unavailable variant" in §4.1 UJ says "The passkey affordance is absent. She types her password. Login completes normally." This is correct behavior but the variant adds no new information beyond FR-L-4. UJ variants should model edge-case decisions, not restate FRs. *Fix:* Replace this variant with a more decision-relevant edge case (e.g., passkey credential exists but device hardware is unavailable — does the affordance show anyway with a fallback CTA, or is it suppressed?).

- **low** §7 success metric "Time from invite click to product landing: ≤ 2 steps after login" measures steps, not time. The metric name says "time" but the unit is steps. *Fix:* Either rename to "Steps from invite click to product landing" or replace the unit with a time bound (e.g., "≤ 45 seconds on median device").

---

## 3. Strategic coherence — strong

The PRD has a legible thesis: the invite-click-to-product path is the highest-leverage activation moment; reduce it to near-zero friction while simultaneously reducing login friction for returning agents. Every feature cluster (passkeys, auto-accept, async User-Service, visual debt cleanup, WCAG) serves this thesis. The KC upgrade (G-6) is correctly positioned as an enabler rather than a goal.

### Findings

- **medium** No counter-metrics are defined for any Success Metric. For a launch-tier PRD, this is a gap: if passkey adoption climbs, does password login success rate signal a problem? If auto-accept drops the "Decline" rate to near zero, does that mask bad invite targeting? Counter-metrics force honest measurement. *Fix:* Add one counter-metric per primary SM — e.g., "passkey registration rate should not exceed X% without a corresponding drop in support tickets" or "Decline rate on auto-accepted invites should remain below 5%; above that, re-evaluate auto-accept threshold."

- **low** G-5 (WCAG 2.1 AA) is a compliance goal, not a user-experience goal. It belongs in NFRs but not in the Goals table, which should list outcomes that validate the thesis. *Fix:* Move G-5 to a note on NFR-A-1 and replace it with a goal tied to the activation thesis (e.g., "Zero auth-screen usability barriers for screen-reader users").

---

## 4. Done-ness clarity — adequate

Most FRs are testable. The addendum is disciplined about not over-constraining implementation while still providing enough detail (microcopy table, token system, FTL template list, accessibility HTML patterns). FR IDs are contiguous and unique within each section.

### Findings

- **critical** FR-INV-5 / FR-INV-6 split is incomplete for done-ness: FR-INV-5 says the call is "non-blocking and asynchronous" and "does not gate the agent's entry." FR-INV-6 says "a background retry is attempted" and "a quiet banner is shown only if a manual retry is required." Neither FR specifies: (a) how many retries, (b) what the retry interval is, (c) what "manual retry required" means from the user's perspective (a button? a re-login?). OQ-3 asks about the SLA but is directed at backend engineering, not product. Without answers, story acceptance criteria will be invented by the implementor. *Fix:* Add `[ASSUMPTION]` bounds: e.g., "up to 3 retries, 30-second backoff; if all fail, banner shows 'We had trouble syncing your account — tap to retry' with a retry button." These can be overridden in the tech spec, but a product default must exist.

- **high** FR-L-6 says "Email me a sign-in link" is "visible only when the authenticated user has a verified email address on file." On the login screen, the user has not yet authenticated — the system does not know whether the user has a verified email until after credential check. This is either a sequence-of-events error or the requirement means "visible only when the submitted username has a verified email." The distinction matters for both UX (does the option appear before or after username entry?) and backend (does it require an API call to check?). *Fix:* Clarify: "The magic link option is shown after the username field loses focus (or is submitted), and only if the username maps to a Keycloak account with `emailVerified = true`." If that requires a backend lookup, note it as an `[ASSUMPTION]`.

- **high** FR-AS-5 says "A search field is displayed when the agent belongs to more than four Accounts." The threshold (>4) is a product decision but no rationale is given. More importantly, there is no FR for what the search matches against (account name only? role? both?). Story creation will need this. *Fix:* Add a child requirement: "Search filters account rows by account name prefix match (case-insensitive). Role is not a search filter."

- **medium** FR-L-9 mandates inline errors "below the form." Addendum §5 shows the error div above the input and linked via `aria-describedby`. "Below the form" vs. "below the specific field" is an ambiguity that will produce a UX/engineering disagreement. *Fix:* Change FR-L-9 to "displayed inline, adjacent to the relevant field" and let the addendum HTML pattern (below the input, above the next field) be the authority.

- **medium** NFR-P-1 ("interactive within 2 seconds") does not define "interactive" or the connection baseline ("standard broadband"). "Interactive" for a login form means the username field accepts input and the submit button is clickable — clarify. "Standard broadband" is ambiguous; use a concrete baseline (e.g., "simulated 10 Mbps / 40ms RTT"). *Fix:* "The login form must accept user input within 2 seconds on a simulated 10 Mbps / 40ms RTT connection (Lighthouse throttling profile)."

- **low** FR-INV-7 says "The system does not present a blank screen at any point during invite acceptance or business provisioning." "Business provisioning" is not defined in the glossary and does not appear elsewhere in the PRD. If this means User-Service provisioning, say so. If it means something broader, define it. *Fix:* Replace "business provisioning" with "User-Service post-acceptance provisioning" or add the term to a glossary.

---

## 5. Scope honesty — strong

Non-Goals do real work: registration flow, social login, WhatsApp auth, email templates, admin tenant switcher, and username assignment are all explicitly excluded and the rationale is implicit (working, out of problem scope). `[ASSUMPTION]` tags appear on 8 of the 7 success metrics and on NFR-P-1 and NFR-B-1. Open Questions are low in count (5 open, 2 resolved) relative to the scope — appropriate for a launch-tier PRD.

### Findings

- **medium** There is no Non-Goal for passkey management (delete/rename/list registered passkeys). FR-PK-1 enables registration; FR-PK-3 offers a post-login prompt. An engineer reading this could reasonably infer that passkey management UI is in scope. A credential management screen is a non-trivial scope addition. *Fix:* Add to Non-Goals: "Passkey credential management (listing, renaming, or deleting registered passkeys) is out of scope for this release."

- **medium** The KC upgrade (G-6 / Addendum §1) is treated as a given delivery, but OQ-5 asks whether it needs a staged rollout. If OQ-5 is not resolved before architecture, the upgrade strategy could invalidate the passkeys feature scope. The PRD does not state what happens to passkeys (FR-PK) if the upgrade is deferred. *Fix:* Add a dependency note under FR-PK-4: "If the KC upgrade is delayed beyond this sprint, FR-PK-1 through FR-PK-5 are deferred. All other FR groups are KC-version-independent." This isolates scope impact explicitly.

---

## 6. Downstream usability — adequate

Glossary terms are used consistently: "Agent," "Account," "Invited Agent," "User-Service" appear with stable meaning across FRs, UJs, and SMs. UJ protagonists are named (Priya, Sam, Dana). FR IDs are contiguous and non-overlapping within sections (FR-L-1–12, FR-AS-1–8, FR-INV-1–7, FR-PK-1–5). The addendum FTL template table maps directly to FR groups.

### Findings

- **high** No explicit glossary section. "Account," "Agent," "User-Service," "Inviter," and "pending invitation" are defined inline in §3 and scattered through §4, but there is no single reference table. The addendum microcopy table uses `{Account}` and `{Inviter}` as template tokens — if "Inviter" means "inviting agent's display name" (resolved OQ-7) that is buried in the Open Questions table, not surfaced at point-of-use. A downstream UX or story author will not find it. *Fix:* Add a §0 or §9 Glossary table defining: Account, Agent, Inviter, User-Service, pending invitation, emailVerified. Cross-reference OQ-7 resolution to the Inviter definition.

- **medium** Success Metrics reference FR IDs in the "Source" column but do not reference NFR IDs where relevant. The WCAG metric cites NFR-A-1 through NFR-A-6, which is correct. The "Login-related support tickets" metric cites only FR-L-9 and FR-L-10, not NFR-A-3/A-4 (error accessibility), which also drives this metric. Incomplete sourcing makes traceability brittle. *Fix:* Add NFR-A-3, NFR-A-4 to the support-ticket metric source column.

- **low** FR-INV section jumps from FR-INV-7 directly. If FR-INV-8 is added (Decline outcome, as recommended under §1), the ID continues naturally. No ID gap currently, but note that FR-INV-3's "Not you? Decline" action is semantically a sub-requirement of FR-INV-2 — it should either be FR-INV-3a/3b or explicitly acknowledge it is a consequence of auto-accept that needs its own FR. *Fix:* Add FR-INV-8 for Decline behavior (see §1 finding).

---

## 7. Shape fit — strong

This PRD is correctly shaped for a brownfield UX redesign feeding architecture and story creation. The existing-vs-new distinction is clear: §1 names the current broken behaviors (hardcoded hex, synchronous User-Service call, SSO raw input); §2 Non-Goals excludes working flows; §5 FRs specify the new target state. UJs are appropriate for a multi-stakeholder auth product: three protagonists cover the three dominant flows (returning single-account, multi-account, first-time invited). The addendum correctly holds implementation detail without polluting the PRD.

### Findings

- **medium** There is no UJ for a passkey registration moment. FR-PK-3 defines the prompt behavior but no UJ shows Priya (or any persona) going through "set up a passkey for the first time." This is a new interaction pattern (post-login prompt, browser credential creation dialog, confirmation) that is meaningfully different from Priya's existing login. Without a UJ, UX design will be done without a user story frame. *Fix:* Add UJ §4.4 "Priya — Passkey Registration (Post-Login Prompt)" covering: post-login prompt appears, she taps "Set up a passkey," browser credential dialog, success confirmation, dismiss variant.

- **low** The "Passkey unavailable variant" in §4.1 and "User-Service failure variant" in §4.3 are presented as inline variants of the main UJs rather than separate UJ entries. For chain-top story creation, these variants will likely generate separate stories. Consider whether they should be promoted to first-class UJ entries (§4.1a, §4.3a) to make story scoping explicit. *Fix:* Either promote to separate UJ sub-entries with their own ID or add a note that each variant maps to a discrete story.

---

## Mechanical notes

- **NFR-T-2** hard-codes `#0F766E` as the primary brand color in the PRD body text, but the addendum CSS token block also hard-codes it. Both are consistent — no conflict. However, the PRD NFR should reference the token (`--color-primary`) and defer the hex value to the addendum, which is the right layer of abstraction.
- **Addendum §6 microcopy table** — Rate-limit/lockout row is `[TBD]`. This is the only TBD in an otherwise complete microcopy table. It should be resolved before architecture starts (it may drive a new Keycloak required-action template).
- **§8 Open Questions table** — OQ-6 and OQ-7 are correctly struck through. Consider removing them from the table entirely in the next revision to reduce cognitive load; resolved items create noise in a working document.
- **FR-L-2** — `autocomplete="username webauthn"` is not a valid single autocomplete token per the HTML spec. The correct approach is `autocomplete="username"` on the username field; `webauthn` is a token recognized by browsers for passkey discovery alongside `username`, but the combination syntax should be validated against current browser support. The addendum should add a note on this.
