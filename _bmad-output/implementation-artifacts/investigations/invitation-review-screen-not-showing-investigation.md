# Investigation: Accept/reject invitation screen not showing for agents

## Hand-off Brief

1. **What happened.** User-reported: the accept/reject invitation review screen is not appearing for agents with exactly one pending invitation. Root cause confirmed: the code does this on purpose, per story 5-2 AC-1 ("no accept/reject list rendered" for exactly one invitation — auto-accept with a toast instead).
2. **Where the case stands.** Not a defect — this is a requirement change against a shipped, twice-reviewed acceptance criterion. User confirmed on 2026-08-13 the desired behavior is to always show the screen, even for one invitation. Flipping this touches coupled, already-shipped mechanics (toast, decline-link, `InviteDeclineResource`, `ToastInfoResource`) built specifically because AC-1 skipped the screen (Finding 4).
3. **What's needed next.** This is a scope/acceptance-criteria change on a `done` story, not a bug fix — route through `bmad-correct-course` to decide how to update story 5-2 (and possibly 5-3) and what becomes of the toast/decline path, before any code changes.

## Case Info

| Field            | Value                                                                                                    |
| ---------------- | ---------------------------------------------------------------------------------------------------------- |
| Ticket           | N/A                                                                                                          |
| Date opened      | 2026-08-13                                                                                                   |
| Status           | Concluded — root cause Confirmed; hand-off is a scope/story change, not a code fix                            |
| System           | keycloak-multi-tenancy custom SPI (Java, Keycloak realm), theme in sibling repo `azguards-keycloak-custom-theme` |
| Evidence sources | Repo source code (`src/main/java/dev/sultanov/keycloak/multitenancy/**`); sibling theme repo FTL templates; git history; existing story files for Epic 5 |

## Problem Statement

User-reported (verbatim, from CLI args): "accept / reject invitation screen not showing for agents"

No ticket, log excerpt, or repro steps were supplied yet — this opened from a free-text description. Treated as Hypothesis #1 below; not yet independently confirmed against a specific agent/session.

## Evidence Inventory

| Source                                                                 | Status    | Notes                                                                                          |
| ------------------------------------------------------------------------ | --------- | ------------------------------------------------------------------------------------------------ |
| `authentication/requiredactions/ReviewTenantInvitations.java`             | Available | Required action implementing the review/accept/reject screen; read in full (lines 1-431)        |
| `_bmad-output/implementation-artifacts/5-2-auto-accept-single-invitation-with-toast-decline.md` | Available | Story defining single-invitation auto-accept behavior (not yet read in full) |
| `_bmad-output/implementation-artifacts/5-3-multi-invite-picker.md`        | Available | Story defining the multi-invitation picker screen (not yet read in full)                        |
| `azguards-keycloak-custom-theme/.../login/review-invitations.ftl`         | Available | Theme template for the screen; exists in sibling repo, not yet read                              |
| Git log on `ReviewTenantInvitations.java`                                 | Available | 5 commits touching this file, most recent "feat:login flow refactor" (d108137) — not yet diffed  |
| Server logs / repro session for the specific agent reporting the issue    | Missing   | Would show whether `evaluateTriggers` added the required action, how many invitations were found, and which branch (`success` / auto-accept / challenge) executed |
| Exact agent identity / invitation count for the reported case            | Missing   | Determines which of three code paths (0 / 1 / 2+ invitations) is actually in play               |

## Investigation Backlog

| # | Path to Explore                                                                                   | Priority | Status | Notes |
| - | ----------------------------------------------------------------------------------------------------- | -------- | ------ | ----- |
| 1 | Confirm how many pending invitations the affected agent(s) actually have                              | High     | Open   | Splits the case into "by-design auto-accept" vs. "picker screen broken" |
| 2 | Read `review-invitations.ftl` in the theme repo for rendering-side failures                           | Medium   | Open   | Only relevant if invitation count is 2+ |
| 3 | Diff recent commits on `ReviewTenantInvitations.java` (esp. `d108137 feat:login flow refactor`) for a regression | Medium | Open   | Login flow refactor is a plausible place to have broken required-action registration |
| 4 | Check `evaluateTriggers` session-note short-circuit (`REVIEWED_INVITATIONS` client note) for premature "already reviewed" state | Medium | Open | Would suppress the screen even with pending invitations, on any count |
| 5 | Read stories 5-2 and 5-3 in full for the intended behavior/acceptance criteria to compare against report | Low | Open | Grounds "not showing" against what "showing" is supposed to mean |

## Timeline of Events

| Time | Event | Source | Confidence |
| ---- | ----- | ------ | ---------- |
| (unset) | Not yet established | — | — |

## Confirmed Findings

### Finding 1: The screen is intentionally skipped for 0 or 1 pending invitations

**Evidence:** `src/main/java/dev/sultanov/keycloak/multitenancy/authentication/requiredactions/ReviewTenantInvitations.java:90-109`

**Detail:** `requiredActionChallenge` branches on `invitations.size()`: empty → `context.success()` (no screen); exactly 1 → `autoAcceptInvitation(...)` (silent auto-accept, no screen, per story 5-2's "auto-accept single invitation with toast decline"); 2+ → builds and challenges with `review-invitations.ftl` (the picker screen, per story 5-3). Only the 2+ branch ever shows a screen.

### Finding 2: The required action is only added when invitations exist and the session hasn't already been marked reviewed

**Evidence:** `ReviewTenantInvitations.java:44-74` (`evaluateTriggers`)

**Detail:** If `context.getAuthenticationSession().getClientNote(REVIEWED_INVITATIONS)` is `"true"`, the method returns immediately without checking for invitations — the required action is never (re-)added for the rest of that authentication session. Separately, the required action is added only if `provider.getTenantInvitationsStream(realm, user).findAny().isPresent()` — if the user has zero pending invitations, no required action is registered at all, so no screen (and no auto-accept) occurs.

### Finding 3: The 2+ invitation screen also requires a verified email

**Evidence:** `ReviewTenantInvitations.java:90` — `if (ObjectUtils.isNotEmpty(user.getEmail()) && user.isEmailVerified())`

**Detail:** If the required action was added (invitations exist) but the user's email is empty or unverified, `requiredActionChallenge` does nothing at all — falls through with only a warn log (`ReviewTenantInvitations.java:111`), no `context.success()` and no challenge. This is a silent no-op branch: the user would appear stuck or the required action would simply not resolve, without any screen appearing.

### Finding 5: Auto-accept triggers purely on pending-invitation count, independent of existing tenant memberships

**Evidence:** `ReviewTenantInvitations.java:88-100` — `invitations = provider.getTenantInvitationsStream(realm, user).toList()` counts only pending `TenantInvitationModel` rows; the `invitations.size() == 1` branch has no check against `provider.getTenantMembershipsStream(realm, user)` before calling `autoAcceptInvitation`. The only membership check inside `autoAcceptInvitation` is the "P9" already-member guard (`:151-154`, `:169-174`), which only suppresses a duplicate grant when the invitation is to a tenant the agent **already** belongs to — it does not gate the single-invitation branch itself, and does not apply when the invitation is to a **different**, second tenant.

**Detail:** An agent who is already an active member of Tenant A and receives one new invitation to Tenant B will, on next login, hit the exact same silent `autoAcceptInvitation` path as a brand-new agent: membership to Tenant B is granted automatically with only a toast shown afterward, no review screen — regardless of how many tenants they already belong to. This matches the literal wording of story 5-2 AC-1 ("Given the Agent has exactly one pending invitation"), which carries no qualifier limiting it to a user's first tenant. **Consequence for the fix:** the branch condition to change is purely "pending invitation count," so changing `size == 1` to route to the screen instead of auto-accept correctly covers this case with no additional membership-aware logic needed.

### Finding 4: Reversing single-invitation auto-accept has coupled downstream impact — it isn't an isolated branch flip

**Evidence:** `5-2-...md` AC-2/AC-3 (toast + "Not you? Decline" secondary action) and File Change Map (lines 254-271); `resource/InviteDeclineResource.java`, `resource/ToastInfoResource.java`, and the account theme (`azguards-keycloak-custom-theme/.../account/index.ftl`) all exist solely to serve the auto-accept path's post-login toast/decline UX.

**Detail:** The multi-invite path (`review-invitations.ftl` + `processAction()`, story 5-3) already handles accept **and** reject generically via the `acceptedTenants`/`rejectedTenants` form fields (`ReviewTenantInvitations.java:297-374`) — it is not restricted to 2+ invitations structurally. So routing `size == 1` into the same challenge as `size > 1` is plausible without new form-handling code. But doing so removes the reason the toast/decline mechanism (`InviteDeclineResource`, `ToastInfoResource`, account-page toast JS) exists in the single-invitation case — that mechanism was purpose-built by story 5-2 specifically because AC-1 skipped the screen. Making this change means deciding what happens to that already-shipped, twice-reviewed toast/decline path: keep it only for a "0 becomes 1 later" edge case, repurpose it, or retire it.

## Deduced Conclusions

### Deduction 1: "Screen not showing" is ambiguous between at least three distinct behaviors

**Based on:** Findings 1-3

**Reasoning:** The codebase has three legitimate reasons an agent could see no screen: (a) by-design auto-accept with exactly one invitation, (b) `REVIEWED_INVITATIONS` short-circuit suppressing re-evaluation, (c) unverified-email fall-through leaving the required action unresolved. Each has a different fix (or "not a bug") verdict.

**Conclusion:** Cannot proceed to root-cause without narrowing which of these matches the reported case — this is registered as Hypothesis 1-3 below rather than picking one.

## Hypothesized Paths

### Hypothesis 1: Report describes the by-design single-invitation auto-accept, not a defect

**Status:** Confirmed

**Theory:** The affected agent has exactly one pending invitation, which is auto-accepted silently by design (Finding 1) with only a toast shown afterward, not a screen.

**Supporting indicators:** `5-2-auto-accept-single-invitation-with-toast-decline.md` AC-1 (lines 24-30) is explicit and unambiguous: *"Given the Agent has exactly one pending invitation ... Then the invitation is accepted automatically (no accept/reject list rendered)."* This is the literal, reviewed, shipped acceptance criterion (Status: done, two adversarial review passes on 2026-06-20 and 2026-06-22).

**Would confirm:** Confirming the affected agent had exactly 1 pending invitation at login time.

**Would refute:** Affected agent had 2+ pending invitations, or 0 invitations, at the time of the report.

**Resolution:** User confirmed on 2026-08-13: the desired behavior is that the accept/reject screen should show even for exactly one pending invitation. This is not a code defect — the code correctly implements AC-1 as written. The report is a **requirement change against a shipped, reviewed acceptance criterion**, not a bug in the implementation.

### Hypothesis 2: A prior session already marked `REVIEWED_INVITATIONS`, suppressing re-evaluation

**Status:** Open

**Theory:** A previous login (or a previous required action in the same auth session) set the `tenantInvitationsReviewed` client note to `"true"`, so `evaluateTriggers` returns early on a later login attempt within the same authentication session even though invitations are pending.

**Supporting indicators:** This note lives on the authentication session, not the user session, so its blast radius is normally limited to one browser auth flow — but any flow that re-enters `evaluateTriggers` within the same auth session (e.g. a retry after a different required action) would be affected.

**Would confirm:** Reproducing with a fresh authentication session showing the same "no screen" result would refute this; showing the note is `"true"` before evaluation, with invitations still pending, would confirm it.

**Would refute:** Screen appears correctly on a completely fresh login attempt.

**Resolution:** —

### Hypothesis 3: Unverified/missing email on the agent account silently blocks the challenge

**Status:** Open

**Theory:** The affected agent(s) have an email that is empty or not yet verified, so `requiredActionChallenge` falls through the `if` at line 90 without calling `context.success()` or `context.challenge()`, leaving the required action outstanding but never rendering anything.

**Supporting indicators:** This is the only branch in the reviewed code with no terminal action (no `success()`, no `challenge()`) — a structurally suspicious no-op.

**Would confirm:** Affected agent's Keycloak user record has `emailVerified = false` or a blank email.

**Would refute:** Affected agent has a verified email on file.

**Resolution:** —

### Hypothesis 4 (user's original framing): The screen genuinely fails to render/appear despite 2+ invitations and a verified, fresh session

**Status:** Open

**Theory:** A regression (possibly introduced in `d108137 feat:login flow refactor`, the most recent commit touching this file) broke either required-action registration, the FTL rendering, or routing to the challenge for the multi-invite picker case.

**Supporting indicators:** None yet — not diffed.

**Would confirm:** Diff of `d108137` shows a change to `evaluateTriggers`, `requiredActionChallenge`, the `review-invitations.ftl` template, or a related registration file (e.g. `META-INF/services` factory registration) that plausibly breaks this path.

**Would refute:** Diff shows this file/template untouched by recent changes, and manual repro with 2+ invitations + verified email shows the screen correctly.

**Resolution:** —

## Missing Evidence

| Gap                                                                 | Impact                                                              | How to Obtain                                                                 |
| ---------------------------------------------------------------------- | --------------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| Number of pending invitations for the affected agent(s) at repro time  | Determines whether this is Hypothesis 1 (by-design), 2, 3, or 4        | Query `TenantInvitationEntity` for the affected user, or ask the reporting agent   |
| Email verification status of the affected agent(s)                     | Confirms or refutes Hypothesis 3                                       | Query the Keycloak user record (`emailVerified` attribute)                        |
| Server logs around the reported repro (the `log.debugf`/`log.infof`/`log.warnf` lines in this file are copious) | Would directly show which branch executed | Keycloak server logs filtered by user ID / timeframe of the report                |
| Diff of `d108137 feat:login flow refactor`                             | Confirms or refutes Hypothesis 4 (regression)                          | `git show d108137` / `git diff` against the prior commit                          |

## Source Code Trace

| Element       | Detail                                                                                                    |
| ------------- | -------------------------------------------------------------------------------------------------------- |
| Error origin  | Not yet localized — three plausible branch points in `ReviewTenantInvitations.java` (lines 55-59, 90, 97-109) |
| Trigger       | Agent login while holding pending tenant invitations                                                      |
| Condition     | Varies by hypothesis: invitation count (0/1/2+), `REVIEWED_INVITATIONS` client note state, email verification status |
| Related files | `authentication/TenantsBean.java` (form data model), `review-invitations.ftl` (theme repo), `resource/InviteDeclineResource.java`, `resource/InviteLinkVerificationResource.java` |

## Conclusion

**Confidence:** High — Confirmed. Root cause is `ReviewTenantInvitations.java:97-100` correctly implementing `5-2-auto-accept-single-invitation-with-toast-decline.md` AC-1 as written and shipped (Status: done, reviewed twice). The "screen not showing for one invitation" is the code working as designed, not a defect. The open item is a product decision: change AC-1 so the screen always renders (1+ invitations), and decide the fate of the AC-2/AC-3 toast/decline mechanism that exists only because AC-1 currently skips the screen.

Hypotheses 2 and 3 (session short-circuit, unverified-email no-op) and Hypothesis 4 (regression in `d108137`) remain Open/unexplored — they were not needed to explain the reported case once Hypothesis 1 was confirmed by the user, but they are real latent issues worth tracking separately (see Missing Evidence and Side Findings) if a *different* agent reports the screen missing despite having 2+ invitations.

Finding 5 confirms the blast radius: auto-accept fires on pending-invitation count alone, with no regard for existing tenant memberships. An already-onboarded agent invited to a second tenant is silently auto-joined the same way a brand-new agent is. The fix (route `size == 1` to the screen instead of auto-accept) naturally covers this case without extra membership-aware logic.

## Recommended Next Steps

### Fix direction

Change `ReviewTenantInvitations.requiredActionChallenge()` so the `invitations.size() == 1` branch routes to the same `review-invitations.ftl` challenge as `size > 1`, instead of `autoAcceptInvitation(...)`. Mechanically small (the multi-invite form/`processAction()` already generically handles accept+reject for any count, per Finding 4) — but it is a scope change against a shipped, reviewed acceptance criterion with coupled downstream mechanics, not a one-line bug fix. Before touching code, resolve:
- Does AC-2/AC-3 (toast + "Not you? Decline" link, `InviteDeclineResource`, `ToastInfoResource`, account-page toast JS) get retired, or kept for some other trigger?
- Does story 5-2 get amended in place, or does a new story supersede its AC-1/AC-2/AC-3?
- Any interaction with story 5-3's "multi-invite picker" framing/copy, which was written assuming 2+ invitations only.

### Diagnostic

Not applicable for this case — resolved by user statement. If a future report describes the screen missing for a 2+ invitation agent, use Hypotheses 2-4 and the Missing Evidence rows below as the starting point for that separate investigation.

## Reproduction Plan (for the desired-behavior verification, post-fix)

1. Agent with exactly 1 pending invitation, verified email → expect `review-invitations.ftl` challenge to render (not auto-accept).
2. Agent with 2+ pending invitations → expect unchanged behavior (already covered by story 5-3).
3. Decide and verify: does the toast/decline path (`InviteDeclineResource`, `ToastInfoResource`) still fire for any case after the change, or is it fully retired?

## Side Findings

- The `autoAcceptInvitation` catch-all (`ReviewTenantInvitations.java:226-233`) swallows any unexpected exception during auto-accept and calls `context.success()` anyway — by design (comment `D1`), but worth noting as a place where a screen would silently never appear even on outright failure, with only an error log as a trace.

## Follow-up: 2026-08-13
