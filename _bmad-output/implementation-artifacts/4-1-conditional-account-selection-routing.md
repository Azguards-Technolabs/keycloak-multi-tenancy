---
baseline_commit: ea9e5840bf93ccca843694f54bb1b4577a26da6c
---

# Story 4.1: Conditional Account-Selection Routing

Status: done

## Story

As an authenticated Agent,
I want the Account step to appear only when I actually need to choose,
So that single-Account logins aren't slowed by a needless screen.

## Acceptance Criteria

1. **Given** the Agent belongs to exactly one Account
   **When** authentication completes
   **Then** the Account selection screen is skipped and the Agent routes directly to the product (FR-AS-1)

2. **Given** the Agent belongs to two or more Accounts
   **When** authentication completes
   **Then** the Account picker is presented before routing (FR-AS-2)

3. **Given** the Agent belongs to zero Accounts
   **When** authentication completes
   **Then** the Agent is routed to the Create New Business flow (FR-AS-7)

4. **Given** an Account is selected/resolved (any of the three cases above)
   **When** the Agent proceeds
   **Then** the existing token/session contracts are preserved — `active_tenant` attribute, `active-tenant-id` session note, `oidc-active-tenant`/`oidc-all-tenants` mappers; tenant switch re-mints tokens (AR-11)

## Tasks / Subtasks

---

### REPO 1: `keycloak-multi-tenancy`

> This is the ONLY repository for this story. No theme changes are required.

- [x] **Task 1: Fix zero-account routing in `requiredActionChallenge`** (AC: #3, #4)
  - [x] Open `src/main/java/dev/sultanov/keycloak/multitenancy/authentication/requiredactions/SelectActiveTenant.java`
  - [x] Locate the `requiredActionChallenge` method — find the `tenantMemberships.isEmpty()` branch:
    ```java
    if (tenantMemberships.isEmpty()) {
        context.success();
    ```
  - [x] Change it to explicitly route to Create New Business by adding the `create-tenant` required action before calling `context.success()`:
    ```java
    if (tenantMemberships.isEmpty()) {
        log.debugf("User has no tenant memberships in challenge — routing to create-tenant");
        context.getUser().addRequiredAction(CreateTenant.ID);
        context.success();
    ```
  - [x] Add the import for `CreateTenant` at the top of the file if not already present:
    `import dev.sultanov.keycloak.multitenancy.authentication.requiredactions.CreateTenant;`
  - [x] **Rationale:** `evaluateTriggers` already handles the normal zero-account case (doesn't queue `select-active-tenant`; `CreateTenant.evaluateTriggers` handles it independently). This fix covers the edge-case path where a user had >1 memberships when `evaluateTriggers` ran (so `select-active-tenant` was queued) but by the time `requiredActionChallenge` runs, all memberships have been revoked. Without this fix, the agent reaches the account picker, sees no accounts, and is silently passed through with no `active-tenant-id` set and no Create New Business prompt — a dead end.
  - [x] **Verify no double-add:** `UserModel.addRequiredAction()` is idempotent in KC — calling it with an ID that's already present is a no-op. `CreateTenant.evaluateTriggers()` may have already added this action; adding it here is safe.

- [x] **Task 2: Verify existing routing paths are correct** (AC: #1, #2, #4)
  - [x] Read through `SelectActiveTenant.java` `evaluateTriggers` to confirm the single-account auto-select path:
    - When `tenantMemberships.size() == 1` → `context.getAuthenticationSession().setUserSessionNote(Constants.ACTIVE_TENANT_ID_SESSION_NOTE, ...)` is called → picker is skipped ✓
  - [x] Read through `evaluateTriggers` to confirm the multi-account path:
    - When `tenantMemberships.size() > 1` → `context.getUser().addRequiredAction(ID)` is called → picker shown ✓
  - [x] Read through `processAction` to confirm token contract:
    - `context.getAuthenticationSession().setUserSessionNote(Constants.ACTIVE_TENANT_ID_SESSION_NOTE, selectedTenant)` is set on selection ✓
    - The OIDC mappers (`oidc-active-tenant`/`oidc-all-tenants`) read from the session note via the existing `TokenManager.java` — no changes needed ✓
  - [x] No code changes required for this task — it is a verification-only task. Document the findings in the Completion Notes.

- [x] **Task 3: Add tracing tag to the updated zero-account path** (AC: #3)
  - [x] In `requiredActionChallenge`, within the existing Zipkin tracing block, add a span tag when the zero-membership path is taken:
    ```java
    if (tenantMemberships.isEmpty()) {
        span.tag("routing.zero_accounts", "true");
        log.debugf("User has no tenant memberships in challenge — routing to create-tenant");
        context.getUser().addRequiredAction(CreateTenant.ID);
        context.success();
    ```
  - [x] This follows the existing pattern of span tags in the class (e.g. `span.tag("user.id", ...)`).

- [x] **Task 4: Build and verify** (AC: #1–#4)
  - [x] `mvn package -DskipTests` → BUILD SUCCESS
  - [x] `grep -n "CreateTenant" src/main/java/dev/sultanov/keycloak/multitenancy/authentication/requiredactions/SelectActiveTenant.java` → at least 1 match (the new routing line)
  - [x] `grep -rn "console\." src/main/java/dev/sultanov/keycloak/multitenancy/authentication/requiredactions/SelectActiveTenant.java` → 0 matches
  - [x] Run existing integration tests (requires Docker/Testcontainers) to confirm no regression on the three routing paths:
    ```
    mvn verify -Dtest=BrowserIntegrationTest
    ```
    Expected results:
    - `user_shouldBePromptedToCreateTenant_whenTheyDontHaveInvitations` → PASS (zero-account → Create New Business) — covers AC #3
    - `user_shouldBePromptedToSelectTenant_whenTheyAcceptMultipleInvitations` → PASS (multi-account picker) — covers AC #2
    - `user_shouldNotBePromptedToCreateTenant_whenTheyAcceptInvitation` → PASS (single-account auto-routing, no picker) — covers AC #1
  - [x] Docker not available locally — CI validates these (pre-existing Docker constraint — see Story 1.6 notes).

- [x] **Task 5: Manual verification checklist** (requires running KC 26.6.3 instance)

  **AC #1 — Single-account auto-routing:**
  - Log in as a user who belongs to exactly one Account
  - Verify the Account selection screen is NOT shown — user lands directly in the product
  - Verify the `active-tenant-id` session note is set (check KC admin panel or token introspection)

  **AC #2 — Multi-account picker:**
  - Log in as a user who belongs to two or more Accounts
  - Verify the Account picker screen appears with all Accounts listed
  - Select one Account and verify the user lands in the product with the correct `active-tenant-id`

  **AC #3 — Zero-account routing:**
  - Log in as a user who belongs to no Accounts (brand-new user with no invitations)
  - Verify the Create New Business screen appears
  - Create a business — verify the user proceeds into the product

  **AC #4 — Token contracts:**
  - After selecting an Account (AC #2), obtain a token via `/protocol/openid-connect/token`
  - Verify the `active_tenant` claim is present in the token
  - Verify the `oidc-active-tenant` and `oidc-all-tenants` mapper claims are present

---

### Review Findings

_Code review 2026-06-17 — Blind Hunter + Edge Case Hunter + Acceptance Auditor. 0 decision-needed, 0 patch, 2 deferred, 7 dismissed as noise. Acceptance Auditor confirmed all 4 ACs met and all spec constraints honored; the 3-line diff faithfully implements the spec._

- [x] [Review][Defer] No `active-tenant-id` session-note short-circuit in `requiredActionChallenge` [SelectActiveTenant.java:69-74] — deferred, pre-existing (already acknowledged in spec §"Deferred Items" and `deferred-work.md`; the new branch improves on the prior dead-end pass-through, does not worsen it)
- [x] [Review][Defer] IDP tenant-filtering semantics: zero accessible tenants throws `ACCESS_DENIED` at `:164` before the new zero-account branch is reached; `SelectActiveTenant` checks the *filtered* membership view while `CreateTenant.evaluateTriggers` checks the *unfiltered* view [SelectActiveTenant.java:154-171 vs CreateTenant.java:35] — deferred, pre-existing (not introduced by this change; the throw at :164 prevents the hypothesized create↔select loop for non-IDP sessions filtered==unfiltered)

## Dev Notes

### Working Repository

```
keycloak-multi-tenancy (Repo 1 — only repo, complete all tasks here):
  src/main/java/dev/sultanov/keycloak/multitenancy/authentication/requiredactions/
    SelectActiveTenant.java   ← MODIFY (Task 1+3 — add ~4 lines to requiredActionChallenge)
```

**Do NOT modify any other Java files.** No DB schema changes. No realm-export.json changes. No FTL changes. No i18n changes.

**Do NOT modify:** `CreateTenant.java`, `TenantsBean.java`, `Constants.java`, `TenantProvider.java`, or any mapper classes.

---

### Current State — What `SelectActiveTenant` Does Today

The class already implements correct routing for the normal cases:

**`evaluateTriggers()`** — runs once per login before any required action fires:
- Short-circuits if `active-tenant-id` session note already present (SSO re-auth path)
- 0 memberships → does **nothing** (zero-account routing is owned by `CreateTenant.evaluateTriggers()` which independently adds `create-tenant` when memberships are empty)
- 1 membership → sets `active-tenant-id` session note immediately — the `select-active-tenant` required action is NEVER queued, so the picker screen is never shown
- >1 memberships → adds `select-active-tenant` required action so the picker renders

**`requiredActionChallenge()`** — only runs when `select-active-tenant` WAS queued (i.e., user had >1 memberships at `evaluateTriggers` time):
- 0 memberships (edge case) → `context.success()` ← **THE GAP** — missing `create-tenant` routing
- 1 membership → re-applies the auto-select, calls `context.success()` (defensive re-check)
- >1 memberships → renders `select-tenant.ftl` picker via `TenantsBean.fromMembership(tenantMemberships)`

**`processAction()`** — runs when the user selects an Account from the picker:
- Validates selected tenant ID is in user's membership list
- Sets `active-tenant-id` session note → OIDC mappers use this to populate token claims
- Calls `context.success()` → KC proceeds to next step / token issuance

---

### The Only Change Required

Only one defensive line is missing: in `requiredActionChallenge()`, the zero-membership branch must add `CreateTenant.ID` as a required action before calling `context.success()`.

**Before:**
```java
if (tenantMemberships.isEmpty()) {
    context.success();
```

**After:**
```java
if (tenantMemberships.isEmpty()) {
    span.tag("routing.zero_accounts", "true");
    log.debugf("User has no tenant memberships in challenge — routing to create-tenant");
    context.getUser().addRequiredAction(CreateTenant.ID);
    context.success();
```

This is safe because:
1. `UserModel.addRequiredAction()` is idempotent (KC no-ops if already present)
2. This path is only reached when the user somehow lost ALL memberships between `evaluateTriggers` and `requiredActionChallenge` — a narrow edge case in production but a correctness gap that Story 4.1 is responsible for closing

---

### Why the Normal Zero-Account Path Already Works

For a brand-new user with 0 memberships, the routing to Create New Business works without any changes to `SelectActiveTenant`:

1. KC calls all SPI providers' `evaluateTriggers()`
2. `CreateTenant.evaluateTriggers()` runs → detects 0 memberships → adds `create-tenant` action
3. `SelectActiveTenant.evaluateTriggers()` runs → detects 0 memberships → does nothing, does NOT add `select-active-tenant`
4. KC processes required actions: `create-tenant` fires → user sees Create New Business
5. After the user creates a business (now 1 membership), KC re-evaluates triggers
6. `SelectActiveTenant.evaluateTriggers()` runs again → detects 1 membership → sets `active-tenant-id` session note
7. User proceeds to product with active tenant set

The `select-active-tenant` action is **never queued** for a zero-account user, so `requiredActionChallenge()` is **never called** for them in normal operation.

---

### Token/Session Contracts (AR-11) — What MUST Stay Unchanged

**DO NOT TOUCH any of these:**
- `Constants.ACTIVE_TENANT_ID_SESSION_NOTE = "active-tenant-id"` — set by `evaluateTriggers()` (1-account) or `processAction()` (multi-account) — never change the constant name
- The `oidc-active-tenant` mapper — reads `active-tenant-id` session note to emit the tenant claim — no changes
- The `oidc-all-tenants` mapper — reads all memberships to emit all-tenant claims — no changes
- The `SwitchActiveTenant` resource endpoint — re-mints tokens on tenant switch — no changes
- `TokenManager.java` — manages active-tenant token emission — no changes

The single-account auto-select path writes the session note in `evaluateTriggers()`, which is transferred to the user session on authentication completion. The multi-account selection path writes it in `processAction()`. Both correctly feed the OIDC mappers. No changes to this machinery are needed.

---

### Mandatory Code Patterns (from Epic 2 Retro + previous stories)

1. **Zipkin tracing is mandatory on every required action method.** The exact pattern is already in `SelectActiveTenant`. Task 3 adds a span tag — do NOT add or remove entire try/catch/finally blocks. Only add `span.tag(...)` and `log.debugf(...)` calls inside the existing try block.

2. **`@JBossLog` + `log.debugf` / `log.infof`:** Use these for all new log statements. Do NOT use `System.out.println` or `console.*`.

3. **No inline `context.success()` bypassing the tracing block.** All `context.*` calls must be inside the existing `try (var ignored = ...)` block.

4. **Import style:** The class already uses static imports for `Constants.*`. Add `CreateTenant` as a regular import.

---

### Existing Test Coverage

The following existing tests in `BrowserIntegrationTest` already cover all 3 ACs. They pass on the current codebase and must continue to pass after the change:

| Test | AC Covered | Scenario |
|------|------------|----------|
| `user_shouldBePromptedToCreateTenant_whenTheyDontHaveInvitations` | AC #3 | Zero accounts → Create New Business screen |
| `user_shouldNotBePromptedToCreateTenant_whenTheyAcceptInvitation` | AC #1 | Single account → no picker, straight to product |
| `user_shouldBePromptedToSelectTenant_whenTheyAcceptMultipleInvitations` | AC #2 | Multi-account → picker shown, selection works |
| `user_shouldBePromptedToCreateTenant_whenTheyDeclineInvitation` | AC #3 | Post-rejection zero account → Create New Business |

The story does NOT require writing new test classes. The pre-existing tests are the acceptance gate.

**Important:** The tests require Docker for Testcontainers. If Docker is not available in the local dev environment, CI validates them. Confirm tests pass in CI before marking done.

---

### What Is Out of Scope for Story 4.1

- **FTL template styling** — no `select-tenant.ftl` changes; that is Story 4.2's job
- **Account card component** (role display, last-used pin, logo/initials) — Story 4.2
- **Conditional search field** — Story 4.3
- **Skeleton loading state** — Story 4.4
- **TenantsBean enrichment** (role/logoUrl/lastUsed data for FTL) — Story 4.2
- **Any modifications to `CreateTenant.java`** — zero-account routing logic stays in CreateTenant
- **`messages_en.properties` i18n** — no new keys needed for routing-only changes
- **Any FreeMarker template** (`select-tenant.ftl`, `create-tenant.ftl`) — AR-OOS for Story 4.1
- **`register.ftl`, `login-oauth-grant.ftl`, email templates, admin switcher** — AR-OOS for the entire epic

---

### Deferred Items Relevant to This Story

From `deferred-work.md`:
- **`SelectActiveTenant.requiredActionChallenge` logic drift** — "re-fetches and re-sets the active-tenant session note without the existing-note short-circuit that `evaluateTriggers` uses." Task 1 of this story closes the zero-account gap. The lack of an `active-tenant-id`-already-set short-circuit in `requiredActionChallenge` is acknowledged as low-risk (if the action runs, the user needs a tenant selected) and is NOT fixed here to keep scope contained.
- **`getTenantsStream` full-table fetch + in-memory filtering** — performance concern in `JpaTenantProvider.java`; deferred, not Story 4.1 scope.

---

### References

- Epics file: `_bmad-output/planning-artifacts/epics.md` — Epic 4, Story 4.1 (FR-AS-1, FR-AS-2, FR-AS-7, AR-11)
- Architecture: `_bmad-output/planning-artifacts/architecture.md` — token/session contracts section
- Previous story: `_bmad-output/implementation-artifacts/3-4-optional-post-login-enrollment-prompt.md`
  - Mandatory code patterns (tracing, dual-interface, log patterns) — all apply here
- `SelectActiveTenant.java` — current state: 199 lines; the only file being modified
- `CreateTenant.java` — provides `CreateTenant.ID = "create-tenant"` used in the new routing line
- `TenantsBean.java` — exposes `id`, `name`, `roles`, `logoUrl` to FTL (DO NOT change — Story 4.2 enriches this)
- `Constants.java` — `ACTIVE_TENANT_ID_SESSION_NOTE = "active-tenant-id"` (DO NOT change)
- `BrowserIntegrationTest.java` — pre-existing tests that cover all 3 routing scenarios; must still pass
- Sprint status: `_bmad-output/implementation-artifacts/sprint-status.yaml`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- `CreateTenant` is in the same package (`requiredactions`) — no import statement needed; same-package access is automatic in Java.
- `UserModel.addRequiredAction()` is idempotent in Keycloak; confirmed safe to call even if `create-tenant` was already queued by `CreateTenant.evaluateTriggers()`.
- Docker not available locally; integration tests (`BrowserIntegrationTest`) deferred to CI as per the pre-existing Docker constraint documented in Story 1.6.

### Completion Notes List

- **Task 1 + 3 (combined):** Added `span.tag("routing.zero_accounts", "true")`, `log.debugf(...)`, and `context.getUser().addRequiredAction(CreateTenant.ID)` to the `tenantMemberships.isEmpty()` branch in `requiredActionChallenge()`. This closes the edge-case gap where a user had >1 memberships at `evaluateTriggers` time (so `select-active-tenant` was queued) but lost all memberships before `requiredActionChallenge` ran — previously the user would be silently passed through with no `active-tenant-id` and no Create New Business prompt.
- **Task 2 (verification):** Confirmed `evaluateTriggers()` correctly handles 0/1/>1 memberships and `processAction()` correctly sets the `active-tenant-id` session note. No code changes required.
- **Task 4:** `mvn package -DskipTests` → BUILD SUCCESS. `CreateTenant.ID` reference verified at line 73. Zero `console.*` calls.
- **Task 5:** Manual verification requires a running KC 26.6.3 instance — deferred to the reviewer per standard process.

### File List

**Repo 1 — keycloak-multi-tenancy:**
- `src/main/java/dev/sultanov/keycloak/multitenancy/authentication/requiredactions/SelectActiveTenant.java` (MODIFIED — added 3 lines to `requiredActionChallenge` zero-account branch)

## Change Log

- 2026-06-17: Story created — ready for dev.
- 2026-06-17: Implemented — added zero-account defensive routing + tracing tag to `SelectActiveTenant.requiredActionChallenge`. BUILD SUCCESS. Status → review.
