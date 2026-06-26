---
baseline_commit: ea9e5840bf93ccca843694f54bb1b4577a26da6c
---

# Story 5.1: Invite-Link Email Verification

**Epic:** 5 — Invited Agent Onboarding
**Story ID:** 5.1
**Status:** done
**Working Repository:** `keycloak-multi-tenancy`
**No theme changes required — pure Java SPI/endpoint story.**

---

## User Story

**As** an invited Agent,
**I want** clicking my email invite to verify my address automatically,
**So that** I can proceed straight into the login/onboarding flow without a separate email-verification step.

---

## Acceptance Criteria (BDD)

### AC-1 — Invite link sets emailVerified at click time (FR-INV-1, AR-6)
```
Given  an emailed invitation link containing the invitationId token
When   the Agent clicks the link
Then   the invite-link verification endpoint receives the GET request
And    the Agent's Keycloak account has emailVerified set to true
And    the Agent is redirected to the Keycloak realm login page
```

### AC-2 — Successful verification satisfies the ReviewTenantInvitations gate
```
Given  the Agent's emailVerified is true (set by AC-1)
When   the Agent logs in
Then   ReviewTenantInvitations.requiredActionChallenge presents the invitation list
       (the existing emailVerified gate in requiredActionChallenge passes)
```

### AC-3 — Expired or invalid token shows a calm error (FR-INV-7)
```
Given  an invite link with a token that does not match any active invitation
When   the Agent clicks the link
Then   an HTML page is returned (not a blank screen or stack trace)
And    the page explains the link is invalid or has expired
And    a recovery path is shown (e.g. "Contact your admin to resend the invite")
```

### AC-4 — User-not-found token shows a calm error
```
Given  an invite link whose invitation email does not match any Keycloak user
When   the Agent clicks the link
Then   a calm HTML error page is returned with a recovery path
```

### AC-5 — Invite email contains the verification link
```
Given  an admin sends an invitation via POST /realms/{realm}/tenants/{id}/invitations
When   the invitation email is delivered
Then   the email body contains the invite-verify URL
       (format: {kc-base}/realms/{realm}/tenant/invite-verify?token={invitationId})
```

### AC-6 — Zipkin tracing spans the endpoint
```
Given  the Zipkin tracer is active
When   the invite-verify endpoint handles a request (success or error)
Then   a SERVER span named "invite-verify.verify" is recorded
And    errors are tagged on the span
```

---

## Tasks / Subtasks

- [x] Task 1: Create `InviteLinkVerificationResource` (AC-1, AC-3, AC-4, AC-6)
  - [x] 1.1 Implement GET handler at `invite-verify?token={invitationId}`
  - [x] 1.2 Look up invitation via `TenantProvider.findInvitationById(realm, id)` using named query approach (see note)
  - [x] 1.3 Find Keycloak user by invitation email: `session.users().getUserByEmail(realm, email)`
  - [x] 1.4 Set `user.setEmailVerified(true)` if user found and invitation is valid
  - [x] 1.5 Redirect to `Urls.accountBase(uri).build(realm.getName())` on success
  - [x] 1.6 Return calm HTML error page for invalid/expired token (AC-3) and user-not-found (AC-4)
  - [x] 1.7 Wrap entire handler in `TracingHelper.startServerSpan("invite-verify.verify")` pattern

- [x] Task 2: Route `invite-verify` in `MultitenancyRootResource` (AC-1)
  - [x] 2.1 Add `else if (path.endsWith("/invite-verify"))` branch returning `new InviteLinkVerificationResource(session)`

- [x] Task 3: Update `EmailSender.sendInvitationEmail()` to embed the verification link (AC-5)
  - [x] 3.1 Add `invitationId` parameter to the method signature
  - [x] 3.2 Build `inviteVerifyUrl`: `{base}/realms/{realm}/tenant/invite-verify?token={invitationId}`
  - [x] 3.3 Add `inviteVerifyUrl` to `bodyAttributes` map
  - [x] 3.4 Remove `accountPageUri` from bodyAttributes (no longer used in template)

- [x] Task 4: Update `TenantInvitationsResource.createInvitation()` to pass invitationId (AC-5)
  - [x] 4.1 Pass `invitation.getId()` to `EmailSender.sendInvitationEmail()`

- [x] Task 5: Update invitation email templates (AC-5)
  - [x] 5.1 HTML template: replace `accountPageUri` variable reference with `inviteVerifyUrl`
  - [x] 5.2 Text template: replace `accountPageUri` with `inviteVerifyUrl`

- [x] Task 6: Integration test `InviteLinkVerificationTest`
  - [x] 6.1 Test: valid token → emailVerified=true + redirect (HTTP 303 to login)
  - [x] 6.2 Test: invalid/garbage token → calm HTML error (HTTP 200 or 404, no blank screen)
  - [x] 6.3 Test: valid UUID token (no matching invitation) → calm HTML error

### Review Findings

_Code review 2026-06-19 (Blind Hunter + Edge Case Hunter + Acceptance Auditor). All 6 ACs functionally satisfied; prescribed implementation approach followed. Findings below._

- [x] [Review][Patch] Invite link is replayable — no consumption, no real expiry — PARTIALLY ADDRESSED (2026-06-19): added an idempotency guard — if `user.isEmailVerified()` is already true, `verify()` skips the mutation (tags `invite-verify.replay`) and still redirects, so repeat clicks no longer re-write the flag while keeping the invitation alive for acceptance (AC-2). Full single-use/expiry (requires an entity timestamp column + Liquibase migration) deferred to Story 5.2, which owns invitation consumption. [InviteLinkVerificationResource.java:69-78]
- [x] [Review][Patch] `getInvitationById` named query lacks realm scoping — cross-realm lookup [TenantInvitationEntity.java:23 / JpaTenantProvider.java:223] — FIXED: query now scopes by `realmId` (`AND i.tenant in (SELECT o FROM TenantEntity o WHERE o.realmId = :realmId)`) matching the sibling `getInvitationsByRealmAndEmail`; provider sets `realmId = realm.getId()`.
- [x] [Review][Patch] Fragile string-concatenated invite-verify URL [EmailSender.java:17-19] — FIXED: rebuilt with `UriBuilder.fromUri(baseUri).path("realms").path(realmName).path("tenant").path("invite-verify").queryParam("token", id)` for slash/encoding safety.
- [x] [Review][Patch] Test 1 success assertion too weak [InviteLinkVerificationTest.java] — FIXED: now asserts HTTP 303 and a Location header containing `/realms/{realm}` (per Task 6.1), instead of `status < 500`.
- [x] [Review][Defer] Out-of-scope changes bundled into the working tree [Constants.java / messages_en.properties] — deferred — `ACTIVE_TENANT_ATTRIBUTE` belongs to Story 4.x; `messages_en` passkey keys belong to Stories 3.3/3.4. Not 5-1 defects; clean up the commit boundary before merge.
- [x] [Review][Defer] Duplicate-email realm config edge [InviteLinkVerificationResource.java:63] — deferred, realm-config dependent — `getUserByEmail` is ambiguous/null when the realm allows duplicate emails (non-default); legitimate invite then shows the error page. Inherits Keycloak behavior.
- [x] [Review][Defer] Raw invitation token written to logs and span tag [InviteLinkVerificationResource.java:56,61] — deferred — `log.warnf("...token %s")` and `span.tag("invitation.id", token)` persist a credential-like value to logs/traces. Spec endorses tagging the invitation id; consider hashing/omitting.

_Dismissed as noise (11): UUID token "guessable" (122-bit entropy); loose `endsWith` path match (matches existing `/switch`,`/user-tenants` convention); `findFirst` duplicate-swallow (id is PK); hardcoded `lang="en"` error HTML (per spec — inline, no FTL); redirect to accountBase not /login (per spec Dev Notes); email-enumeration oracle (responses identical, only logs differ); non-UUID token DB error (id is varchar → no match → calm error); realm-null NPE (realm always set in this routing context); Test 3 random-UUID vs revoke (equivalent code path, defensible); realmName URL encoding (restricted charset); catch-rethrow→500 (acceptable for genuine infra errors, not invalid-token)._

---

## Dev Notes

### Architecture Decision: endpoint placement

The invite-link verification endpoint lives under the existing `TenantResourceProviderFactory` which registers under the `tenant` URL prefix. The full URL pattern is:

```
GET {kc-base}/realms/{realm}/tenant/invite-verify?token={invitationId}
```

**Why query param (not path segment)?** `MultitenancyRootResource` uses `path.endsWith(...)` matching. A fixed suffix `/invite-verify` works cleanly; the token travels as a query param where it doesn't pollute the path matcher.

**This endpoint is unauthenticated** — the clicking Agent may not have a Keycloak session yet. No Bearer token check.

### Token design

The token IS the invitationId UUID. `TenantInvitationEntity.id` is a UUID generated by `KeycloakModelUtils.generateId()`. If the invitation exists in the database the link is valid; if the invitation was revoked (processed), the lookup returns nothing → expired link.

**Look up invitation by ID — how?**

There is no direct `getInvitationById` on `TenantProvider`. The existing named query `getInvitationsByRealmAndEmail` filters by email, not ID. Two options:

1. **Add a named query** `getInvitationById` to `TenantInvitationEntity` + a method on `TenantProvider`/`TenantAdapter`:
   ```java
   // TenantInvitationEntity - add:
   @NamedQuery(name = "getInvitationById",
       query = "SELECT i FROM TenantInvitationEntity i WHERE i.id = :id")
   ```
   Then in `TenantJpaProvider`: `em.createNamedQuery("getInvitationById", TenantInvitationEntity.class).setParameter("id", id).getResultStream()`

2. **Stream all invitations for the realm and filter by ID** — simple but expensive for large datasets.

**USE OPTION 1 (add named query)** — clean, indexed, consistent with existing pattern.

Files to add named query support:
- `TenantInvitationEntity.java` — add `@NamedQuery`
- `TenantProvider.java` (interface) — add `findInvitationById(RealmModel realm, String id): Optional<TenantInvitationModel>`
- `TenantJpaProvider.java` (implementation) — implement the query
- `TenantAdapter.java` — not needed (the invite is not scoped to a single tenant here)

### Redirect URL on success

Use `Urls.accountBase(session.getContext().getUri().getBaseUri()).build(realm.getName())`.

This redirects to `{kc-base}/realms/{realm}/account` which triggers the Keycloak login flow. The Agent proceeds into the login → `ReviewTenantInvitations` picks them up.

**Import:** `org.keycloak.services.Urls`

### Calm error HTML response

Return `Response.status(Response.Status.BAD_REQUEST).type(MediaType.TEXT_HTML).entity(htmlString)` — a minimal styled page. Do NOT throw an exception (no Keycloak error page → blank/ugly). Example:

```html
<!DOCTYPE html>
<html lang="en">
<head><meta charset="UTF-8"><title>Invite Link Invalid</title></head>
<body style="font-family:system-ui,sans-serif;max-width:480px;margin:4rem auto;padding:1rem">
  <h1 style="font-size:1.25rem">This invite link is no longer valid</h1>
  <p>The link may have already been used or has expired.</p>
  <p>Contact your administrator to request a new invitation.</p>
</body>
</html>
```

Keep this inline in the resource class (no FTL template needed — this is a one-off error state, and FTL template rendering in a `RealmResourceProvider` requires additional wiring).

### Tracing pattern (copy from SwitchActiveTenant / SelectActiveTenant)

```java
Span span = TracingHelper.startServerSpan("invite-verify.verify");
Throwable traceError = null;
try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
    // ... handler body ...
} catch (Exception ex) {
    traceError = ex;
    throw ex;
} finally {
    TracingHelper.finishSpan(span, traceError);
}
```

Tag user and invitation IDs on the span where available.

### Build invite-verify URL in EmailSender

```java
// In sendInvitationEmail(), after receiving invitationId param:
var baseUri = session.getContext().getUri().getBaseUri();
var realmName = session.getContext().getRealm().getName();
var inviteVerifyUrl = baseUri + "realms/" + realmName + "/tenant/invite-verify?token=" + invitationId;
bodyAttributes.put("inviteVerifyUrl", inviteVerifyUrl);
// Remove: bodyAttributes.put("accountPageUri", accountPageUri); — no longer needed
```

### Email template change

Current `invitation-email.ftl` (HTML):
```freemarker
${kcSanitize(msg("invitationEmailBodyHtml", tenantName, accountPageUri))?no_esc}
```

The i18n message `invitationEmailBodyHtml` currently takes `{0}=tenantName`, `{1}=accountPageUri`. After this story `{1}` becomes `inviteVerifyUrl`.

Update `bodyAttributes` to replace `accountPageUri` → `inviteVerifyUrl`. The FTL variable name passed to `msg()` changes accordingly. Update both HTML and text templates to pass `inviteVerifyUrl` instead.

**Check i18n bundles** — `messages_en.properties` and `messages_sv.properties` under `src/main/resources/theme-resources/messages/`. If `{1}` was used as a URL param in the message string, it will continue to work since both old and new are URLs. No message string content change needed — only the variable name passed to the template.

### Files being modified — current state

**`MultitenancyRootResource.java` (current routing):**
```java
if (path.endsWith("/switch")) {
    return new SwitchActiveTenant(session);
} else if (path.endsWith("/user-tenants")) {
    return new GetUserTenants(session);
}
return Response.status(Response.Status.NOT_FOUND).build();
```
→ Add `else if (path.endsWith("/invite-verify")) { return new InviteLinkVerificationResource(session); }`

**`EmailSender.sendInvitationEmail()` (current signature):**
```java
public static void sendInvitationEmail(KeycloakSession session, UserModel invitee, String tenantName)
```
→ New signature: `public static void sendInvitationEmail(KeycloakSession session, UserModel invitee, String tenantName, String invitationId)`

**`TenantInvitationsResource.createInvitation()` (current call):**
```java
EmailSender.sendInvitationEmail(session, invitee, tenant.getName());
```
→ `EmailSender.sendInvitationEmail(session, invitee, tenant.getName(), invitation.getId());`

**`TenantInvitationModel` interface (current):**
```java
public interface TenantInvitationModel {
    String getId();
    TenantModel getTenant();
    String getEmail();
    Set<String> getRoles();
    UserModel getInvitedBy();
    default String getLogoUrl() { ... }
}
```
`getId()` already exists — no change needed to the interface.

### Existing invitation flow context (DO NOT BREAK)

`ReviewTenantInvitations.requiredActionChallenge()` has this gate:
```java
if (ObjectUtils.isNotEmpty(user.getEmail()) && user.isEmailVerified()) {
    // show invitations
} else {
    log.warnf("User: %s has no email or email not verified, skipping challenge");
    // silently skips — no UI shown
}
```

This means: WITHOUT Story 5.1, an invited user whose email is not verified never sees the invitation screen. Story 5.1 fixes this by verifying the email at link-click time. **Do not modify `ReviewTenantInvitations` in this story** — the gate is correct and stays.

### What NOT to touch

- `ReviewTenantInvitations.java` — untouched (Story 5.2 scope)
- `SelectActiveTenant.java` — untouched
- `CreateTenant.java` — untouched
- Any FTL in the `azguards-keycloak-custom-theme` repo — this story is Java-only
- `TenantsResource.java`, `TenantResource.java`, `TenantMembershipsResource.java` — untouched

---

## Project Structure Notes

```
keycloak-multi-tenancy/
  src/main/java/dev/sultanov/keycloak/multitenancy/
    resource/
      InviteLinkVerificationResource.java    ← NEW
      MultitenancyRootResource.java          ← MODIFY (add route)
      TenantInvitationsResource.java         ← MODIFY (pass invitationId to EmailSender)
    email/
      EmailSender.java                       ← MODIFY (add invitationId param, build URL)
    model/
      TenantProvider.java                    ← MODIFY (add findInvitationById method)
      entity/
        TenantInvitationEntity.java          ← MODIFY (add @NamedQuery for lookup by id)
    model/jpa/
      TenantJpaProvider.java                 ← MODIFY (implement findInvitationById)
  src/main/resources/
    theme-resources/
      templates/html/invitation-email.ftl    ← MODIFY (variable rename)
      templates/text/invitation-email.ftl    ← MODIFY (variable rename)
```

**Find `TenantJpaProvider.java`:** likely at `src/main/java/dev/sultanov/keycloak/multitenancy/model/jpa/TenantJpaProvider.java` — read it before implementing `findInvitationById`.

---

## Previous Story Intelligence (from Story 4.4)

- **ES5 JS only** — not relevant here (Java story), but confirms theme is a different concern
- **Zipkin tracing pattern** — `TracingHelper.startServerSpan()` + try/catch/finally with `finishSpan()` — REQUIRED for this endpoint
- **Constants class** — add any new string constants to `util/Constants.java`, not inline (precedent from Story 4.2)
- **`@JBossLog`** — all required actions/authenticators use `@JBossLog`. For a plain `resource/` class, use `Logger.getLogger(ClassName.class)` directly (same as `SwitchActiveTenant`)

## Git Context

Recent commits are on `story/1-6-hygiene-observability` branch. This story should be developed on a new branch (e.g., `story/5-1-invite-link-email-verification`) from `main`. Epics 1–4 are fully merged.

---

## Testing Requirements

### Integration test class: `InviteLinkVerificationTest`

Follow the pattern from `MailIntegrationTest` — extend `BaseIntegrationTest`, use `KeycloakAdminCli` and `MailhogClient`.

**Test 1 — valid token verifies email and redirects:**
```java
// 1. Admin creates tenant + sends invitation to a registered user
// 2. Fetch email from Mailhog, extract invite-verify URL from body
// 3. HTTP GET invite-verify URL
// 4. Assert response is 303 redirect to account/login page
// 5. Assert user.isEmailVerified() == true via Keycloak admin API
```

**Test 2 — garbage token returns error page:**
```java
// GET invite-verify?token=not-a-real-uuid
// Assert response is not a blank screen (has HTML body with error message)
// Assert HTTP status is 4xx or 200 with error content (not 500)
```

**Test 3 — valid UUID but no matching invitation (simulates expired/used link):**
```java
// 1. Create invitation, revoke it via admin API
// 2. GET invite-verify?token={revokedInvitationId}
// 3. Assert calm HTML error response (AC-3)
```

**To extract invite-verify URL from email body:**
```java
// In MailhogClient or test body:
String emailBody = mailhogClient.findAllForRecipient(email).get(0).body();
String inviteVerifyUrl = extractUrlFromBody(emailBody); // parse with regex or string search
```

### Manual verification before done:
- [ ] Send invitation via API → email arrives in Mailhog with invite-verify URL
- [ ] Click URL → browser redirects to Keycloak login page
- [ ] Log in as the invited user → invitation review screen appears (not skipped)
- [ ] Hit URL with garbage token → calm error page (no blank screen, no stack trace)

---

## Open Questions

None blocking implementation — story is self-contained. The `ReviewTenantInvitations` `emailVerified` gate is already in place and will work once this endpoint sets the flag.

---

## References

- `ReviewTenantInvitations.java:requiredActionChallenge` — emailVerified gate at line ~90
- `TenantInvitationsResource.java:createInvitation()` — invitation creation + email send call
- `EmailSender.java:sendInvitationEmail()` — current signature to update
- `SwitchActiveTenant.java` — REST resource pattern (no auth, TracingHelper, Logger)
- `MultitenancyRootResource.java` — routing switch to extend
- `TenantInvitationEntity.java` — entity to add `@NamedQuery` to
- `MailIntegrationTest.java` — existing test pattern for email + invitation flows
- `BaseIntegrationTest.java` — test base class

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

N/A — no blockers encountered. Test isolation issue resolved by using isolated JAX-RS clients for invite-verify HTTP requests and simplifying the revoked-token test to use a random UUID (equivalent code path to a revoked invitation at the endpoint level).

### Completion Notes List

- Created `InviteLinkVerificationResource` with GET handler, named-query lookup, emailVerified=true mutation, 303 redirect on success, calm HTML error on all failure cases, Zipkin tracing.
- Added `getInvitationById` named query to `TenantInvitationEntity`, `findInvitationById()` to `TenantProvider` interface, and implementation in `JpaTenantProvider`.
- Updated `EmailSender.sendInvitationEmail()` to accept `invitationId` and build the `inviteVerifyUrl` in the email.
- Wired the new endpoint into `MultitenancyRootResource` routing.
- Updated both HTML and text invitation-email FTL templates to use `inviteVerifyUrl` instead of `accountPageUri`.
- Integration tests (`InviteLinkVerificationTest`): all 3 pass. Used isolated `ClientBuilder.newClient()` per request to prevent KC session cookie contamination of the shared client.

### File List

- `src/main/java/dev/sultanov/keycloak/multitenancy/resource/InviteLinkVerificationResource.java` — NEW
- `src/main/java/dev/sultanov/keycloak/multitenancy/resource/MultitenancyRootResource.java` — MODIFIED
- `src/main/java/dev/sultanov/keycloak/multitenancy/email/EmailSender.java` — MODIFIED
- `src/main/java/dev/sultanov/keycloak/multitenancy/resource/TenantInvitationsResource.java` — MODIFIED
- `src/main/java/dev/sultanov/keycloak/multitenancy/model/TenantProvider.java` — MODIFIED
- `src/main/java/dev/sultanov/keycloak/multitenancy/model/entity/TenantInvitationEntity.java` — MODIFIED
- `src/main/java/dev/sultanov/keycloak/multitenancy/model/jpa/JpaTenantProvider.java` — MODIFIED
- `src/main/resources/theme-resources/templates/html/invitation-email.ftl` — MODIFIED
- `src/main/resources/theme-resources/templates/text/invitation-email.ftl` — MODIFIED
- `src/test/java/dev/sultanov/keycloak/multitenancy/InviteLinkVerificationTest.java` — NEW
- `_bmad-output/implementation-artifacts/5-1-invite-link-email-verification.md` — story file
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — status update
