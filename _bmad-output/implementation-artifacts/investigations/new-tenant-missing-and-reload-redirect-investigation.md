# Investigation: New business/tenant missing from `/tenant/user-tenants` after post-login creation; reload redirects to Keycloak tenant-selection page

## Hand-off Brief

1. **What happened.** A tenant/business created via the application service after login does not appear in `GET /realms/.../tenant/user-tenants`, and reloading the site then routes the user through Keycloak's `select-active-tenant` required-action page.
2. **Where the case stands.** Two Confirmed code-level defects found by direct code reading; the root linkage between "application service" tenant creation and the missing membership record is Hypothesized pending visibility into the application service's call (which lives outside this repo).
3. **What's needed next.** Confirm, from the application service side, which identity/token is used when it calls this SPI's tenant-creation endpoint (or whether it calls it at all) — see Missing Evidence.

## Case Info

| Field            | Value                                                                                        |
| ---------------- | --------------------------------------------------------------------------------------------- |
| Ticket           | N/A                                                                                             |
| Date opened      | 2026-08-12                                                                                      |
| Status           | Active                                                                                          |
| System           | keycloak-multi-tenancy custom SPI (Java, Keycloak realm `azguards-whatsapp`), realm `whatatalk-auth.azguardstech.com` |
| Evidence sources | Repo source code (`src/main/java/dev/sultanov/keycloak/multitenancy/**`); a live JWT access token pasted by the user (decoded for claim inspection only) |

## Problem Statement

User-reported: "once new business in create after login from application service. that business is not coming in this API [`GET tenant/user-tenants`] also if reloaded website redirects to tenant selection page of keycloak."

Two symptoms bundled together:
1. A tenant created post-login via "the application service" is absent from `/tenant/user-tenants`.
2. Reloading the site after that creation causes a redirect into Keycloak's tenant-selection required action.

## Evidence Inventory

| Source                                              | Status    | Notes                                                                 |
| ---------------------------------------------------- | --------- | ---------------------------------------------------------------------- |
| `resource/GetUserTenants.java`                       | Available | Implements `/tenant/user-tenants`                                     |
| `resource/MultitenancyRootResource.java`             | Available | Confirms path routing for `/user-tenants`                             |
| `util/TokenVerificationUtils.java`                   | Available | Shows how the bearer token resolves to a `UserModel`                   |
| `resource/TenantsResource.java`                      | Available | Admin tenant-creation endpoint (`POST .../tenants`)                    |
| `resource/AbstractAdminResource.java`                | Available | Shows how `auth.getUser()` is derived for `TenantsResource`            |
| `model/jpa/JpaTenantProvider.java`                   | Available | `createTenant()` — confirms membership grant target                    |
| `authentication/requiredactions/SelectActiveTenant.java` | Available | Tenant-selection required action + trigger logic                   |
| `authentication/requiredactions/CreateTenant.java`   | Available | Browser-flow tenant creation (not the app-service path)                |
| `resource/SwitchActiveTenant.java`                   | Available | In-app "switch active tenant" endpoint                                 |
| `util/Constants.java`                                | Available | Session-note / attribute key definitions                               |
| Decoded sample JWT (user-supplied)                   | Available | Used only to compare `jti` vs `sid` claim values — see Finding 2       |
| Application-service source (tenant-creation caller)  | Missing   | Not in this repo — cannot confirm which identity/token it authenticates with |
| Server logs around the reported repro                | Missing   | Would show which endpoint the app service actually hit and with what subject |

## Investigation Backlog

| # | Path to Explore                                                                 | Priority | Status | Notes |
| - | -------------------------------------------------------------------------------- | -------- | ------ | ----- |
| 1 | Confirm which credential the application service presents when creating a tenant | High     | Open   | Determines whether Finding 3 (Hypothesis) is the actual cause |
| 2 | Check Keycloak/app server logs for the `tenant.create` span around the repro time | Medium   | Open   | `TenantsResource.createTenant` logs `"Tenant created successfully: {}"` and the tracing span tags `user.id` implicitly via `auth` |
| 3 | Verify `AccessToken.getId()` vs `getSessionState()`/`getSessionId()` semantics against the exact Keycloak version pinned in `pom.xml` | Medium | Open | Needed to fully close out Finding 2 |

## Confirmed Findings

### Finding 1: `/tenant/user-tenants` and the tenant-selection trigger both query live data, not token claims

**Evidence:** `src/main/java/dev/sultanov/keycloak/multitenancy/util/TokenVerificationUtils.java:38-40` extracts `userId = token.getSubject()` and does `session.users().getUserById(realm, userId)` (live DB lookup). `src/main/java/dev/sultanov/keycloak/multitenancy/resource/GetUserTenants.java:56` then calls `tenantProvider.getUserTenantsStream(realm, user)` — a live JPA query (`JpaTenantProvider` uses `TenantMembershipEntity`/`TenantEntity` criteria queries, not any cached token claim).

**Detail:** This rules out "stale access token" as the cause of the missing tenant. Even though the pasted JWT does carry static `tenants`/`all_tenants` custom claims (added at token-issuance time by `resource/TokenManager.java:77-92`), the `/user-tenants` REST endpoint the user is calling does not read those claims — it re-derives the tenant list from the database on every call using only the token's `sub` (user ID). If the new tenant is missing from this endpoint's response, the membership record for this exact user ID genuinely does not exist yet in Keycloak's tenant-membership table — it is not a refresh/caching issue.

### Finding 2: `SwitchActiveTenant` looks up the user session by the wrong token field, so the active-tenant session note it tries to persist is silently dropped

**Evidence:** `src/main/java/dev/sultanov/keycloak/multitenancy/resource/SwitchActiveTenant.java:96` —
```java
UserSessionModel userSession = session.sessions().getUserSession(realm, token.getId());
if (userSession != null) {
    userSession.setNote(Constants.ACTIVE_TENANT_ID_SESSION_NOTE, request.getTenantId());
}
```
`token.getId()` returns the access token's JWT ID (`jti` claim), not the Keycloak user-session ID. The user-supplied sample token confirms these are different values on the same token: `"jti":"ontrna:11a49777-9528-921d-ce84-45a2f090cc3e"` vs `"sid":"t9Al33uMmNkLHOstZwB6RetC"`. `KeycloakSession.sessions().getUserSession(RealmModel, String)` expects a user-session ID (the `sid` value), not a `jti`.

**Detail:** Because the two values never coincide, `getUserSession(realm, token.getId())` will not resolve the caller's actual `UserSessionModel`, so the `if (userSession != null)` branch on line 97 is effectively dead code in normal operation — the "Also store in session note for protocol mapper" write never lands on the real session. This means the in-app tenant-switch API never durably updates the session-level `active-tenant-id` note that `SelectActiveTenant.evaluateTriggers()` (`authentication/requiredactions/SelectActiveTenant.java:40-42`) relies on to skip re-prompting for tenant selection.

## Deduced Conclusions

### Deduction 1: The missing tenant is a data-linkage problem in tenant creation, not a display/caching problem

**Based on:** Finding 1.

**Reasoning:** `/tenant/user-tenants` performs a live per-user membership query keyed on `token.sub`. If the created business is absent from the response for that user's own token, no membership row exists for `(new_tenant_id, this_user_id)` at query time.

**Conclusion:** The fix must happen at tenant-creation time (ensuring the membership is written for the correct user), not at the read endpoint — the read endpoint is already correct.

### Deduction 2: Reload-triggered tenant-selection redirect is consistent with the session-note bug in Finding 2, independent of the missing-tenant issue

**Based on:** Finding 2, and `SelectActiveTenant.evaluateTriggers`/`requiredActionChallenge` (`authentication/requiredactions/SelectActiveTenant.java:32-94`).

**Reasoning:** `evaluateTriggers` only skips adding the `select-active-tenant` required action when it finds `Constants.ACTIVE_TENANT_ID_SESSION_NOTE` on the auth session or the existing SSO user session (`getSessionNote`, lines 200-206). The only two places in this codebase that write that note are (a) `SelectActiveTenant.processAction` (`:114`), which correctly writes it onto the **authentication session** during an interactive login/challenge, and (b) `SwitchActiveTenant` (`:98`), which is meant to update it on an **already-established session** after an in-app tenant switch but — per Finding 2 — never actually reaches a live session object. Any reload/SSO re-check that creates a fresh authentication session and relies on the user session's note (rather than a note carried over from the very same login) will find nothing, and — because this user already belongs to 8+ tenants — `getFilteredTenantMemberships(...).size() > 1` is true, so the required action is re-added and the browser is routed to the Keycloak tenant-selection form.

**Conclusion:** Whenever a user has switched tenant via the in-app API (not the login-time picker) prior to a reload, the reload will unconditionally re-show the Keycloak tenant-selection page, because the note that would normally suppress it was never actually persisted.

## Confirmed Findings (cross-repo, added 2026-08-12)

### Finding 3: `azguards-application` creates the Keycloak tenant using a client_credentials service-account token, never the end user's

**Evidence:**
- `azguards-application/src/main/java/com/azguards/application/service/BusinessCreationService.java:72-74` calls `tenantService.createTenant(...)`, passing only a plain `userId` string — no `Authorization`/token parameter is threaded through from the incoming HTTP request anywhere in this class.
- `azguards-keycloak-lib/src/main/java/com/azguards/keycloak/service/TenantServiceImpl.java:75` → `keycloakClient.createTenant(requestBody)`.
- `azguards-keycloak-lib/src/main/java/com/azguards/keycloak/config/KeycloakClient.java:44-56` (`executeRestCall`): `String token = tokenManager.getAccessTokenString(); ... headers.setBearerAuth(token.trim());` — the token is minted fresh, not forwarded from the caller.
- `azguards-keycloak-lib/src/main/java/com/azguards/keycloak/config/CustomTokenManager.java:57-69`: `formData.add("grant_type", "client_credentials"); formData.add("client_id", clientId); formData.add("client_secret", clientSecret);`, with `clientId`/`clientSecret` = `azguards-whatsapp-admin` / a static secret (`CustomTokenManager.java:37-38`, `azguards-application/src/main/resources/application-local.properties:10-11`).
- `KeycloakClient.createTenant()` posts to `keycloak.tenant.url` (`azguards-application/src/main/resources/application-local.properties:13`, e.g. `.../realms/azguards-whatsapp/tenants`) — this is this SPI's `TenantsResource.createTenant` (`resource/TenantsResource.java:37-101`).

**Detail:** This confirms (formerly Hypothesis 1) that the tenant-creation call reaching `TenantsResource.createTenant` in this repo is authenticated as the `azguards-whatsapp-admin` client's service account, not the real end user. `AbstractAdminResource.setupAuth()` (`resource/AbstractAdminResource.java:108`) resolves `auth.getUser()` from that token, so `JpaTenantProvider.createTenant()` (`model/jpa/JpaTenantProvider.java:80`, `tenant.grantMembership(user, Set.of(Constants.TENANT_ADMIN_ROLE))`) grants `TENANT_ADMIN` membership to `service-account-azguards-whatsapp-admin`, not to the real user. This is the root cause of the missing-tenant symptom: `GetUserTenants` correctly queries live membership rows keyed on the real user's ID (Finding 1), but no such row was ever created — the row that was created belongs to the service account instead.

### Finding 4: A second, compounding gap — the real user only ever receives an invitation, not a membership, and nothing forces them to accept it

**Evidence:**
- `BusinessCreationService.java:91-95` sends the real end user an invitation via `tenantService.createInvitation(...)` → `TenantInvitationsResource.createInvitation` (`resource/TenantInvitationsResource.java:59-90`), which calls `tenant.addInvitation(...)` (`:76`) — **not** `grantMembership`.
- Membership from an invitation is only granted later, when the user goes through the `ReviewTenantInvitations` required action on a **subsequent login** (`authentication/requiredactions/ReviewTenantInvitations.java:170,354`: `inv.getTenant().grantMembership(user, inv.getRoles());`).

**Detail:** For a brand-new user, a forced next-login step is a natural onboarding gate. But this flow is for an **already-authenticated** user creating an *additional* business — nothing in `BusinessCreationService` or the frontend forces a re-login/accept step afterward, so even once Finding 3 is fixed (correct user gets the membership grant path), the current design still routes new businesses added post-login through an invitation the user must separately discover and accept before it appears in `/tenant/user-tenants`. This needs a product decision, not just a code fix: either grant membership directly for this specific flow (bypassing the invitation step, since the creator already proved ownership by being an authenticated actor triggering their own business creation), or explicitly surface the pending invitation in the app and prompt accept-now.

### Finding 5: First-time onboarding (brand-new user, zero tenants) uses the exact same service-account bug, but is usually masked by `ReviewTenantInvitations`'s auto-accept + required-action priority ordering

**Evidence:**
- Distinct onboarding path confirmed: `whats-app-business-frontend/src/Onboarding/Business/Service/businessOnboardingThunks.js:20` (`POST api/v1/business/onboard`, no auth header — user has no session yet) → `BusinessOnboardingController.onboardBusiness()` (`azguards-application/.../controller/BusinessOnboardingController.java:35-44`) → `BusinessOnboardingService.onboardBusiness()` (`:34-58`) → `OnboardingOrchestratorService.submitRegistration()`/`completeOnboarding()` (`.../service/OnboardingOrchestratorService.java:82-92, 330-391`) — a separate flow from `BusinessCreationService` ("additional business"), but landing on the **same** `TenantService`/`KeycloakClient`/`CustomTokenManager` bean chain (`azguards-keycloak-lib`), which authenticates with the same `client_credentials` / `azguards-whatsapp-admin` service account (`OnboardingOrchestratorService.java:342` calls `tenantService.createTenant(...)`; `CustomTokenManager.java:57-94`).
- Same invitation-only gap: `sendOwnerInvitation()` (`OnboardingOrchestratorService.java:294-328`) calls `tenantService.createInvitation(...)` (`:326`) — not a membership grant — identical pattern to Finding 4.
- Mitigating mechanism found in this repo: required-action priorities in the test realm export (`src/test/resources/realm-export.json:2480-2505`) are `review-tenant-invitations`=1001, `create-tenant`=1002, `select-active-tenant`=1003 (lower runs first, `RequiredActionProviderModel.RequiredActionComparator`). On the user's very next (first) interactive Keycloak login: `ReviewTenantInvitations.evaluateTriggers` (`authentication/requiredactions/ReviewTenantInvitations.java:44-74`) finds the pending owner invitation; because there's exactly one invitation and the user's email is already verified (`OnboardingOrchestratorService.java:361-365` sets this right after OTP confirm), `requiredActionChallenge` auto-accepts it (`ReviewTenantInvitations.java:121-237`): grants real membership (`:170`), revokes the invitation (`:188`), and explicitly removes the `create-tenant` required action while adding `select-active-tenant` (`:200-202`) — all before `CreateTenant`'s own challenge ever runs (required actions are re-derived live and re-sorted each step, `AuthenticationManager.getApplicableRequiredActionsSorted`). `SelectActiveTenant.requiredActionChallenge` then sees exactly 1 membership and auto-selects it (`SelectActiveTenant.java:75-78`), so no tenant-picker screen appears either.

**Detail:** For the typical case (single owner invitation, first-ever interactive login, production required-action priorities matching the test fixture), a brand-new user does **not** get stuck in a `create-tenant` loop or see an empty tenant list — the invitation-to-membership conversion happens transparently during that first login. However, this is a coincidental mitigation, not a fix: the same underlying defect (service-account-owned tenant creation, invitation instead of membership) is present. There is a real window — from the moment onboarding's verification-confirm step completes until that next interactive login completes — where the new user has a Keycloak account and a Keycloak tenant but **zero real memberships**, only a pending invitation. Anything that checks membership in that window (an immediate frontend call to `/tenant/user-tenants` right after onboarding without an intervening full interactive login, a silent SSO/token-refresh reuse, or a backend check via `listMembershipsByUserId`) would see zero tenants for that brand-new user, reproducing the same symptom reported for "additional business" creation. This also silently depends on production's required-action priority values matching the test fixture (not independently confirmed) and on there being exactly one pending invitation — multiple invitations would surface the review-invitations picker UI instead of auto-accepting.

## Hypothesized Paths

_All hypotheses originally listed here were confirmed by cross-repo investigation — see Findings 3-5 above. No open hypotheses remain for the missing-tenant symptom._

## Missing Evidence

| Gap                                                                 | Impact                                                                 | How to Obtain                                                                 |
| --------------------------------------------------------------------| ------------------------------------------------------------------------| --------------------------------------------------------------------------------|
| Application-service code/config for how it calls Keycloak on business creation | Needed to confirm/refute Hypotheses 1 and 2                          | Review the application service repo's tenant/business-creation handler          |
| Keycloak server logs / admin events around the repro time            | Would show whether `TenantsResource.createTenant` ran, and for which subject | Pull logs filtered on `tenant.create` span or `"Tenant created successfully"`  |
| Exact Keycloak version pinned for this SPI                            | Needed to verify `AccessToken.getId()`/`getSessionState()`/`getSessionId()` semantics precisely for Finding 2 | Check `pom.xml` keycloak.version and corresponding `AccessToken` source          |

## Source Code Trace

| Element       | Detail                                                                                          |
| ------------- | -------------------------------------------------------------------------------------------------|
| Error origin  | Symptom 1: unresolved — likely outside this repo, in the application service's tenant-creation call (Hypotheses 1/2). Symptom 2 (Confirmed): `resource/SwitchActiveTenant.java:96` |
| Trigger       | Symptom 2 fires whenever a user switches tenant via the in-app `/tenant/switch` API and then the browser re-runs the authentication flow's required-action evaluation (e.g., page reload triggering silent SSO) |
| Condition     | Symptom 2 requires the user to belong to >1 tenant (`getFilteredTenantMemberships(...).size() > 1`, `SelectActiveTenant.java:49`, `:75`) — true for this user (9 tenants) both before and after the new business, so this bug is not new-tenant-specific but pre-existing |
| Related files | `resource/SwitchActiveTenant.java`, `authentication/requiredactions/SelectActiveTenant.java`, `util/Constants.java`, `resource/TokenManager.java` (for how the same claims are (re)issued into new tokens) |

## Conclusion

**Confidence:** High.

Both symptoms are now Confirmed with a complete causal chain, spanning `keycloak-multi-tenancy`, `azguards-keycloak-lib`, and `azguards-application`:

- **Symptom 1 (missing tenant):** `azguards-application`'s `BusinessCreationService` creates the Keycloak tenant using `azguards-keycloak-lib`'s `KeycloakClient`, which authenticates via a `client_credentials` grant as the `azguards-whatsapp-admin` service account (Finding 3) — never the real end user's token. `TenantsResource.createTenant` in this repo grants `TENANT_ADMIN` membership to whoever's token is on that call, so the membership lands on the service account, not the user. `GetUserTenants`/`SelectActiveTenant` correctly query live membership rows for the real user (Finding 1) and correctly find none. Compounding this, the real user is only ever sent an invitation (Finding 4), which requires a subsequent login's `ReviewTenantInvitations` required action to convert into a membership — a step nothing currently forces for an already-logged-in user adding an extra business.
- **Symptom 2 (reload → tenant-selection redirect):** `SwitchActiveTenant.java` had a session-lookup bug (`token.getId()`, the JWT's `jti`, instead of the session ID) that made its active-tenant session-note write a silent no-op (Finding 2). **Fixed 2026-08-12** — see Recommended Next Steps.

## Recommended Next Steps

### Fix direction

- **Mechanism A (session-note bug) — FIXED 2026-08-12:** `SwitchActiveTenant.java:98` now calls `session.sessions().getUserSession(realm, token.getSessionId())` instead of `token.getId()`. Verified via `javap` against `keycloak-core-26.6.3.jar` that `AccessToken` (via `IDToken`) exposes `getSessionId()` returning the `sid` claim, matching the semantics `UserSessionProvider.getUserSession(RealmModel, String)` expects. Also added a warning log when the session still can't be resolved, so a future regression is observable instead of silent. Compiles clean under JDK 21 (`mvn compile`).

- **Mechanism B (missing tenant) — FIXED 2026-08-12, cross-repo (Option B chosen: explicit target-user, authorization-gated).** Implemented as two complementary mechanisms sharing one trust boundary, because `OnboardingOrchestratorService.completeOnboarding()` creates the Keycloak Tenant *before* the owning Keycloak User exists (traced during implementation) — a pure "field on create" couldn't cover that ordering, only the `BusinessCreationService` ("additional business") flow where the user already exists.

  1. **`keycloak-multi-tenancy`** (this repo):
     - `TenantRepresentation.ownerUserId` (new field) — `TenantsResource.createTenant` (`:87`, `resolveOwner()` at `:285-300`) grants membership to this user instead of `auth.getUser()` when set. Gated on `auth.isTenantsManager()` (existing role concept, already used by `listTenants`); if the caller lacks it, or the target user doesn't exist, falls back to `auth.getUser()` (today's behavior) rather than hard-failing tenant creation — so a not-yet-configured realm degrades safely instead of breaking.
     - New endpoint `PUT {tenantId}/memberships/users/{userId}` (`TenantsResource.grantMembershipByUserId`, `:241-276`) — grants membership to an explicit user synchronously, same `isTenantsManager()` gate (403 if missing, since this is new/additive — nothing depends on it succeeding yet). Needed for the onboarding flow where `ownerUserId` can't be set at creation time.
  2. **`azguards-keycloak-lib`**: `ownerUserId` threaded through `TenantRepresentation` (model) and `TenantRegistrationRecord` (dto) so it survives `TenantServiceImpl.createTenant`'s internal builder round-trip; new `TenantService.grantMembershipByUserId(tenantId, userId, roles)` → `KeycloakClient.grantMembershipByUserId` (PUT to the new endpoint) → new `TenantMembershipGrantRequest` model.
  3. **`azguards-application`**:
     - `BusinessCreationService.createBusiness()`: passes `userId` as `ownerUserId` at tenant-creation time (user already known here). Before sending the owner invitation, checks `tenantService.listMembershipsByUserId(userId)` for the new tenant — if already a member (realm correctly configured), skips the invitation entirely; otherwise falls back to the pre-existing invitation flow (realm not yet configured).
     - `OnboardingOrchestratorService.sendOwnerInvitation()`: tries `tenantService.grantMembershipByUserId(tenantId, userId, [ownerRole])` once both tenant and user exist; on failure (`RuntimeException`, e.g. role not yet granted), falls back to the original `createInvitation` call. Also resolves **Finding 4/5**: membership is now granted synchronously during onboarding/business-creation instead of requiring the user to accept an invitation on a later login.

  **Manual step required, cannot be done from code:** the `azguards-whatsapp-admin` client's service-account user needs Keycloak's `manage-tenants` client role (on the `realm-management` client, per `TenantAdminAuth.isTenantsManager()`) granted in each realm/environment (local/dev/prod) via the Keycloak admin console or realm config, or the new code paths will keep silently falling back to the pre-existing (buggy) behavior. Verify by confirming `TenantsResource`'s log line `"Caller lacks tenants-manager role..."` stops appearing after granting it.

  **Compile verification:** `keycloak-multi-tenancy` compiles clean (`mvn compile`, JDK 21). `azguards-keycloak-lib` and `azguards-application` could not be compiled in this environment — both depend on `com.azguards:azguards-common-lib`, a private AWS CodeArtifact package (`giftcardswap` domain, `eu-central-1`), and the cached auth token in `~/.m2/settings.xml` has expired (pre-existing environment gap, confirmed unrelated to these changes — none of the cached versions of that artifact have ever fully resolved in this sandbox). Refresh via `aws codeartifact login --tool maven --repository java_artifact --domain giftcardswap --domain-owner 056539964040 --region eu-central-1` (adjust to the actual stored profile) and re-run `mvn compile` in both repos before merging. All edits were manually reviewed for type/import/signature consistency and follow existing patterns in each file (e.g. `grantMembershipByUserId` mirrors the existing `revokeMembershipByUserId` end-to-end).

### Diagnostic

- Pull Keycloak logs for the `tenant.create` span / `"Tenant created successfully: {}"` log line at the time of a fresh repro, and confirm the `sub`/`azp` is `service-account-azguards-whatsapp-admin`.
- Query the `tenant_membership` table for the new tenant's ID — expect the row's `user_id` to match the service account, not the real user.

## Reproduction Plan

1. Log in as a user with multiple tenants (as in the pasted token).
2. Call `PUT .../tenant/switch` to switch active tenant via the in-app API.
3. Reload the page (triggering silent SSO / re-authentication).
4. Observe redirect to Keycloak's `select-tenant.ftl` page — this isolates and reproduces Finding 2 independent of the missing-tenant issue.
5. Separately, create a new business through the application service, then immediately call `GET .../tenant/user-tenants` with the end user's own fresh token — if the tenant is absent, capture the Keycloak logs from that exact window to identify which identity performed the creation (isolates Hypotheses 1/2).

## Side Findings

- `resource/UserServiceRestClient.java` and `resource/UserServiceRetryResource.java` implement a deliberate async-retry mechanism for keeping an external `user-service` in sync with Keycloak-side tenant-invitation status changes, including a `user-service-sync-retry-needed` user attribute and a manual `/user-service-retry` endpoint. This confirms the system already anticipates Keycloak↔app-service drift in one direction; the reported bug may be the same class of problem in the other direction (app-service→Keycloak), for which no equivalent reconciliation currently exists.
- `TenantsResource.createTenant` silently defaults `status` via `org.keycloak.utils.StringUtil.isNotBlank(status) ? status : "ACTIVE"` (`JpaTenantProvider.java`) — not related to this investigation but worth noting if tenant status is ever part of a future visibility bug.
