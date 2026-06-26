---
baseline_commit: ea9e5840bf93ccca843694f54bb1b4577a26da6c
---

# Story 5.3: Multi-Invite Picker

**Epic:** 5 — Invited Agent Onboarding
**Story ID:** 5.3
**Status:** done
**Working Repositories:** `keycloak-multi-tenancy` (Java SPI — complete first) then `azguards-keycloak-custom-theme` (FTL/CSS — complete second)

---

## User Story

**As** an invited Agent with more than one pending invitation,
**I want** to choose which to accept or decline,
**So that** I control which Accounts I join.

---

## Acceptance Criteria (BDD)

### AC-1 — Multi-invite picker rendered when > 1 invitation (FR-INV-4)
```
Given  the Agent has more than one pending invitation after authentication
When   the ReviewTenantInvitations required action runs
Then   an invite picker is presented with one row per invitation
And    no invitation is auto-accepted
```

### AC-2 — Each row shows Account name, Inviter, and independent actions (FR-INV-4)
```
Given  the invite picker is rendered
When   each invitation row is displayed
Then   it shows the Account name (tenant name)
And    it shows the Inviter's display name (first + last name, or username, or "Someone" fallback)
And    it has an independent Accept button and a Decline button
```

### AC-3 — Independent per-invitation actions; Agent proceeds when done (FR-INV-4)
```
Given  the Agent acts on one invitation (accept or decline)
When   they choose an action for that row
Then   the others remain actionable (not auto-submitted)
And    after the Agent acts on all desired invitations and clicks "Go to Dashboard"
Then   processAction() fires with the accepted/rejected tenant IDs
And    the Agent proceeds to account selection
```

### AC-4 — Picker uses Epic 1 tokens/components; no CDN dependencies
```
Given  the review-invitations.ftl template
When   it renders
Then   it uses tokens.css and reviewTenant.css (already tokenized) for all styling
And    Bootstrap CDN, Font Awesome CDN, and Google Fonts CDN links are absent
And    button icons use text labels (or inline SVG) — no external icon font dependency
```

### AC-5 — No console.* statements in the picker's inline JavaScript (NFR-S-1)
```
Given  the review-invitations.ftl inline <script> block
When   audited
Then   no console.log / console.debug / console.error / console.warn statements remain
```

### AC-6 — Accessibility: focus management and ARIA (NFR-A-1..5)
```
Given  the picker renders
When   it loads
Then   focus moves to the page heading (h1 or page-title equivalent)
And    the invitation list region has role="list" with aria-label
And    each row is role="listitem"
And    the Accept and Decline buttons have accessible labels including the Account name
       (e.g. aria-label="Accept invitation from Acme Corp")
And    the "Go to Dashboard" button has aria-disabled="true" until at least one
       action is taken (when hasMemberships=false)
```

### AC-7 — Tracing span covers processAction (AR-12)
```
Given  the Zipkin tracer is active
When   processAction() runs for the multi-invite path
Then   a SERVER span named "review-invitations.processAction" is recorded
And    user.id is tagged on the span
```
_(Note: AC-7 is already implemented in the current processAction() — this is a verify-only criterion.)_

---

## Tasks / Subtasks

### Repo 1: `keycloak-multi-tenancy` (complete first)

- [x] Task 1: Extend `TenantsBean` to expose `inviterName` per invitation row (AC-2)
  - [x] 1.1 Add `inviterName` field to `TenantsBean.Tenant` inner class (String, final, constructor param)
  - [x] 1.2 Add `getInviterName()` accessor
  - [x] 1.3 Extract a `private static String computeInviterName(UserModel invitedBy)` method on `TenantsBean`:
        Priority: (1) `firstName + " " + lastName` trimmed and non-blank, (2) `username`, (3) `"Someone"`.
        This mirrors the private method in `ReviewTenantInvitations` — do NOT modify `ReviewTenantInvitations`.
  - [x] 1.4 Update `fromInvitations()` to pass `computeInviterName(invitation.getInvitedBy())` to the `Tenant` constructor
  - [x] 1.5 Verify `fromMembership()` static factory does NOT use or change the new field (it uses a different constructor path; `inviterName` is only relevant for invitations)

- [x] Task 2: Add i18n key for "Invited by" (AC-2)
  - [x] 2.1 Add `invitedBy=Invited by` to `src/main/resources/theme-resources/messages/messages_en.properties`
        (Note: this key is consumed by the theme FTL; the fallback template also loads messages_en)

- [x] Task 3: Integration test for multi-invite path (AC-1, AC-3)
  - [x] 3.1 Create `src/test/java/dev/sultanov/keycloak/multitenancy/MultiInvitePickerTest.java`
        extending `BaseIntegrationTest`
  - [x] 3.2 Test: admin creates 2 tenants, sends invitations to user for both → user clicks invite-verify
        link → user logs in → picker is rendered (not auto-accept) → user accepts both → user has 2 memberships
  - [x] 3.3 Test: user accepts one, declines one → only the accepted tenant's membership exists
  - [x] 3.4 Test: user declines all when hasMemberships=false → picker re-renders with error
        "You must accept at least one tenant invitation to proceed if you have no existing memberships."

### Repo 2: `azguards-keycloak-custom-theme` (complete after Java work)

- [x] Task 4: Update `review-invitations.ftl` — inviter name display (AC-2)
  - [x] 4.1 Add inviter name display to each invitation row:
        `<p class="tenant-inviter">${msg("invitedBy")} <strong>${kcSanitize(tenant.inviterName!"Someone")}</strong></p>`
        Place it below the account name line in `.tenant-info`

- [x] Task 5: Remove CDN dependencies from `review-invitations.ftl` (AC-4)
  - [x] 5.1 Remove Bootstrap CDN `<link>` (`cdn.jsdelivr.net/npm/bootstrap@5.3.0`)
  - [x] 5.2 Remove Font Awesome CDN `<link>` (`cdnjs.cloudflare.com/...font-awesome`)
  - [x] 5.3 Remove both Google Fonts `<link>` tags (Material Icons + Inter) from inside the `<form>` section
  - [x] 5.4 Replace Material Icons `<i class="material-icons button-icon">close</i>` and
        `<i class="material-icons button-icon">check</i>` with text-only labels (the `.button-text` span
        already carries the label; simply remove the `<i>` elements)
  - [x] 5.5 Verify the FTL still links `tokens.css`, `components.css`, and `reviewTenant.css`
        (these are already present and must remain)

- [x] Task 6: Remove all `console.*` statements from the inline `<script>` block (AC-5, NFR-S-1)
  - [x] 6.1 Delete every `console.log(...)`, `console.error(...)`, `console.debug(...)`,
        `console.warn(...)` line from the `<script>` block in `review-invitations.ftl`
  - [x] 6.2 Remove `try/catch` blocks whose sole purpose was `console.error` on failure
        (no console.* were in the FTL — JS is external in script.js, already clean)

- [x] Task 7: Accessibility improvements (AC-6, NFR-A-1..5)
  - [x] 7.1 Add `tabindex="-1"` to the page heading (upgraded to `<h1>`) and `wtA11y` auto-focuses it on DOMContentLoaded
  - [x] 7.2 Add `role="list"` and `aria-label="${msg('reviewInvitationsHeader')}"` to
        the `#kc-form-wrapper` div that contains the invitation cards
  - [x] 7.3 Add `role="listitem"` to each `.tenant-invitation-card` div
  - [x] 7.4 Add `aria-label` to Accept and Decline buttons:
        Accept: `aria-label="${msg('doAccept')} — ${kcSanitize(tenant.name!'Unknown')}"`
        Decline: `aria-label="${msg('doReject')} — ${kcSanitize(tenant.name!'Unknown')}"`
  - [x] 7.5 When `hasMemberships=false`, set the Proceed button to `disabled aria-disabled="true"` initially;
        `validateProceedButton()` in script.js updated to also set `aria-disabled` attribute

- [x] Task 8: Fix the missing `default-logo.png` fallback (deferred from 4-2, now in scope)
  - [x] 8.1 Replace broken `onerror` fallback with initials-based CSS fallback:
        - If `tenant.logoUrl` is set: render `<img>` without `onerror`
        - If `tenant.logoUrl` is empty: render `<span class="wt-tenant-initials">` with initials computed from tenant name
  - [x] 8.2 Added `.wt-tenant-initials` CSS rule to `reviewTenant.css` (sized 48×48px to match `.tenant-logo`)

### Review Findings (code review 2026-06-23)

- [x] [Review][Decision→Defer] Diff is contaminated across multiple stories and the 5.3 test is untracked — _Deferred (2026-06-23): unrelated stories bundled in working tree; commit-boundary cleanup tracked separately, test to be staged before final commit._ — The reviewed working tree bundles 5.2 (single-invitation auto-accept in `ReviewTenantInvitations`), 4.2 (last-used pin + sort in `TenantsBean.fromMembership` / `SelectActiveTenant`), zero-account routing, the passkey-enrollment SPI line, and the invite-verify email change — far beyond 5.3's stated Java scope (`TenantsBean.java` + `messages_en.properties`). The story's headline test deliverable `src/test/java/.../MultiInvitePickerTest.java` exists on disk but is **untracked** (`git status ??`), so it is not in the change being reviewed. Decide how to handle commit boundaries and whether to stage the test before marking 5.3 done.
- [x] [Review][Decision→Patch] Out-of-scope invitation: removed revoke-before-throw [ReviewTenantInvitations.java:137-148] — FIXED: removed the `revokeInvitation` call (and its try/catch) and the pre-throw `setClientNote`; the out-of-scope path now throws `ACCESS_DENIED` with no DB mutation. Eliminates the transaction-rollback ambiguity, preserves the legitimate invitation for an in-scope IDP, and makes denial deterministic.
- [x] [Review][Patch] `TenantsBean.fromMembership` dereferences `getTenant()` without the null guard `fromInvitations` now has [TenantsBean.java:57] — FIXED: added `.filter(membership -> membership.getTenant() != null)` — `fromInvitations` added `.filter(i -> i.getTenant() != null)`; `fromMembership` (new 2-arg signature) does `membership.getTenant().getId()` unguarded, so a concurrently-deleted tenant NPEs the tenant-selection screen. Add the same null filter.
- [x] [Review][Defer] `processAction` grants membership to an already-member with no guard → duplicate rows [ReviewTenantInvitations.java:386] — deferred, pre-existing (processAction not modified by this diff)
- [x] [Review][Defer] `processAction` sends accepted/rejected IDs to UserService before validating against the user's invitations; forged/unknown/blank (empty-string split tokens)/duplicate-in-both IDs reach the external call [ReviewTenantInvitations.java:350] — deferred, pre-existing
- [x] [Review][Defer] `processAction` has no idempotency guard → double-submit/back-button grants twice [ReviewTenantInvitations.java:367-417] — deferred, pre-existing
- [x] [Review][Defer] `processAction` dereferences `inv.getTenant().getId()` without null guard → NPE dead-ends login on concurrently-deleted tenant [ReviewTenantInvitations.java:325,369,373] — deferred, pre-existing
- [x] [Review][Defer] `requiredActionChallenge` silently stalls (no success/challenge/failure) when invitee email is empty or unverified [ReviewTenantInvitations.java:109-111] — deferred, pre-existing
- [x] [Review][Defer] No pagination on an unbounded invitation list (picker renders all; processAction is O(n²)) [ReviewTenantInvitations.java:90,367] — deferred, pre-existing
- [x] [Review][Defer] `EmailSender` invite-verify URL uses the raw invitation UUID as a permanent, unauthenticated, non-expiring `token` [EmailSender.java:20-26] — deferred, already acknowledged in deferred-work; mitigated by UUID unguessability; belongs to the invite-verify flow, not 5.3

---

## Architecture & Technical Requirements

### Dual-Repo Execution Order
**Always complete the keycloak-multi-tenancy Java work and deploy/test it before touching the theme repo.**
The FTL reads `tenant.inviterName` from the `TenantsBean.Tenant` — the Java must be deployed first.

### Critical: `processAction()` Is Already Complete — Do Not Modify
`ReviewTenantInvitations.processAction()` already handles the multi-invite flow end-to-end:
- Reads `acceptedTenants` and `rejectedTenants` form params (comma-separated tenant IDs)
- Calls `userServiceRestClient.updateUserTenantInvitationStatuses()`
- Grants membership for accepted tenants, sends acceptance emails
- Sends rejection emails for rejected tenants
- Revokes all processed invitations
- Routes to `SelectActiveTenant` when user has memberships
- Includes full Zipkin tracing under `"review-invitations.processAction"` span
- **Do NOT touch `processAction()` in this story.**

### `requiredActionChallenge()` Multi-Invite Branch — Already Complete
The `size > 1` branch in `requiredActionChallenge()` already renders `review-invitations.ftl`
with `TenantsBean.fromInvitations(invitations)` bound as `data`. No changes needed here either.
**Only `TenantsBean.java` itself needs modification in Repo 1.**

### TenantsBean — Key Constraint (Dev Note 9 from Story 5-2)
Story 5-2 Dev Note 9 explicitly deferred inviter-name exposure: "The current `TenantsBean.Tenant`
inner class has no inviter name field. Do NOT modify TenantsBean for this story." This deferral
was for 5-2 only — **adding `inviterName` to `TenantsBean` is the primary Java deliverable for 5-3.**

### `computeInviterName()` — Do Not Refactor `ReviewTenantInvitations`
`ReviewTenantInvitations` already has a private `computeInviterName(UserModel)`. The cleanest
approach for story 5-3 is to add an identically-named private static method directly on
`TenantsBean` — this avoids making `ReviewTenantInvitations`'s private method visible/shared,
and keeps the bean self-contained. A small amount of duplication is acceptable here over
introducing a coupling between these two classes.

### `TenantsBean.fromInvitations()` — Exact Change Required
Current signature and logic (lines 23–33 of `TenantsBean.java`):
```java
public static TenantsBean fromInvitations(List<TenantInvitationModel> invitations) {
    List<Tenant> tenants = invitations.stream()
            .map(invitation -> new Tenant(
                    invitation.getTenant().getId(),
                    invitation.getTenant().getName(),
                    invitation.getRoles(),
                    invitation.getLogoUrl() != null ? invitation.getLogoUrl() : "",
                    false))
            .collect(Collectors.toList());
    return new TenantsBean(tenants);
}
```
After change — add inviterName as the 6th constructor arg:
```java
public static TenantsBean fromInvitations(List<TenantInvitationModel> invitations) {
    List<Tenant> tenants = invitations.stream()
            .map(invitation -> new Tenant(
                    invitation.getTenant().getId(),
                    invitation.getTenant().getName(),
                    invitation.getRoles(),
                    invitation.getLogoUrl() != null ? invitation.getLogoUrl() : "",
                    false,
                    computeInviterName(invitation.getInvitedBy())))
            .collect(Collectors.toList());
    return new TenantsBean(tenants);
}
```

### `TenantsBean.fromMembership()` — Must NOT Break
`fromMembership()` creates `Tenant` objects for the account-selection picker — not invitations.
After adding the `inviterName` constructor param, `fromMembership()` must pass `""` (empty string)
as the 6th arg, since there is no inviter for a membership row. Or use `""` default.
```java
// In fromMembership():
return new Tenant(tenantId, membership.getTenant().getName(), membership.getRoles(), logoUrl, lastUsed, "");
```
The FTL for `select-tenant.ftl` does not use `tenant.inviterName` and should not be affected.

### FTL Form Submission Protocol — Do Not Change
The FTL submits to `${url.loginAction}` via `#proceed-invitations-form` with:
- `acceptedTenants` hidden input (comma-joined accepted tenant IDs)
- `rejectedTenants` hidden input (comma-joined rejected tenant IDs)
- `hasMemberships` hidden input
This matches exactly what `processAction()` reads. **Do not change field names.**

### Console.log Removal — Be Careful with sessionStorage Recovery Logic
The inline script has `try { sessionStorage.setItem(...) } catch(e) { console.error(...) }`.
When removing the `console.error`, keep the `catch` body empty (or remove it if the only
content was the console call) so the try/catch structure is preserved for the storage failure.
Example — change:
```js
try {
  sessionStorage.setItem('tenantStates', JSON.stringify(tenantStates));
  console.log("Saved to sessionStorage:", tenantStates);
} catch (e) {
  console.error("Error saving to sessionStorage:", e);
}
```
To:
```js
try {
  sessionStorage.setItem('tenantStates', JSON.stringify(tenantStates));
} catch (e) {
  sessionStorage.removeItem('tenantStates');
}
```

### Accessibility — Button Labels With Account Name
The Accept/Decline buttons currently have no accessible name beyond their text content.
Adding `aria-label` makes them unambiguous when screen readers enumerate the buttons on the page:
```ftl
<button type="button"
  class="... accept-button"
  aria-label="${msg('doAccept')} — ${kcSanitize(tenant.name!'Unknown')}"
  onclick="handleTenantAction('${tenant.id?js_string}', 'accept')">
  <span class="button-text">${msg("doAccept")}</span>
</button>
```
Note: `msg("doAccept")` returns "Accept" and `msg("doReject")` returns "Reject" (already in messages_en.properties).

### `updateProceedButtonState()` — hasMemberships Guard
The current JS `updateProceedButtonState()` always enables the Proceed button. Story 5-3 AC-6
says it should start `aria-disabled="true"` when `hasMemberships=false` and enable only when
at least one acceptance is recorded. Update the FTL to pass `hasMemberships` into the script:
```js
var hasMemberships = ${hasMemberships?c};  // FreeMarker boolean → "true"/"false"

function updateProceedButtonState() {
  var proceedBtn = document.querySelector('.proceed-button');
  if (!proceedBtn) return;
  var hasAccepted = Object.values(tenantStates).some(function(v) { return v === 'accept'; });
  var canProceed = hasMemberships || hasAccepted;
  proceedBtn.disabled = !canProceed;
  proceedBtn.setAttribute('aria-disabled', String(!canProceed));
}
```
The initial Proceed button in the FTL should be `disabled` (not just aria-disabled) when
`hasMemberships=false`:
```ftl
<button type="submit" class="... proceed-button"
  <#if !hasMemberships>disabled aria-disabled="true"</#if>>
  ${msg("goDashboard")!"GO TO DASHBOARD"}
</button>
```

### Logo Initials Fallback — Reuse `select-tenant.ftl` Pattern
`select-tenant.ftl` already renders a `.wt-tenant-initials` span for tenants without a logoUrl.
Copy the same pattern. Verify the CSS class exists in `selectTenant.css` — if so, import
`selectTenant.css` in the FTL, OR copy the rule into `reviewTenant.css` (preferred; avoids
importing a file scoped to the account-selection screen).

### Tracing — No New Spans Needed
`processAction()` already wraps its body in a `"review-invitations.processAction"` span with
`user.id` tagged. The multi-invite path is entirely within `processAction()` — no additional
spans needed for story 5-3.

---

## File Change Map

### `keycloak-multi-tenancy` repo

| File | Action | Notes |
|------|--------|-------|
| `src/main/java/dev/sultanov/keycloak/multitenancy/authentication/TenantsBean.java` | UPDATE | Add `inviterName` field + accessor + `computeInviterName()` static method; update `fromInvitations()` and `fromMembership()` |
| `src/main/resources/theme-resources/messages/messages_en.properties` | UPDATE | Add `invitedBy=Invited by` key |
| `src/test/java/dev/sultanov/keycloak/multitenancy/MultiInvitePickerTest.java` | NEW | 3 integration tests (accept-both, accept-one-decline-one, decline-all error) |

### `azguards-keycloak-custom-theme` repo

| File | Action | Notes |
|------|--------|-------|
| `src/main/resources/theme/azguards-whatsapp/login/review-invitations.ftl` | UPDATE | Add inviterName display; remove Bootstrap/FontAwesome/GoogleFonts CDN links; remove console.* from inline script; add ARIA attributes; fix logo fallback; update hasMemberships/proceed-button logic |
| `src/main/resources/theme/azguards-whatsapp/login/resources/css/reviewTenant.css` | UPDATE (conditional) | Add `.wt-tenant-initials` rule if not already present (copy from selectTenant.css); add `.tenant-inviter` style rule |

---

## Previous Story Intelligence (from Story 5-2)

### Tracing Pattern — Already In Place for processAction
```java
Span span = TracingHelper.startServerSpan("review-invitations.processAction");
Throwable traceError = null;
try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
    span.tag("user.id", user.getId());
    // handler body
} catch (Exception ex) {
    traceError = ex;
    throw ex;
} finally {
    TracingHelper.finishSpan(span, traceError);
}
```
This is already implemented in `processAction()`. No change needed.

### Key 5-2 Implementation Notes Relevant to This Story
- **`processAction()` deliberately left untouched in 5-2** — confirmed by Dev Agent Record:
  "confirmed — only `requiredActionChallenge()` was modified. `processAction()` is exactly as-is for Story 5-3."
- **`processAction()` re-renders `review-invitations.ftl` on error** — it calls
  `TenantsBean.fromInvitations(invitations)` again inside the UserService failure and no-membership-error
  paths. After adding `inviterName`, those re-render calls will automatically include inviter names. ✓
- **`formData.getFirst(ACCEPTED_TENANTS_ATTR)`** — reads comma-separated tenant IDs. The FTL's
  hidden input `id="acceptedTenants"` must remain unchanged. The JS `form.addEventListener('submit', ...)`
  populates it correctly. Do NOT change this.
- **Working tree bundling warning** — from story 5-2 code review, the working tree has uncommitted
  changes from multiple stories. Before editing `TenantsBean.java`, run
  `git diff src/main/java/.../authentication/TenantsBean.java` to verify the current state.

### 5-2 Code Review Deferred: `review-invitations.ftl` missing `default-logo.png`
From code-review deferred items (4-2): "review-invitations.ftl references a missing `default-logo.png`
— the asset does not exist under `resources/img/`; the `onerror` fallback is dead. Pre-existing
and out of Story 4-2 scope. Fix on the invitations screen separately." **Story 5-3 is the right
time to fix this** (Task 8 above).

---

## Dev Notes & Guardrails

1. **Do NOT modify `processAction()`** — it is already complete, well-tested, and correct for
   multi-invite. Any modification risks breaking the accept/decline loop. Story 5-3's Java scope
   is limited to `TenantsBean.java` and `messages_en.properties`.

2. **`Tenant` constructor parameter order** — the current constructor is
   `(String id, String name, Set<String> roles, String logoUrl, boolean lastUsed)`.
   Adding `inviterName` as the 6th param is the safest approach (no risk of breaking `fromMembership()`
   by reordering existing params). Pass `""` from `fromMembership()`.

3. **`fromInvitations()` null-safety** — `invitation.getTenant()` CAN return null in edge cases
   (concurrently-deleted tenant). The existing `fromInvitations()` does not guard for this; the
   caller (`requiredActionChallenge()`) loads invitations from the stream and the `size > 1` branch
   fires before any tenant deletion check. However, since `processAction()` already guards for
   absent invitations gracefully, and `TenantsBean.fromInvitations()` is only called after we know
   `invitations.size() > 1`, the risk is low. Add a null guard on `invitation.getTenant()` in
   the lambda as a defense: skip/exclude rows where `getTenant() == null`.

4. **FTL `tenant.inviterName` access** — FreeMarker calls `getInviterName()` on the `Tenant` bean.
   Use `${tenant.inviterName!"Someone"}` (FreeMarker default operator) as the display-side
   fallback, even though `computeInviterName()` already guarantees a non-null "Someone" value.

5. **CDN removal does NOT affect the Bootstrap grid used in `review-invitations.ftl`** — the FTL
   currently uses Bootstrap classes (`d-flex`, `justify-content-center`, `align-items-center`,
   `min-vh-100`, `container`, `row`, `col-md-5`). After removing Bootstrap CDN, these classes
   will have no effect. Either (a) replace with the `reviewTenant.css` / `tokens.css` layout that
   already handles full-screen centering, or (b) add minimal equivalents in `reviewTenant.css`.
   The existing `.background-overlay` + `.content-container` CSS in `reviewTenant.css` already
   provides the full-screen centered layout — use those classes instead of Bootstrap.

6. **No hardcoded hex in `review-invitations.ftl`** — the current FTL does NOT have inline hardcoded
   colors; all styling is in `reviewTenant.css` which is already fully tokenized. The theme-resources
   fallback FTL (`src/main/resources/theme-resources/templates/review-invitations.ftl`) has hardcoded
   hex but it is NOT in scope (we are editing the theme FTL, not the fallback).

7. **`hasMemberships` hidden input** — the current FTL has `<input type="hidden" name="hasMemberships"
   id="hasMemberships" value="${hasMemberships?c}" />`. Keep this — `processAction()` does not read
   it (it re-checks the DB), but removing it would be a silent diff noise. Leave it in place.

8. **Messages key `goDashboard` is not yet in `messages_en.properties`** — the Proceed button
   currently hard-codes "GO TO DASHBOARD" as the label. If you move it to a message key, add
   `goDashboard=Go to Dashboard` to `messages_en.properties`. Alternatively, leave it as-is
   (not a blocking issue for this story; the existing hard-coded text is acceptable).

9. **Integration test setup** — `MultiInvitePickerTest` follows the same pattern as `InviteDeclineTest`
   (extends `BaseIntegrationTest`, uses `KeycloakAdminCli` for admin operations, isolated
   `ClientBuilder.newClient()` per HTTP request). For two-tenant setup, use the
   `JpaTenantProvider.createTenant()` pattern but be aware of the
   mobileNumber+countryCode uniqueness guard (the 2026-06-22 fix already bypasses it when both
   are empty). Creating two tenants with distinct names and no mobile/country will work.

10. **Playwright test extension** — `ReviewInvitationsPage.java` in the test harness already has
    `acceptInvitation()` / `rejectInvitation()` methods. The deferred note from 1-6 mentions
    "multi-accept has no inter-click wait" — add an explicit wait between card state changes when
    writing the new test to avoid the flake risk.

---

## Testing Requirements

### Integration Tests (keycloak-multi-tenancy)

**`MultiInvitePickerTest.java`** — 3 tests:

1. **Accept all invitations**:
   - Admin creates 2 tenants (Tenant A, Tenant B), sends invitations to user from both
   - User clicks invite-verify link → `emailVerified=true`
   - User logs in → `requiredActionChallenge()` sees 2 invitations → renders picker (NOT auto-accept)
   - Test simulates form POST with `acceptedTenants=tenantA_id,tenantB_id`, `rejectedTenants=`
   - Assert user has memberships in both Tenant A and Tenant B
   - Assert no invitations remain

2. **Accept one, decline one**:
   - Same setup: 2 invitations
   - Test POSTs with `acceptedTenants=tenantA_id`, `rejectedTenants=tenantB_id`
   - Assert user has membership in Tenant A only
   - Assert no invitations remain (declined invitations are also revoked)

3. **Decline all with no existing memberships → error re-render**:
   - Same setup: 2 invitations, no existing memberships (`hasMemberships=false`)
   - Test POSTs with `acceptedTenants=`, `rejectedTenants=tenantA_id,tenantB_id`
   - `hasUnprocessedInvitations=true` (both are in rejectedTenants but... wait, check the condition)
   - Actually: `acceptedTenants.isEmpty() && !hasMemberships && hasUnprocessedInvitations` →
     `hasUnprocessedInvitations` = `invitations.stream().anyMatch(inv -> !rejectedTenants.contains(...)))`
     Since both are in rejectedTenants, `hasUnprocessedInvitations=false` → proceeds (no error)!
     So the error case is: POST with `acceptedTenants=` AND `rejectedTenants=` (no action taken)
   - Correct test: POST with both `acceptedTenants=` and `rejectedTenants=` while `hasMemberships=false`
     → Assert 422/challenge re-render with error message

### Manual Verification Checklist

**Keycloak-side (repo 1):**
- [ ] Admin creates 3 tenants, invites user to all 3 → user logs in → picker shown with 3 rows, NOT auto-accept
- [ ] Each row shows: logo/initials, Account name, Inviter display name (or "Someone"), Accept + Decline buttons
- [ ] Accept 2, decline 1 → user has 2 memberships → proceeds to account selection
- [ ] `TenantsBean.fromInvitations()` compiles cleanly (`mvn compile`) — verify no constructor mismatch
- [ ] `fromMembership()` still works for account selection (existing `select-tenant.ftl` unaffected)

**Theme-side (repo 2):**
- [ ] No Bootstrap/FontAwesome/GoogleFonts CDN link tags in HTML source
- [ ] No `console.log` / `console.error` / `console.debug` in the page's `<script>` block
- [ ] Each row shows inviter name below the Account name
- [ ] Accept/Decline buttons have aria-label including the Account name
- [ ] Picker layout correct at 320px mobile viewport
- [ ] Dark mode: all row colors adapt via tokens (no hardcoded hex)
- [ ] When hasMemberships=false: Proceed button starts disabled; enables after first Accept click
- [ ] Tenants without a logoUrl show initials placeholder (no broken-image icon)

---

## Open Questions (Not Blockers)

1. **`goDashboard` message key** — Should "GO TO DASHBOARD" be moved to an i18n key? Not blocking;
   the current text is functional. Add it opportunistically when editing the FTL.

2. **Bootstrap layout classes in `review-invitations.ftl`** — After removing Bootstrap CDN,
   the `.container`, `.row`, `.col-md-5` classes become inert. The `reviewTenant.css` already
   provides a centered full-screen layout via `.background-overlay` + `.content-container`.
   Verify that the card renders correctly without Bootstrap, and simplify the outer HTML structure
   to use the existing non-Bootstrap layout classes.

3. **`invitedBy` message key wording** — "Invited by" vs "From" vs no label (just show the name)?
   The story specifies "each row shows Account name and Inviter" — a brief "Invited by:" label
   before the name is clearest. Adjust wording per product preference.

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

- ✅ Task 1: `TenantsBean` extended with `inviterName` field (6th constructor param), `getInviterName()` accessor, and private static `computeInviterName(UserModel)` method. `fromInvitations()` updated with null guard for tenant and passes inviter name. `fromMembership()` passes `""` as 6th arg. Mirrors logic in `ReviewTenantInvitations.computeInviterName()` without coupling the classes.
- ✅ Task 2: Added `invitedBy=Invited by` and `goDashboard=Go to Dashboard` to `messages_en.properties`. The `goDashboard` key replaces the hard-coded "GO TO DASHBOARD" button label.
- ✅ Task 3: Created `MultiInvitePickerTest.java` with 3 tests: accept-both (AC-1,AC-3), accept-one-decline-one (AC-3), and empty-submit error re-render (AC-3,AC-6). `ReviewInvitationsPage.proceed()` updated to match new "Go to Dashboard" button text.
- ✅ Task 4: Added `<p class="tenant-inviter">` with inviterName below account name in each invitation card. `.tenant-inviter` CSS rule added to `reviewTenant.css`.
- ✅ Task 5: Removed Bootstrap CDN, Font Awesome CDN, and Google Fonts CDN links. Removed `<i class="material-icons">` elements; buttons now use text-only labels via `.button-text` span. Bootstrap layout classes replaced with existing `reviewTenant.css` layout (`background-overlay`, `content-container`, `tenant-invitations-container`). `script.js` `updateTenantCardUI` updated to remove icon-element references.
- ✅ Task 6: No console.* statements in FTL or `script.js` (JS was already in external file; already clean).
- ✅ Task 7: `<h5>` upgraded to `<h1>` with `${msg("reviewInvitationsHeader")}` for correct heading semantics; `tabindex="-1"` added so `wtA11y.focusTarget()` auto-focuses it. `role="list"` + `aria-label` on `#kc-form-wrapper`. `role="listitem"` on each card. `aria-label` on Accept/Reject buttons. Proceed button starts `disabled aria-disabled="true"` when `hasMemberships=false`. `validateProceedButton()` updated to also set `aria-disabled`.
- ✅ Task 8: Logo fallback uses FreeMarker initials pattern (copied from `select-tenant.ftl`) with `.wt-tenant-initials` span. CSS rule added to `reviewTenant.css` at 48×48px (matching `.tenant-logo`).

### File List

**keycloak-multi-tenancy repo:**
- `src/main/java/dev/sultanov/keycloak/multitenancy/authentication/TenantsBean.java`
- `src/main/resources/theme-resources/messages/messages_en.properties`
- `src/test/java/dev/sultanov/keycloak/multitenancy/MultiInvitePickerTest.java` (NEW)
- `src/test/java/dev/sultanov/keycloak/multitenancy/support/browser/ReviewInvitationsPage.java`

**azguards-keycloak-custom-theme repo:**
- `src/main/resources/theme/azguards-whatsapp/login/review-invitations.ftl`
- `src/main/resources/theme/azguards-whatsapp/login/resources/css/reviewTenant.css`
- `src/main/resources/theme/azguards-whatsapp/login/resources/js/script.js`

---

## Change Log

- 2026-06-23: Story 5.3 created — multi-invite picker. Java scope: `TenantsBean` inviterName extension only (`processAction()` already complete). Theme scope: inviter name display, CDN removal, console.log cleanup, ARIA, logo initials fallback.
- 2026-06-23: Story 5.3 implemented. All 8 tasks complete. `TenantsBean` extended with `inviterName` field and `computeInviterName()`. `messages_en.properties` updated with `invitedBy` and `goDashboard` keys. `MultiInvitePickerTest.java` created (3 integration tests). `review-invitations.ftl` fully reworked: CDN-free, ARIA-compliant, initials-based logo fallback, inviter display, `goDashboard` button. `reviewTenant.css` updated with `.wt-tenant-initials` and `.tenant-inviter` rules. `script.js` updated to remove icon-element dependencies and add `aria-disabled` to proceed button. Status → review.
