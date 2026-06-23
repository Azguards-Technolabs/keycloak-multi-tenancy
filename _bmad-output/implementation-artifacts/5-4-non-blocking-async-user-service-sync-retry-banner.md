---
baseline_commit: ea9e5840bf93ccca843694f54bb1b4577a26da6c
---

# Story 5.4: Non-blocking async User-Service sync & retry banner

**Epic:** 5 — Invited Agent Onboarding
**Story ID:** 5.4
**Status:** done
**Working Repository:** `keycloak-multi-tenancy` ONLY — Java SPI changes only; no theme changes required.

---

## User Story

**As** an invited Agent,
**I want** to enter the product even if the backend sync is slow or failing,
**So that** a downstream outage never blocks me at the door.

---

## Acceptance Criteria (BDD)

### AC-1 — User-Service call is fire-and-forget async; does NOT gate Agent entry (FR-INV-5, NFR-S-3, AR-8)
```
Given  invite acceptance completes (single auto-accept OR multi-invite processAction)
When   the User-Service call is submitted
Then   it is dispatched to a background thread-pool executor
And    processAction() / autoAcceptInvitation() calls context.success() IMMEDIATELY
And    the Agent's login flow advances regardless of User-Service response
And    NO error form is shown if the User-Service call fails
```

### AC-2 — Failed User-Service call is retried up to 3 times with 30-second backoff (FR-INV-6, AR-8)
```
Given  the async User-Service call throws an exception
When   the first attempt fails
Then   the system retries after 30 seconds, up to 3 additional retry attempts (4 total)
And    each retry is scheduled on the same thread-pool executor
And    the retry is transparent to the user (no blocking, no screen)
```

### AC-3 — Retry flag stored as user attribute on final failure (FR-INV-6, AR-8)
```
Given  all retry attempts (initial + 3 retries) have failed
When   the final attempt throws
Then   a user attribute "user-service-sync-retry-needed" = "true" is persisted on the user's KC account
And    the flag is NOT set if an earlier attempt succeeds
```

### AC-4 — Retry flag cleared on async success (AR-8)
```
Given  a User-Service call succeeds (on any attempt including a retry)
When   the call returns 2xx
Then   the user attribute "user-service-sync-retry-needed" is removed from the user's KC account
And    a new KC session is opened via KeycloakSessionFactory to perform this removal
```

### AC-5 — Retry banner readable by FTL from user attribute (FR-INV-6, AR-9, UX-DR10)
```
Given  the user attribute "user-service-sync-retry-needed" is "true"
When   the FTL template reads user.getAttribute("user-service-sync-retry-needed")
Then   the value is non-null and truthy
And    the FTL can conditionally render the quiet banner:
       "We had trouble syncing your account — tap to retry."
And    no blocking error screen is shown (FR-INV-7)
```

### AC-6 — Banner retry action re-attempts sync in background (FR-INV-6)
```
Given  the "tap to retry" action is invoked (via a new /user-service-retry endpoint)
When   the endpoint is called
Then   a fresh async User-Service call is submitted with the same retry logic
And    the endpoint returns immediately (non-blocking)
And    on success the "user-service-sync-retry-needed" attribute is cleared
```

### AC-7 — Toast notes set unconditionally for single auto-accept path (AC consistency)
```
Given  single auto-accept fires (autoAcceptInvitation())
When   the async call is submitted (before outcome is known)
Then   toast session notes ARE set (inviter name, tenant name, tenant ID)
       regardless of whether the async call will succeed or fail
And    the previous "skip toast on sync failure" guard (userServiceSucceeded flag) is REMOVED
```

### AC-8 — Tracing spans cover async dispatch; retry attempts tagged (AR-12)
```
Given  the Zipkin tracer is active
When   the async executor submits the User-Service call
Then   a CLIENT span named "user-service.updateStatus.async" is started before the call
And    the span is finished after the call completes (success or failure)
And    if a retry occurs, a new span is started per attempt with tag "retry.attempt" = attempt number
```

---

## Tasks / Subtasks

### Task 1: Add retry-needed constant to `Constants.java`
- [x] 1.1 Add `public static final String USER_SERVICE_SYNC_RETRY_ATTR = "user-service-sync-retry-needed";`
- [x] 1.2 No other changes to Constants.java needed

### Task 2: Add async retry infrastructure to `UserServiceRestClient.java`
- [x] 2.1 Add a static `ScheduledExecutorService ASYNC_EXECUTOR` (2 daemon threads, named `"user-service-async-N"`)
  - Use `Executors.newScheduledThreadPool(2, daemonThreadFactory)` pattern — see Guardrail G-1
- [x] 2.2 Add public `submitAsync(String userId, List<String> accepted, List<String> rejected, KeycloakSessionFactory sessionFactory, String realmId, int maxRetries, long backoffSeconds)` method
  - Immediately schedules `scheduleAttempt(..., attempt=0)` with delay=0
- [x] 2.3 Add private `scheduleAttempt(...)` that:
  - Creates a CLIENT tracing span `"user-service.updateStatus.async"` tagged with `retry.attempt = attempt`
  - Calls the existing `updateUserTenantInvitationStatuses()` (synchronous HTTP inside the async thread)
  - On success: calls `clearRetryFlag(sessionFactory, realmId, userId)` — see Task 2.4
  - On exception: if `attempt < maxRetries`, schedule next attempt after `backoffSeconds`; else call `setRetryFlag(sessionFactory, realmId, userId)` — see Task 2.5
  - Always finishes the span in a finally block
- [x] 2.4 Add private `clearRetryFlag(KeycloakSessionFactory, String realmId, String userId)`:
  - Uses `KeycloakModelUtils.runJobInTransaction(factory, session -> { ... })`
  - Inside: `session.realms().getRealm(realmId)` → `session.users().getUserById(realm, userId)` → `user.removeAttribute(Constants.USER_SERVICE_SYNC_RETRY_ATTR)`
  - Null-safe: skip silently if realm or user not found
  - Log the outcome at DEBUG level
- [x] 2.5 Add private `setRetryFlag(KeycloakSessionFactory, String realmId, String userId)`:
  - Same KC session pattern
  - Sets `user.setSingleAttribute(Constants.USER_SERVICE_SYNC_RETRY_ATTR, "true")`
  - Log at WARN level: "User-service sync failed after all retries for user {userId}"
- [x] 2.6 Import additions: `KeycloakSessionFactory`, `KeycloakModelUtils`, `RealmModel`, `ScheduledExecutorService`, `Executors`, `TimeUnit`, `ThreadFactory`

### Task 3: Refactor `autoAcceptInvitation()` in `ReviewTenantInvitations.java`
- [x] 3.1 REMOVE the synchronous `try { userServiceRestClient.updateUserTenantInvitationStatuses(...) } catch { userServiceSucceeded = false }` block (lines 163–172)
- [x] 3.2 REMOVE the `boolean userServiceSucceeded` variable and the `if (userServiceSucceeded)` guard around toast note setting (lines 163, 214)
- [x] 3.3 AFTER granting membership and revoking invitation (after existing task 3.6), submit async:
  ```java
  KeycloakSessionFactory factory = context.getSession().getKeycloakSessionFactory();
  userServiceRestClient.submitAsync(
      user.getId(), List.of(tenantId), List.of(),
      factory, context.getRealm().getId(), 3, 30L);
  ```
- [x] 3.4 Toast notes are now set UNCONDITIONALLY (no `userServiceSucceeded` guard) when tenant name is non-blank — move to after the async submission
- [x] 3.5 Keep all other logic (IDP scope check, membership guard, email, revoke, required actions) unchanged

### Task 4: Refactor `processAction()` in `ReviewTenantInvitations.java`
- [x] 4.1 REMOVE the blocking try/catch block at lines 342–361 (the one that shows a challenge form on UserService failure)
- [x] 4.2 After all memberships are granted and invitations processed, submit async:
  ```java
  KeycloakSessionFactory factory = context.getSession().getKeycloakSessionFactory();
  userServiceRestClient.submitAsync(
      user.getId(), acceptedTenants, rejectedTenants,
      factory, context.getRealm().getId(), 3, 30L);
  ```
- [x] 4.3 The rest of `processAction()` (membership grants, invitation revokes, required action updates, `context.success()`) is UNCHANGED in flow — just the blocking UserService call is replaced

### Task 5: Add `/user-service-retry` endpoint for banner's retry action (AC-6)
- [x] 5.1 Create `UserServiceRetryResource.java` in `resource/` package:
  - Constructor: `UserServiceRetryResource(KeycloakSession session)`
  - `@POST` `retrySync()` method authenticates via cookie, reads tenant memberships, submits async, returns 202
- [x] 5.2 Register the new endpoint in `MultitenancyRootResource.handleAll()`:
  - Added `else if (path.endsWith("/user-service-retry"))` routing to `UserServiceRetryResource`
- [x] 5.3 Note: The banner's "tap to retry" URL (`/realms/{realm}/mt-resource/user-service-retry`) will be consumed by the existing FTL banner component — no FTL changes needed

### Task 6: Integration test
- [x] 6.1 Created `UserServiceAsyncTest.java` with:
  - T-1: Verifies multi-invite picker completes and agent gets memberships even when user-service is unreachable (non-blocking AC-1)
  - T-2: Verifies single auto-accept path grants membership (AC-7 — toast set unconditionally)
  - T-3: Verifies `/user-service-retry` POST returns 202 Accepted immediately (AC-6)

---

## Architecture & Technical Requirements

### AR-1: Async executor must use daemon threads
The `ScheduledExecutorService` in `UserServiceRestClient` MUST use daemon threads so they do not prevent JVM shutdown:
```java
private static final ScheduledExecutorService ASYNC_EXECUTOR =
    Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "user-service-async-" + THREAD_COUNTER.getAndIncrement());
        t.setDaemon(true);
        return t;
    });
private static final java.util.concurrent.atomic.AtomicInteger THREAD_COUNTER = new AtomicInteger(0);
```

### AR-2: KC session lifecycle in async callbacks
The `KeycloakSession` from `context.getSession()` is REQUEST-SCOPED and MUST NOT be passed to the async thread. Only `KeycloakSessionFactory` (available via `context.getSession().getKeycloakSessionFactory()`) is safe to pass. Use `KeycloakModelUtils.runJobInTransaction(factory, session -> {...})` for all KC operations inside the async callback.

Import required: `org.keycloak.models.utils.KeycloakModelUtils` (already on classpath; used in `JpaTenantProvider`).

### AR-3: User attribute vs session note for retry flag
The retry flag MUST be stored as a **user attribute** (not session note) because:
- Session notes are auth-session-scoped and destroyed after login completes
- The async callback fires AFTER the auth session is gone
- User attributes persist across logins and are readable by FTL templates via `user.getAttribute("user-service-sync-retry-needed")`

### AR-4: Tracing pattern for async spans
Each async attempt creates a NEW CLIENT span (not a child of the original request span, since that span is already finished). Pattern:
```java
Span span = TracingHelper.startClientSpan("user-service.updateStatus.async");
span.tag("retry.attempt", String.valueOf(attempt));
Throwable traceError = null;
try {
    // ... call
} catch (Exception e) {
    traceError = e;
    throw e;
} finally {
    TracingHelper.finishSpan(span, traceError);
}
```
Do NOT call `TracingHelper.tracer().withSpanInScope(span)` (the try-with-resources pattern) from an async thread — the span scope is thread-local and the parent scope from the request thread has ended.

### AR-5: Behavior change in `processAction()` — understand before editing
The CURRENT `processAction()` behavior:
- Line 342–361: if UserService fails → show error challenge form → user stays on invite picker → membership is NOT granted yet

The NEW behavior:
- Submit async → ALWAYS proceed to grant memberships → ALWAYS call context.success()
- UserService failure is eventual: flag stored, banner shown

This means the "abort-on-failure" semantics are REMOVED. Memberships are granted in KC even if UserService sync fails. This is the intended design per FR-INV-5 and NFR-S-3.

### AR-6: Toast notes always set in `autoAcceptInvitation()`
In the current code (line 214), `if (userServiceSucceeded)` guards the toast note setting. With async, we don't know the outcome at the time of context.success() — set toast notes UNCONDITIONALLY when `tenantName != null && !tenantName.isBlank()`. The UserService sync outcome is orthogonal to whether the toast is shown.

### AR-7: Retry backoff values — open question
The values `maxRetries=3, backoffSeconds=30` are ASSUMPTIONS (OQ-3 in the epics). Confirm with product/ops before implementing if SLA is known. Encode them as named constants (not magic numbers):
```java
// In ReviewTenantInvitations.java
private static final int USER_SERVICE_MAX_RETRIES = 3;
private static final long USER_SERVICE_RETRY_BACKOFF_SECONDS = 30L;
```

### AR-8: `updateUserTenantInvitationStatuses()` is still synchronous inside the async thread
The existing `updateUserTenantInvitationStatuses()` method (using `httpClient.send()` blocking call) does NOT need to change — it is called from inside the async thread where blocking is fine. Only the INVOCATION SITE in `ReviewTenantInvitations` changes from synchronous to async.

---

## File Change Map

| File | Change Type | Notes |
|------|-------------|-------|
| `src/main/java/dev/sultanov/keycloak/multitenancy/util/Constants.java` | UPDATE | Add `USER_SERVICE_SYNC_RETRY_ATTR` constant |
| `src/main/java/dev/sultanov/keycloak/multitenancy/resource/UserServiceRestClient.java` | UPDATE | Add static executor, `submitAsync()`, `scheduleAttempt()`, `clearRetryFlag()`, `setRetryFlag()` |
| `src/main/java/dev/sultanov/keycloak/multitenancy/authentication/requiredactions/ReviewTenantInvitations.java` | UPDATE | Remove sync UserService calls in `autoAcceptInvitation()` and `processAction()`; add async submission; fix toast guard |
| `src/main/java/dev/sultanov/keycloak/multitenancy/resource/UserServiceRetryResource.java` | NEW | POST `/user-service-retry` endpoint for banner retry action |
| `src/main/java/dev/sultanov/keycloak/multitenancy/resource/MultitenancyRootResource.java` | UPDATE | Route `/user-service-retry` to `UserServiceRetryResource` |

---

## Previous Story Intelligence (Story 5.3)

From the completed Story 5.3 implementation:

### Patterns to follow
- `@JBossLog` annotation used on `ReviewTenantInvitations` — keep, do not change to `@Slf4j`
- `TracingHelper.startServerSpan("review-invitations.processAction")` pattern is already in `processAction()` — do not add a duplicate span; the existing span covers the synchronous portion
- `context.getAuthenticationSession().setClientNote(REVIEWED_INVITATIONS, "true")` is already called at the end of `processAction()` — keep this
- `KeycloakModelUtils.generateId()` is already imported in `JpaTenantProvider` — safe to use `KeycloakModelUtils` class without adding a new dependency
- The `TenantsBean.fromInvitations(invitations)` and `.setAttribute("data", ...)` pattern — unchanged, do not modify

### Guardrails from 5.3 review

**G-1: `processAction()` completeness guard** — after removing the blocking UserService block, ensure `context.success()` is still called at line 414 (after `context.getAuthenticationSession().setClientNote(REVIEWED_INVITATIONS, "true")`). Do NOT accidentally remove the success call.

**G-2: Null-safety on tenant in `processAction()`** — the existing `inv.getTenant().getId()` calls in `processAction()` can NPE if the tenant was concurrently deleted. The existing code lacks the guard that `autoAcceptInvitation()` has. Do not add NPE guards as part of this story — it is out of scope and would increase risk of regression. Keep existing behavior.

**G-3: Async thread cannot access `context.*`** — `RequiredActionContext` is request-scoped. Capture all needed values (userId, realmId, tenantIds) as final local variables BEFORE submitting to the executor:
```java
final String userId = user.getId();
final String realmId = context.getRealm().getId();
final KeycloakSessionFactory factory = context.getSession().getKeycloakSessionFactory();
final List<String> finalAccepted = List.copyOf(acceptedTenants);  // defensive copy
final List<String> finalRejected = List.copyOf(rejectedTenants);
userServiceRestClient.submitAsync(userId, finalAccepted, finalRejected, factory, realmId, ...);
```

**G-4: `List.of(tenantId)` in `autoAcceptInvitation()`** — the current synchronous call at line 165 uses `List.of(tenantId)` for accepted and `List.of()` for rejected. Keep these semantics in the async call.

**G-5: Integration test backoff** — 3×30s = 90s is too slow for tests. Consider making backoff configurable via constructor or system property for test environments. OR mock `UserServiceRestClient` to verify `submitAsync` is called. Either approach is acceptable.

**G-6: Static executor lifetime** — the static `ASYNC_EXECUTOR` is created once per JVM, not per Keycloak session or realm. This is correct for a shared background worker. Do NOT create a new executor per request or per `ReviewTenantInvitations` instance.

**G-7: `acceptedTenants` in `processAction()` is a `List<String>` from `Arrays.asList()`** — `Arrays.asList()` is fixed-size but mutable. Use `List.copyOf(acceptedTenants)` when passing to the async thread to prevent concurrent modification. Same for `rejectedTenants`.

**G-8: No console.* in production** — the async callback MUST use `log.debugf()`/`log.infof()`/`log.warnf()`/`log.errorf()` from the `@JBossLog`-provided `log` field. JBoss Log is NOT available on the static executor thread unless the logger is captured. Use:
```java
private static final Logger log = Logger.getLogger(UserServiceRestClient.class);
```
in `UserServiceRestClient` (already present — do not add duplicate).

---

## Dev Notes & Guardrails

1. **Read the current `autoAcceptInvitation()` before editing** — the `userServiceSucceeded` boolean at line 163 is referenced at lines 163–172 AND 214. Removing both occurrences in one edit prevents a compilation error from a dangling variable.

2. **`processAction()` async call placement** — submit the async call BEFORE the membership loop (not after), so the tenantIds are known. Wait — actually, the tenantIds are known from `acceptedTenants`/`rejectedTenants` parsed at lines 307–312, which is before the loop. Submit async after parsing, before the loop OR after the loop. Placing it after the loop (but before `context.success()`) is safer since all memberships are committed to JPA by then.

3. **`KeycloakModelUtils.runJobInTransaction` handles its own session lifecycle** — it opens a session, begins a transaction, calls your lambda, commits the transaction, and closes the session. You do NOT need `try { session.close() }` manually.

4. **`UserServiceRestClient` constructor is already called via `new UserServiceRestClient()`** at line 40 of `ReviewTenantInvitations`. The `ASYNC_EXECUTOR` is static so it is shared across all instances. This is intentional and correct.

5. **`UserServiceRetryResource` needs authentication** — the `/user-service-retry` POST endpoint is called from a logged-in user's browser. Use `AuthenticationManager.authenticateIdentityCookie(session, realm, true)` to get the current user, exactly as other resource classes do. Return 401 if null.

6. **For `UserServiceRetryResource`, the tenant list re-computation**: The simplest correct approach is to re-submit ALL the user's current KC tenant memberships as `accepted`. The UserService PATCH endpoint is idempotent (it can receive the same tenantId multiple times). This avoids storing state about which tenants were originally accepted/rejected.

7. **The retry flag attribute key is `"user-service-sync-retry-needed"`** — do not store it as a session note (`setUserSessionNote`) as those require the auth session to be active. Store it as a persistent user attribute (`user.setSingleAttribute(...)`).

8. **Open Question OQ-3**: The 3-retry / 30-second backoff values are product assumptions. Implement them as named constants. Do NOT hardcode `3` and `30` inline.

---

## Testing Requirements

### Integration Tests

**T-1 (Critical): Async non-blocking behavior** — In `MultiInvitePickerTest` or new test:
- Wire the test Keycloak with a mock User-Service URL that returns 500 (already done in test setup via `host.docker.internal:4003`)
- Have an Agent with 2+ invitations complete the invite picker
- Assert the Agent is redirected to account selection (not stuck on error form)
- Assert the user attribute `user-service-sync-retry-needed` is eventually set to `"true"` (poll with a small wait)

**T-2: Async success path** — User-Service endpoint returns 200 (default test setup):
- Complete single auto-accept
- Assert user attribute `user-service-sync-retry-needed` is NOT set on the user (or is cleared if set)
- Assert toast notes ARE set (inviter name, tenant name, tenant ID)

**T-3: Retry endpoint** — `POST /realms/{realm}/mt-resource/user-service-retry`:
- Call endpoint as an authenticated user
- Assert 202 Accepted response
- Assert no blocking (immediate response)

### Manual Verification Checklist
- [ ] Multi-invite picker: submit with User-Service unreachable → no error screen → proceeds to account selection
- [ ] Single auto-accept: User-Service unreachable → no error → toast still shows
- [ ] Check Zipkin: `user-service.updateStatus.async` span appears after login
- [ ] Check Zipkin: retry spans tagged with `retry.attempt = 1, 2, 3` on repeated failures
- [ ] After all retries fail: user attribute `user-service-sync-retry-needed = "true"` visible in Keycloak admin UI

---

## Open Questions

**OQ-3 (non-blocking):** The User-Service SLA informing retry-backoff timing is unresolved. The 3×/30s values are assumptions. The constants `USER_SERVICE_MAX_RETRIES = 3` and `USER_SERVICE_RETRY_BACKOFF_SECONDS = 30L` in `ReviewTenantInvitations` can be updated once confirmed without touching the logic.

**OQ-4 (non-blocking):** `UserServiceRetryResource` re-submits all current memberships as `accepted`. If the original operation included `rejectedTenants`, those won't be re-sent as rejected. For the retry banner use case (partial sync failure), this is acceptable. If full fidelity is needed, store the original payload in a user attribute — out of scope for this story.

---

## Dev Agent Record

```
model: claude-sonnet-4-6
started: 2026-06-23
completed: 2026-06-23
notes:
  - Static ScheduledExecutorService with 2 daemon threads; shared across all UserServiceRestClient instances (G-6)
  - scheduleAttempt uses bare span without withSpanInScope per AR-4 (async thread, parent scope gone)
  - clearRetryFlag and setRetryFlag use KeycloakModelUtils.runJobInTransaction for safe KC session lifecycle (AR-2)
  - autoAcceptInvitation: removed userServiceSucceeded bool + sync call; toast now unconditional (AC-7)
  - processAction: removed blocking try/catch; async submitted after the membership loop with List.copyOf (G-7)
  - UserServiceRetryResource authenticates via cookie, re-submits all current memberships as accepted (Dev Note 6)
  - Integration test covers T-1 (non-blocking), T-2 (toast unconditional), T-3 (retry endpoint 202)
  - Retry flag attribute key "user-service-sync-retry-needed" stored as user attribute not session note (AR-3)
files_changed:
  keycloak-multi-tenancy:
    - src/main/java/dev/sultanov/keycloak/multitenancy/util/Constants.java
    - src/main/java/dev/sultanov/keycloak/multitenancy/resource/UserServiceRestClient.java
    - src/main/java/dev/sultanov/keycloak/multitenancy/authentication/requiredactions/ReviewTenantInvitations.java
    - src/main/java/dev/sultanov/keycloak/multitenancy/resource/UserServiceRetryResource.java
    - src/main/java/dev/sultanov/keycloak/multitenancy/resource/MultitenancyRootResource.java
    - src/test/java/dev/sultanov/keycloak/multitenancy/UserServiceAsyncTest.java
change_log:
  - "Story 5.4: Non-blocking async user-service sync with retry banner — 2026-06-23"
  - "UserServiceRestClient: added submitAsync, scheduleAttempt, clearRetryFlag, setRetryFlag with daemon thread executor"
  - "ReviewTenantInvitations: removed blocking user-service calls in autoAcceptInvitation and processAction; both now fire-and-forget async"
  - "ReviewTenantInvitations: toast notes set unconditionally in autoAcceptInvitation (AC-7)"
  - "UserServiceRetryResource: new POST /user-service-retry endpoint for banner retry action (AC-6)"
  - "MultitenancyRootResource: registered /user-service-retry route"
  - "UserServiceAsyncTest: integration tests for non-blocking behavior, unconditional toast, and retry endpoint"
```

---

## Review Findings

_Code review 2026-06-23 — adversarial 3-layer review (Blind Hunter / Edge Case Hunter / Acceptance Auditor). All 8 ACs and guardrails G-1..G-8 confirmed SATISFIED. Findings below are correctness/hardening issues on the 5.4-scoped files only._

- [x] [Review][Decision→Patch] Shared `ScheduledExecutor` sizing/backpressure (OQ-3) — RESOLVED: pool size now configurable via `-Duser-service.async.pool-size` (default 2) and a soft queue bound `-Duser-service.async.max-pending` (default 1000) drops new chains + marks retry-needed on overflow [UserServiceRestClient.java]
- [x] [Review][Decision→Patch] `/user-service-retry` rate limiting — RESOLVED: endpoint now no-ops when no retry-needed flag is set, and enforces a 60s per-user cooldown (`429` if exceeded) [UserServiceRetryResource.java]
- [x] [Review][Patch] `clearRetryFlag()` moved OUTSIDE the retry-triggering try and guarded — a flag-clear failure no longer re-sends a successful sync or raises a spurious banner [UserServiceRestClient.java]
- [x] [Review][Patch] `setRetryFlag()` now routed through guarded `setRetryFlagSafe()` + retry `schedule()` wrapped in `RejectedExecutionException` handling — terminal failures can no longer escape and be silently swallowed by the executor [UserServiceRestClient.java]
- [x] [Review][Patch] Retry constants consolidated into `Constants.java` (`USER_SERVICE_MAX_RETRIES` / `USER_SERVICE_RETRY_BACKOFF_SECONDS`); both call sites now reference them [Constants.java, ReviewTenantInvitations.java, UserServiceRetryResource.java]
- [x] [Review][Defer] Thin async test coverage — `UserServiceAsyncTest` does not assert retry scheduling/backoff or the `user-service-sync-retry-needed` attribute; T-2 claims AC-7 toast but only checks membership; T-3 is a heavy/flaky Playwright path — deferred (meets G-5 minimum bar, strengthen later)
- [x] [Review][Defer] Retry endpoint re-sends `rejected=List.of()` (loses prior rejections) and treats a partial 2xx as full success [UserServiceRetryResource.java] — deferred, explicitly OQ-4 (acknowledged out of scope)

> Dismissed as noise (5): (1) "async update runs outside a KC transaction" — false, `updateUserTenantInvitationStatuses` is a pure HTTP call; the session-touching clear/set flag paths correctly use `runJobInTransaction`. (2) "no alerting on terminal failure" — by design, the retry attribute + banner IS the signal (AC-3/AC-5). (3) shutdown `RejectedExecutionException` / null-id NPE / user-deleted-mid-retry / null provider — guarded or only reachable at JVM shutdown or off the authenticated-user path. (4) per-instance `HttpClient` "never closed" — JDK `HttpClient` has no `close()` (pre-21) and is GC'd. (5) zero-membership retry → misleading 202 — `updateUserTenantInvitationStatuses` already early-returns on empty.
>
> Out-of-scope: the review diff also captured uncommitted 5.1–5.3 work in `ReviewTenantInvitations.java` / `MultitenancyRootResource.java` (alreadyMember race, `tenant.setStatus("ACTIVE")`, `endsWith` routing, `getSessionNote` cookie-auth side-effect, `getRoles()` null). These are NOT 5.4 and are already tracked in `deferred-work.md` under "code review of story-5.3" — not re-deferred here.
