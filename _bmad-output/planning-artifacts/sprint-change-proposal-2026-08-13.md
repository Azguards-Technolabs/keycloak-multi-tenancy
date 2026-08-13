# Sprint Change Proposal — 2026-08-13

**Trigger:** Investigation `_bmad-output/implementation-artifacts/investigations/invitation-review-screen-not-showing-investigation.md`
**Requested by:** Asif
**Status:** Approved 2026-08-13 — implementation handed off (Story 5.5)

---

## 1. Issue Summary

Story 5.2 (`5-2-auto-accept-single-invitation-with-toast-decline.md`, done) implemented AC-1 exactly as specified in the PRD (G-3, FR-INV-2): when an invited Agent has **exactly one** pending invitation, it is silently auto-accepted — no accept/reject screen is ever shown, only a post-login toast with a "Not you? Decline" link.

**New requirement (confirmed 2026-08-13):** the accept/reject review screen must always show for **one or more** pending invitations — never auto-accept. This applies regardless of whether the Agent already belongs to other Accounts (confirmed by code trace: `ReviewTenantInvitations.java:88-100` branches purely on pending-invitation count, with no membership check).

This is a **PRD-level reversal**, not a story-level tweak: it directly contradicts a stated goal (G-3), three functional requirements (FR-INV-2, FR-INV-3, FR-INV-8), and a success metric target. Per your direction, this is being treated as **a new requirement superseding G-3** (product decision changed), not a correction of a past mistake.

**Additional finding surfaced during impact analysis:** Epic 6 / Story 6.1 (`6-1-auth-screen-mockup-parity.md`, done) documents AC-3/AC-4 — a full-screen `invite-accepted.ftl` confirmation that was supposed to *already* supersede the toast for the single-invite case. Tracing the shipping commit (`c775367`, an ancestor of `main`) and both repos' full git history shows this was **never actually implemented** — no `invite-accepted.ftl`, no `challengeInviteAccepted()`/`processAction` proceed/decline handling exist anywhere. Per your direction, this is treated as **moot** under the new unified design (the never-built confirmation screen is no longer needed) and flagged as a documentation/status-integrity side finding, not re-investigated here.

## 2. Impact Analysis

### Epic Impact

| Epic | Impact |
|---|---|
| **Epic 5 — Invited Agent Onboarding** | Stories 5.2 and 5.3 converge on one mechanism. 5.2's distinct "auto-accept + toast" identity is retired; 5.3's "picker" screen generalizes down to cover 1+ invitations (a single row when there's one). Epic summary needs rewording (no longer "zero decision screens"). |
| **Epic 6 — Auth Screen Mockup Parity** | Story 6.1 AC-3/AC-4 (never-implemented invite-accepted confirmation) become moot — nothing to build or un-build, since they don't exist in code. Status accuracy flagged as a separate doc-integrity issue (see Side Findings). |
| Other epics (1–4) | No impact — unrelated flows. |

No new epic needed; no epic removed; no resequencing required (this is a retrofit onto already-shipped work, not future planning).

### Artifact Conflicts

**PRD** (`prd.md`) — conflicts confirmed, edits proposed below. MVP scope is not reduced, but three requirements and one goal are rewritten, and one success-metric target changes as a direct consequence (see Decision Needed).

**Epics** (`epics.md`) — Epic 5 summary, FR-INV-2/3/4 cross-references, AR-7 description, UX-DR9 "applied" note, Story 5.2 and 5.3 acceptance criteria, Story 6.1 AC-3/AC-4 all need updates.

**Architecture** (`architecture.md`) — one summary bullet (line 34-36) references "auto-accept single invite with toast + decline"; needs rewording. No structural/component/data-model change — still a required action + FTL challenge, same extension points.

**UX designs** — `UX-DR9` (toast component, `DESIGN.md`) is scoped only to this flow per `epics.md:243` ("Epic 1 (component) → Epic 5 (applied)"). No other epic applies it. It becomes an unused-but-still-valid shared component; no UX doc edit required, just noted as now-dormant.

**Code** (secondary artifact, checklist 3.4):
- `ReviewTenantInvitations.java` — remove the `size == 1` auto-accept branch; route it into the same `review-invitations.ftl` challenge as `size > 1`. `processAction()` already handles accept/decline generically per invitation — no new form-handling code needed.
- `ToastInfoResource.java`, `InviteDeclineResource.java`, the three `TOAST_*` constants, and the account-theme toast JS (`account/index.ftl`) all become **fully dead code** — confirmed by grep: the toast notes have exactly one producer (the auto-accept branch being removed) and one consumer (`ToastInfoResource`). `InviteDeclineResource` exists solely to revoke a membership granted moments earlier by auto-accept and force a logout; once accept/reject happens *before* any grant (as the multi-invite path already does via `processAction`'s `rejectedTenants` branch — no grant, no logout needed), there is no remaining scenario that calls it. **Recommendation: retire all four**, not just stop triggering them.
- Tests: `UserServiceAsyncTest.singleAutoAccept_toastNotesSetUnconditionally` (tests the exact behavior being removed — needs rewrite or removal), `InviteDeclineTest` (all 3 tests assume "auto-accept fires, then decline" as setup — needs rewrite or removal), `MultiInvitePickerTest` (verify no ">1"-only assumptions before reuse for the 1+ case).

### Path Forward Evaluation

| Option | Viable? | Effort | Risk |
|---|---|---|---|
| **1. Direct Adjustment** — modify 5.2/5.3, add a new story to carry the code change, update PRD/epics/architecture in place | **Yes — recommended** | Medium (branch-flip is small; the toast/decline retirement + 3 test files is the bulk of the work) | Low-Medium (well-isolated to one required action + its two dependent resources) |
| **2. Rollback** — revert 5.2 and/or 6.1 | Not viable | — | 5.2 introduced infrastructure (extraction of `autoAcceptInvitation`, tracing, `Constants`) still needed conceptually; 6.1 never touched this code, nothing to roll back |
| **3. PRD MVP Review** — reduce/redefine MVP scope | Not applicable | — | This isn't a scope reduction; it's a requirement correction, fully covered by Option 1 |

**Recommended:** Option 1 — Direct Adjustment, via a new story (5.5) that supersedes 5.2's auto-accept behavior, plus PRD/epics/architecture edits below.

## 3. Recommended Approach

Direct Adjustment. Rationale: the mechanism change is small and well-understood (Findings 4 & 5 in the investigation case file already establish that `processAction()` needs no new logic); the larger cost is document consistency (PRD/epics/architecture) and retiring now-dead code cleanly (toast, decline, three tests) rather than leaving orphaned endpoints and constants behind.

## 4. Detailed Change Proposals

### 4.1 PRD (`_bmad-output/planning-artifacts/prds/prd-keycloak-multi-tenancy-2026-06-11/prd.md`)

**G-3** (line 41)
```diff
- | G-3 | Auto-accept single pending invitations so invited Agents land in the product without a manual approval step. |
+ | G-3 | Present an explicit accept/reject review step for every pending invitation (one or more), so invited Agents make an informed decision before joining an Account. |
```
Rationale: new requirement supersedes the original auto-accept goal.

**FR-INV-2** (line 157) — retire, behavior removed entirely
```diff
- | FR-INV-2 | After authentication, if the Agent has exactly one pending invitation, the system auto-accepts it without requiring the Agent to interact with an accept/reject list. |
+ | FR-INV-2 | *(Superseded 2026-08-13 — auto-accept removed. See FR-INV-4, which now covers one or more pending invitations.)* |
```

**FR-INV-3** (line 158) — retire, no separate post-hoc toast for this flow
```diff
- | FR-INV-3 | After auto-acceptance, a toast notification is displayed with the copy "{Inviter's display name} invited you to join {Account}" and a "Not you? Decline" secondary action. |
+ | FR-INV-3 | *(Superseded 2026-08-13 — no auto-acceptance event to confirm. Accept/decline is now an explicit choice on the review screen, FR-INV-4.)* |
```

**FR-INV-4** (line 159) — generalized to own the unified behavior
```diff
- | FR-INV-4 | If the Agent has more than one pending invitation after authentication, the system presents an invite picker. Each row shows the Account name and Inviter. The Agent can accept or decline each invitation independently. |
+ | FR-INV-4 | If the Agent has one or more pending invitations after authentication, the system presents an invite review screen (a single row when there is one invitation). Each row shows the Account name and Inviter. The Agent can accept or decline each invitation independently, regardless of any existing Account memberships. |
```

**FR-INV-8** (line 163) — retire, decline-before-grant makes forced logout unnecessary
```diff
- | FR-INV-8 | When the Agent taps "Not you? Decline" on the auto-accept toast, the system revokes the invitation acceptance, removes the Agent from the Account, and logs the Agent out, returning them to the login screen. |
+ | FR-INV-8 | *(Superseded 2026-08-13 — decline now happens before any membership is granted, via FR-INV-4's review screen. No post-hoc revocation or forced logout is needed; declining simply revokes the invitation and notifies the inviter.)* |
```

**Journey 4.3 — Dana** (lines 108-112)
```diff
- 4. After authentication, she has exactly one pending invitation. It is auto-accepted. No list to manage.
- 5. A toast confirmation appears: "{Inviter} invited you to join {Account}" with a "Not you? Decline" action.
- 6. Dana lands in the product inside that Account.
-
- **Multiple invites variant:** Dana was invited to two Accounts. After authentication, she sees an invite picker — one row per Account — and can accept or decline each independently.
+ 4. After authentication, she sees an invite review screen — one row per pending invitation, even if she has only one.
+ 5. Dana taps Accept on the Account's row.
+ 6. Dana lands in the product inside that Account.
+
+ (This is now one unified behavior for one or more invitations — not a "variant.")
```

**Success metrics table** (line 233) — **Decision Needed**, see below.

### 4.2 Epics (`_bmad-output/planning-artifacts/epics.md`)

**Epic 5 summary** (lines 270-271 and 753-755)
```diff
- Invited Agents go from invite email to inside their Account with zero decision screens. Delivers invite-link email verification, auto-accept of a single invitation with toast + "Not you? Decline", a multi-invite picker, and the non-blocking async User-Service refactor with background retry and quiet banner — no blank screens.
+ Invited Agents go from invite email to an explicit accept/reject review step for every pending invitation, then straight into their Account. Delivers invite-link email verification, a unified invite review screen (single row for one invitation, a picker for several), and the non-blocking async User-Service refactor with background retry and quiet banner — no blank screens.
```

**Story 5.2** — add a superseded banner at the top of its acceptance criteria (do not delete; preserves history)
```diff
+ > **Superseded 2026-08-13.** AC-1/AC-2/AC-3 (auto-accept + toast + decline) are replaced — see Story 5.5. Root cause and rationale: `_bmad-output/implementation-artifacts/investigations/invitation-review-screen-not-showing-investigation.md`.
```

**New Story 5.5: Unify invitation review for one or more invitations** (added after 5.4)
- Remove the `invitations.size() == 1` auto-accept branch in `ReviewTenantInvitations.requiredActionChallenge()`; route it into the existing `review-invitations.ftl` challenge (same as `size > 1`).
- Retire `ToastInfoResource`, `InviteDeclineResource`, the three `TOAST_*` constants, their `MultitenancyRootResource` route registrations, and the account-theme toast JS/decline link.
- Rewrite or remove `UserServiceAsyncTest.singleAutoAccept_toastNotesSetUnconditionally` and all 3 `InviteDeclineTest` tests; verify `MultiInvitePickerTest` has no ">1"-only assumptions and extend it to cover the single-invitation case.
- Acceptance: an Agent with exactly one pending invitation sees `review-invitations.ftl` and must explicitly accept or decline; behavior is identical regardless of existing tenant memberships (per Finding 5).

**Story 6.1** — add a note, do not reopen
```diff
+ > **Note (2026-08-13):** AC-3/AC-4 (full-screen `invite-accepted.ftl` confirmation) were never implemented despite this story's "done" status — confirmed by commit and file-history trace. Moot under Story 5.5's unified design; flagged as a documentation-integrity item, not reopened by this proposal.
```

### 4.3 Architecture (`_bmad-output/planning-artifacts/architecture.md`, line 34-36)
```diff
- - **Invitation Acceptance (FR-INV, 8):** emailVerified on invite-link click; auto-accept single
-   invite with toast + decline; multi-invite picker; **non-blocking async User-Service call** with
-   background retry + quiet banner; no blank screens.
+ - **Invitation Acceptance (FR-INV, 8):** emailVerified on invite-link click; unified accept/reject
+   review screen for one or more pending invitations; **non-blocking async User-Service call** with
+   background retry + quiet banner; no blank screens.
```

### 4.4 `sprint-status.yaml`
```diff
  epic-5: in-progress
  5-1-invite-link-email-verification: done
  5-2-auto-accept-single-invitation-with-toast-decline: done
  5-3-multi-invite-picker: done
  5-4-non-blocking-async-user-service-sync-retry-banner: done
+ 5-5-unify-invitation-review-for-single-and-multi: ready-for-dev
  epic-5-retrospective: optional
```
(5.2 stays `done` — it shipped what it said, correctly, per the PRD as it stood then. It is superseded, not undone.)

## Decision Resolved (2026-08-13)

**Success metric target** (`prd.md`, "Time from invite click to product landing"): kept at `≤ 2 steps after login` as an aspirational target, marked `[AT RISK 2026-08-13]` — auto-accept removal adds one explicit tap; the target is not changed to ≤3, and is flagged as needing a lighter-weight mechanism to hit later. Source updated from FR-INV-2/FR-INV-3 (both superseded) to FR-INV-4.

## 5. Implementation Handoff

**Scope classification: Moderate** — touches a shipped PRD requirement and epic/story structure (not just one story's internals), but stays within existing architecture/extension points (no new component, no data-model change). Requires backlog reorganization (new Story 5.5, status annotations on 5.2/6.1) before Developer implementation.

**Handoff:**
1. **Product Owner / Asif** — approve this proposal, decide the success-metric question above, approve PRD/epics/architecture edits as written.
2. **Developer (`bmad-create-story` → `bmad-dev-story`)** — implement Story 5.5 once created: the `ReviewTenantInvitations.java` branch change, retirement of `ToastInfoResource`/`InviteDeclineResource`/toast constants/routes/theme JS, and the three-test rewrite.

**Success criteria:** an Agent with exactly one pending invitation sees the accept/reject review screen and must act explicitly, on both first-tenant and additional-tenant invitations; no dead code left behind (toast/decline paths fully removed, not just unreachable); all three affected test files pass or are intentionally removed with rationale recorded.

## Side Findings

- Story 6.1 ("done", Epic 6) claims Java and theme changes (`invite-accepted.ftl`, `challengeInviteAccepted()`, etc.) that do not exist anywhere in either repository's git history. This is a genuine done/not-done status-integrity gap, independent of this change. Recommend a separate, later look at how a story got marked `done` with a Dev Agent Record and File Change List describing work that was never committed.
