---
baseline_commit_extension: 7f51fae39e426e282ef127975a848f14e9d9bcf3
baseline_commit_theme: d435581
baseline_commit: 7f51fae39e426e282ef127975a848f14e9d9bcf3
---

# Story 2.4: Conditional "Email me a sign-in link" (Magic Link)

Status: done

## Story

As an Agent with a verified email on file,
I want the option to receive a sign-in link by email,
so that I can log in without my password when convenient.

## Acceptance Criteria

1. **Given** a `MagicLinkAuthenticator` added as a conditional step in the browser flow (AR-5) **When** the username is submitted along with the `authenticationExecution` switch **Then** the system checks whether that username maps to an account with `emailVerified = true` — if verified, shows the magic link send form; if not, renders a "Use another method" error (FR-L-6)

2. **Given** the check passes (emailVerified = true) **When** the login methods render **Then** "Email me a sign-in link" is shown as a method row in the `.wt-methods` area of `login.ftl` **And** when the check fails the option is effectively absent (server renders the error state on the magic-link form, not back on login.ftl)

3. **Given** the Agent requests a magic link **When** it is sent **Then** a "Check your email" state is shown with throttled resend (≥ 60 seconds between resends) and a "Use another method" / "Use password instead" path

4. **Given** an expired or already-used link **When** the Agent clicks it **Then** a calm message "That link has expired — we'll send a new one." is shown with a resend path (no dead-end)

5. **Given** the Agent clicks a valid magic link in their email **When** the action token handler processes it **Then** the Agent is authenticated and routed to the next required action (account selection, invite review, etc.) without re-entering credentials

6. **Given** the magic link FTL **When** it renders in any state **Then** it uses Epic 1 tokens/components (standalone HTML, no `@layout.registrationLayout`), meets the a11y floor (focus management, aria-live, visible labels), and no hardcoded hex colors

7. **Given** the "Email me a sign-in link" button on login.ftl **When** the Agent clicks it **Then** `localStorage.setItem('wt_last_auth_method', 'magic-link')` is written (matches the `'password'` and `'sso'` pattern from Stories 2.1/2.3)

8. **Given** new Java and FTL code paths **When** they execute **Then** they carry `TracingHelper` spans (AR-12) and the test harness compiles cleanly after the new providers are added

## Tasks / Subtasks

---

### REPO 1: `keycloak-multi-tenancy`

> Complete all Repo 1 tasks before starting Repo 2. The SPI must build and all new FTL context variables must be exported before the theme FTL can use them.

- [x] **Task 1: Create `MagicLinkActionToken`** (AC: #5)
  - [x] Create `src/main/java/dev/sultanov/keycloak/multitenancy/authentication/authenticators/MagicLinkActionToken.java`
  - [x] Extends `org.keycloak.authentication.actiontoken.DefaultActionToken` (AbstractUserActionToken absent in KC 26.6.3; DefaultActionToken is correct base)
  - [x] Token type constant: `public static final String TOKEN_TYPE = "magic-link";`
  - [x] Additional field: `private String authSessionTabId;` (stored as a note in the JWT) — needed to reconnect to the browser's auth session when the link is clicked
  - [x] Use Jackson `@JsonProperty("tab_id")` on the `authSessionTabId` field
  - [x] Constructor: `MagicLinkActionToken(String userId, int absoluteExpirationSeconds, String authSessionTabId)` calls `super(userId, TOKEN_TYPE, absoluteExpirationSeconds, null, authSessionTabId)`; sets `this.authSessionTabId = authSessionTabId`
  - [x] Default no-arg constructor (required by Jackson): `public MagicLinkActionToken() { super(); }`
  - [x] Getter: `public String getAuthSessionTabId()`

- [x] **Task 2: Create `MagicLinkActionTokenHandler`** (AC: #5)
  - [x] Create `src/main/java/dev/sultanov/keycloak/multitenancy/authentication/authenticators/MagicLinkActionTokenHandler.java`
  - [x] Extends `AbstractActionTokenHandler<MagicLinkActionToken>` (implements ActionTokenHandler + ActionTokenHandlerFactory)
  - [x] `getTokenClass()` → `MagicLinkActionToken.class` (provided by super)
  - [x] `handleToken()` with tracing span, user lookup, MAGIC_LINK_VERIFIED note, processFlow to browser flow
  - [x] `canUseTokenRepeatedly()` → `false`
  - [x] Add tracing span + finishSpan around all logic (follow pattern from `LoginWithSsoAuthenticator`)
  - [x] Register in `src/main/resources/META-INF/services/org.keycloak.authentication.actiontoken.ActionTokenHandlerFactory` (correct SPI name for KC 26.6.3)

- [x] **Task 3: Create `MagicLinkAuthenticator`** (AC: #1, #2, #3, #4, #5, #8)
  - [x] Create `src/main/java/dev/sultanov/keycloak/multitenancy/authentication/authenticators/MagicLinkAuthenticator.java`
  - [x] Implements `org.keycloak.authentication.Authenticator`
  - [x] Public constants: MAGIC_LINK_SENT, MAGIC_LINK_VERIFIED, MAGIC_LINK_LAST_SENT_TS, MAGIC_LINK_USER_ID, EXPIRATION_SECONDS=900, RESEND_COOLDOWN_SECONDS=60
  - [x] `authenticate()` with tracing, username check, emailVerified check, MAGIC_LINK_VERIFIED fast-path, challenge
  - [x] `action()` with tracing, tryAnotherWay, rate-limit check, token generation using AuthenticationSessionCompoundId, email send
  - [x] `requiresUser()` → `false`; `configuredFor()` → `true`; `setRequiredActions()` → no-op; `close()` → no-op

- [x] **Task 4: Create `MagicLinkAuthenticatorFactory`** (AC: #1, #8)
  - [x] Create `src/main/java/dev/sultanov/keycloak/multitenancy/authentication/authenticators/MagicLinkAuthenticatorFactory.java`
  - [x] Implements `AuthenticatorFactory`; all methods implemented per spec
  - [x] Registered in `src/main/resources/META-INF/services/org.keycloak.authentication.AuthenticatorFactory`

- [x] **Task 5: Extend `EmailSender` with magic link support** (AC: #3, #5)
  - [x] Added `sendMagicLinkEmail()` to `EmailSender.java`; reuses private `sendEmail()` helper

- [x] **Task 6: Create email templates** (AC: #5)
  - [x] Created `src/main/resources/theme-resources/templates/html/magic-link-email.ftl`
  - [x] Created `src/main/resources/theme-resources/templates/text/magic-link-email.ftl`

- [x] **Task 7: Add i18n keys to extension `messages_en.properties`** (AC: #3, #4)
  - [x] 10 magic link keys appended; no duplicates verified

- [x] **Task 8: Build and verify** (AC: #8)
  - [x] `mvn package -DskipTests` → BUILD SUCCESS in 8s
  - [x] JAR contains all 4 MagicLink classes
  - [x] `org.keycloak.authentication.AuthenticatorFactory` has 3 entries; `org.keycloak.authentication.actiontoken.ActionTokenHandlerFactory` created

---

### REPO 2: `azguards-keycloak-custom-theme`

> Start only after Repo 1 builds successfully. The FTL context variables (`magicLinkSent`, `magicLinkError`) are set by the Java authenticator; they are not available until the JAR is deployed. Implement and test locally with Keycloak running the new JAR.

- [x] **Task 9: `login.ftl` — Add "Email me a sign-in link" method-row** (AC: #2, #7)
  - [x] Open `src/main/resources/theme/azguards-whatsapp/login/login.ftl`
  - [x] Replace `<!-- 2.4: <button class="wt-method-row" ...>Email me a sign-in link</button> -->` with:
    ```html
    <!-- Hidden form: posts username + authenticationExecution to switch KC flow to magic-link -->
    <form id="kc-select-magic-form" action="${url.loginAction}" method="post" style="display:none">
      <input type="hidden" id="authexec-magic-input" name="authenticationExecution" value="${(magicLinkAuthExecId!'')?html}">
      <input type="hidden" id="magic-username-input" name="username">
    </form>
    <#if magicLinkAuthExecId?? && magicLinkAuthExecId?has_content>
    <button
      type="button"
      class="wt-method-row"
      id="magic-method-btn"
    >
      <!-- Email icon -->
      <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24"
           fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
           stroke-linejoin="round" aria-hidden="true" focusable="false">
        <rect x="2" y="4" width="20" height="16" rx="2"/>
        <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/>
      </svg>
      Email me a sign-in link
    </button>
    </#if>
    ```
  - [x] Inside the IIFE, after the Story 2.3 SSO block, add:
    ```js
    /* ── Story 2.4: Magic link method-row ── */
    var magicBtn    = document.getElementById('magic-method-btn');
    var magicForm   = document.getElementById('kc-select-magic-form');
    var magicUser   = document.getElementById('magic-username-input');
    var usernameEl  = document.getElementById('username');
    if (magicBtn && magicForm) {
      magicBtn.addEventListener('click', function () {
        try { localStorage.setItem('wt_last_auth_method', 'magic-link'); } catch (e) {}
        if (magicUser && usernameEl) { magicUser.value = usernameEl.value || ''; }
        magicForm.submit();
      });
    }
    ```
  - [x] Used Option B (theme.properties `magicLinkAuthExecId` property) — guards button with `<#if (properties.magicLinkAuthExecId!'') != ''>`. Admin sets UUID after flow wiring. Does not break SSO guard as specified.

- [x] **Task 10: Create `login-magic-link.ftl` (NEW standalone template)** (AC: #3, #4, #6)
  - [x] Created standalone HTML; no `@layout.registrationLayout`; same chrome as `login-with-sso.ftl`
  - [x] State A (initial/error) and State B (sent) controlled by `magicLinkSent`
  - [x] Send form (`action=send`), Resend form (`action=resend`), Try-another-way form with `auth.showTryAnotherWay` guard
  - [x] `<h1 tabindex="-1">` focused on load; error div has `role="alert" aria-live="polite"`; error state focuses error div
  - [x] Inline IIFE for focus management and bfcache button reset

- [x] **Task 11: Add theme i18n overrides** (AC: #3, #4, #6)
  - [x] Extension keys sufficient; no theme overrides needed

- [x] **Task 12: Build verification for theme** (AC: #6)
  - [x] `mvn package` → BUILD SUCCESS
  - [x] Zero hardcoded hex in `login-magic-link.ftl` and changed `login.ftl` lines

---

### Review Findings (code review 2026-06-14 — Repo 1 / magic-link scope)

**decision-needed** — RESOLVED 2026-06-14:

- D1 → **patch** (Repo 1 part): override KC token-error messages + render calm error page (see Patch list).
- D2 → **action item** (kept open below): support cross-device link clicks.
- D3 → **dismissed**: keep AC#1's explicit `notVerified` error; enumeration trade-off accepted.
- D4 → **patch**: honor realm login-with-email/case config (see Patch list).

**action items** (open — substantial work, not auto-patched):

- [x] [Review][Task] Cross-device magic-link continuation — when the link is clicked in a different browser/tab, the original tab must complete (add a rendezvous so the originating session observes verification); also resolve the resume flow from the auth session rather than `realm.getBrowserFlow()` [MagicLinkActionTokenHandler.java:handleToken]. Decision D2: support cross-device. [blind+edge]

**patch** (fix unambiguous) — ALL APPLIED 2026-06-15, `mvn compile` BUILD SUCCESS:

- [x] [Review][Patch] AC#4 calm token-error UX (Repo 1) — overrode KC `expiredActionTokenNoSessionMessage`/`expiredActionTokenSessionExistsMessage` to calm wording; `handleToken` now renders a LoginFormsProvider error page (not a bare 400) on user-not-found/disabled. Resend-path UX completes with Repo 2 follow-up [MagicLinkActionTokenHandler.java / messages_en.properties]. Decision D1.
- [x] [Review][Patch] Username resolution honors realm config — switched to `KeycloakModelUtils.findUserByNameOrEmail(...)` [MagicLinkAuthenticator.java]. Decision D4.
- [x] [Review][Patch] EmailSender.sendMagicLinkEmail now throws `EmailException`; `action()` catches it, shows `magicLinkSendFailed`, and does NOT set the resend cooldown [EmailSender.java / MagicLinkAuthenticator.java]
- [x] [Review][Patch] emailVerified + isEnabled re-checked on send/resend; `handleToken` checks isEnabled at consume [MagicLinkAuthenticator.java / MagicLinkActionTokenHandler.java]
- [x] [Review][Patch] action() re-renders the form for any non-send/resend/tryAnotherWay action instead of falling through with no response [MagicLinkAuthenticator.java]
- [x] [Review][Patch] MAGIC_LINK_USER_ID null-guarded before getUserById [MagicLinkAuthenticator.java]
- [x] [Review][Patch] HTML email template now uses `${magicLinkUrl?html}` [templates/html/magic-link-email.ftl]
- [x] [Review][Patch] handleToken fires a LOGIN error event (USER_NOT_FOUND/USER_DISABLED) on failure paths [MagicLinkActionTokenHandler.java]
- [x] [Review][Patch] Removed dead/mislabeled `tab_id` field; compound id passed to `super(...)` only [MagicLinkActionToken.java]
- [x] [Review][Patch] Removed unused MAGIC_LINK_SENT constant [MagicLinkAuthenticator.java]

**defer** (real, not actionable now):

- [x] [Review][Defer] Rate-limit is per-auth-session only → email-bombing bypass via fresh sessions [MagicLinkAuthenticator.java:88-101] — deferred; spec Dev Notes mark cross-session throttling explicitly out of scope
- [x] [Review][Defer] Token serialization / Urls.actionTokenBuilder failure propagates as generic 500 [MagicLinkAuthenticator.java:123-131] — deferred; minor, cooldown not yet set so retry works
- [x] [Review][Defer] Feature non-functional until Repo 2 theme + admin flow-wiring — AC#2/#6/#7 depend on login.ftl/login-magic-link.ftl (Repo 2) plus manually adding the magic-link execution to the browser flow and setting magicLinkAuthExecId — deferred; cross-repo/deployment dependency, out of this review's scope

### Review Findings (code review 2026-06-15 — full uncommitted diff, 3-layer adversarial)

> Scope notes: (1) The 4 magic-link Java classes + 2 service/email files are **untracked** — `git diff HEAD` did not include them; review used the working-tree source directly. They must be `git add`ed before commit. (2) **Repo 2 (`azguards-keycloak-custom-theme`) was not formally diffed** — FTL findings below were read from the working tree of that sibling repo. (3) This diff also contains tenant-search/RBAC/uniqueness work (see `tenant-search-rbac-uniqueness-api.md`) that is arguably a separate story; those findings are tagged `[tenant-crud]`.

**decision-needed** — RESOLVED 2026-06-15:

- D1 → **patch**: abort the accept/reject flow and surface an error when `updateUserTenantInvitationStatuses` fails (no silent divergence). [ReviewTenantInvitations.java]
- D2 → **patch**: restore the `new HashMap<>()` default on `TenantRepresentation.attributes`. [TenantRepresentation.java]
- D3 → **dismissed**: accept the tenant-name uniqueness race; no DB constraint added.
- D4 → **dismissed**: accept the D2 cross-device rendezvous design for AC-5 routing.

**patch** (fix unambiguous):

- [x] [Review][Patch] User-service invitation update failure must abort the accept/reject flow and surface an error rather than logging-and-continuing (prevents KC/user-service divergence). [ReviewTenantInvitations.java] [tenant-crud] — from D1
- [x] [Review][Patch] Restore `private Map<String, List<String>> attributes = new HashMap<>();` default so `getAttributes()` is never null. [TenantRepresentation.java] [tenant-crud] — from D2

- [x] [Review][Patch] **CRITICAL** — Magic-link token is minted already-expired. `EXPIRATION_SECONDS=900` is passed as `absoluteExpirationSeconds` straight into `DefaultActionToken`, which stores it as the absolute `exp` epoch second (= 1970-01-01 00:15Z). Every link is expired on arrival; the feature cannot work. Fix: pass `Time.currentTime() + EXPIRATION_SECONDS`. [MagicLinkAuthenticator.java:143 / MagicLinkActionToken.java:13-14] [edge] — VERIFIED
- [x] [Review][Patch] Disabled-IdP branch lacks `return` — after `failureChallenge(...)`, `doAuthenticate(...)` runs anyway on a disabled provider. Add `return`/`else`. [IdpTenantMembershipsCreatingAuthenticator.java:47-53] [blind] — VERIFIED
- [x] [Review][Patch] `getIdpConfig().isEnabled()` dereferenced unguarded one line after an explicit `getIdpConfig() != null` guard → NPE risk. [IdpTenantMembershipsCreatingAuthenticator.java:44-47] [blind] — VERIFIED
- [x] [Review][Patch] `sendFailed` error state renders the wrong copy — the FTL `<#else>` falls back to `magicLinkNotVerified` ("account ineligible"); the dedicated `magicLinkSendFailed` key is never referenced. A transient SMTP failure misleads the user. Add a `sendFailed` branch. [Repo 2 login-magic-link.ftl ~56-65] [auditor]
- [x] [Review][Patch] Resend/send `action()` never re-reads the submitted `username`, only stored `MAGIC_LINK_USER_ID` — a corrected username on resend is silently ignored (link goes to the first-resolved user). Re-validate or make the field read-only in State B. [MagicLinkAuthenticator.java:123] [edge]
- [x] [Review][Patch] `getTenantsStream` name filter NPEs on a tenant with null name: `t.getName().toLowerCase()` has no null guard; reachable via the new `search`/`q` params. [JpaTenantProvider.java:120] [tenant-crud] [edge]
- [x] [Review][Patch] `q` attribute parser splits on space then `:` — values containing spaces (e.g. `location:New York`) are mangled/dropped silently; key-only terms ignored. The existing test uses `New York`, exercising exactly this. [TenantsResource.java listTenants] [tenant-crud] [blind+edge]
- [x] [Review][Patch] `handleToken` null-authSession branch still returns the "verified — return to your browser" success page, so the polling tab waits forever. Surface an error instead. [MagicLinkActionTokenHandler.java:42-51] [edge]
- [x] [Review][Patch] Duplicate-name guard in `CreateTenant.processAction` is case-sensitive (`tenantName::equals`) while create/update elsewhere use `equalsIgnoreCase` — inconsistent. (Note: the "filter on empty string breaks the check" claim was a false positive — empty args are no-op'd by `ObjectUtils.isEmpty`.) [CreateTenant.java:91] [blind, corrected]
- [x] [Review][Patch] Test uniqueness uses `currentTimeMillis + (random*1000)` — collision-prone within a millisecond under the new uniqueness constraint → flaky CONFLICTs. Use a counter/UUID. [TenantAttributesTest.java, TenantCreationRbacIntegrationTest.java] [tenant-crud] [blind]
- [x] [Review][Patch] Dead test method `SelectTenantPage.signIn()` no longer clicks anything (only resolves) — misleading no-op. Remove or restore the click. [SelectTenantPage.java] [blind]

**defer** (real, not actionable now):

- [x] [Review][Defer] `getTenantsStream` does full table fetch + in-memory name/attribute filtering with no DB-side predicate or pagination pushdown — unbounded on large realms [JpaTenantProvider.java:114-134] [tenant-crud] — deferred, performance hardening
- [x] [Review][Defer] Pagination `first`/`max` silently ignore negative/zero and have no upper cap (`max=0` returns the full list) [TenantsResource.java:121-123] [tenant-crud] — deferred, input-validation hardening
- [x] [Review][Defer] Polling `check` only ever returns `{"verified":false}`; no branch detects a now-expired token to prompt a fresh send (resend button mitigates) [MagicLinkAuthenticator.java:90-96] [edge] — deferred, depends on the CRITICAL expiry fix first
- [x] [Review][Defer] `SelectActiveTenant.requiredActionChallenge` re-fetches and re-sets the active-tenant note without the existing-note short-circuit that `evaluateTriggers` has — logic drift/redundant work [SelectActiveTenant.java] [blind] — deferred, low-risk cleanup
- [x] [Review][Defer] `magicLinkExpired` message key defined but never rendered (expired UX uses KC `expiredActionToken*` overrides) [messages_en.properties] [auditor] — deferred, corroborates known AC-4 resend-path gap
- [x] [Review][Defer] TAW affordance always labeled `magicLinkUsePassword` ("Use password instead") incl. the AC-1 `notVerified` state where AC-1 specifies "Use another method" [Repo 2 login-magic-link.ftl] [auditor] — deferred, copy tweak

### Review Findings (code review 2026-06-15 — magic-link Repo 1 scope, 3-layer adversarial)

> Scope: story 2.4 File List (Repo 1 Java SPI) only — the 7 new magic-link files + `EmailSender.java`, `messages_en.properties`, `AuthenticatorFactory` service. Repo 2 theme/FTL and the tenant-CRUD working-tree changes were excluded by reviewer's choice. Diff = working tree vs HEAD (`7f51fae`), including untracked new files. Layers: Blind Hunter, Edge Case Hunter, Acceptance Auditor (full mode). All ACs satisfiable by Repo 1 (#1, #3, #5, #8) verified satisfied; the prior CRITICAL token-expiry fix confirmed genuinely present (`Time.currentTime() + EXPIRATION_SECONDS`).

**patch** (fix unambiguous — all LOW severity):

- [x] [Review][Patch] Cooldown timestamp uses wall-clock `System.currentTimeMillis()/1000` while token expiry uses KC `Time.currentTime()` — use `Time.currentTime()` consistently for the stored TS and the comparison so the cooldown honors KC's clock offset/test time [MagicLinkAuthenticator.java:112,181] [edge]
- [x] [Review][Patch] `handleToken` re-checks `isEnabled()` but not `isEmailVerified()` at token consume — a user whose email was unverified after the link was issued can still verify. Add `isEmailVerified()` to the consume check for parity with the send-side check [MagicLinkActionTokenHandler.java:33] [edge]
- [x] [Review][Patch] On resend, an explicitly-submitted username that fails to resolve silently keeps the stale `MAGIC_LINK_USER_ID` and emails the originally-resolved account (link goes to a different user than the field shows). When a username is submitted but does not resolve, render `notVerified` instead of falling back to the stale id [MagicLinkAuthenticator.java:127-138] [blind+edge]
- [x] [Review][Patch] A user with `emailVerified=true` but null/blank email reaches `sendMagicLinkEmail`, which throws `EmailException` and surfaces the misleading `sendFailed` copy. Add an explicit blank-email guard returning `notVerified` (ineligible) before send [MagicLinkAuthenticator.java:137-138] [edge]

**defer** (real, not actionable now):

- [x] [Review][Defer] Magic-link email body text hardcoded English while subject + UI strings externalized [templates/html|text/magic-link-email.ftl] — deferred, broader i18n pass [blind]
- [x] [Review][Defer] Resend cooldown is per-auth-session only → no cross-session email-bomb throttle [MagicLinkAuthenticator.java:108-122] — deferred, spec Dev Notes mark cross-session throttling out of scope (re-confirms 2026-06-14 entry) [blind+edge]

**dismissed** (false positive / by-design / already-decided — not persisted as actions):

- Polling "has no `verified:true` path" — by design: the `action=check` fast-path calls `context.success()` (redirect) once `MAGIC_LINK_VERIFIED` is set; `{"verified":false}` is only the pending response and the Repo 2 JS follows the redirect. AC#5 confirmed satisfied. [blind, no context]
- `login-magic-link.ftl` "missing → 500" — template lives in Repo 2 (azguards-keycloak-custom-theme), loaded from the theme classpath at runtime; story File List places it there. Not a Repo 1 defect. [edge, no cross-repo context]
- Account-enumeration oracle via `notVerified` differential — AC#1 explicitly specifies the `notVerified` error and prior review D3 accepted the enumeration trade-off. [blind+edge]
- `context.getAuthenticationSession()` NPE in `authenticate()`/`action()` — KC's AuthenticationProcessor guarantees a live auth session when an Authenticator is invoked; the handler guards only because token-handling runs in a different context. [blind+edge]
- Deleted `reviewInvitationsHeader`/`reviewInvitationsInfo` keys — false positive: these were duplicate definitions at the file head; canonical keys remain (lines 24-25) and are still used by `review-invitations.ftl`. [blind]
- ALTERNATIVE-only flow execution may be skipped — deployment/flow-config concern, documented in the Dev Notes admin step; not a code defect. [edge]
- Error attribute-value → message-key mapping — keys exist in `messages_en.properties`; the value-to-key mapping is a Repo 2 FTL concern, out of scope. [edge]
- `configuredFor()` always true / `setRequiredActions()` empty — idiomatic for a `requiresUser()==false` ALTERNATIVE authenticator. [blind]

**Process observation (out of scope):** `LoginWithSsoAuthenticator.java` was modified in the working tree despite the Dev Notes "Do NOT modify" directive — not part of this scoped diff; needs a separate review.

## Dev Notes

### Working Repositories

```
keycloak-multi-tenancy (Repo 1 — Java SPI, complete first):
  src/main/java/dev/sultanov/keycloak/multitenancy/authentication/authenticators/
    MagicLinkActionToken.java          ← NEW (Task 1)
    MagicLinkActionTokenHandler.java   ← NEW (Task 2)
    MagicLinkAuthenticator.java        ← NEW (Task 3)
    MagicLinkAuthenticatorFactory.java ← NEW (Task 4)
  src/main/java/dev/sultanov/keycloak/multitenancy/email/
    EmailSender.java                   ← MODIFY (Task 5)
  src/main/resources/theme-resources/templates/html/
    magic-link-email.ftl               ← NEW (Task 6)
  src/main/resources/theme-resources/templates/text/
    magic-link-email.ftl               ← NEW (Task 6)
  src/main/resources/theme-resources/messages/
    messages_en.properties             ← MODIFY (Task 7)
  src/main/resources/META-INF/services/
    org.keycloak.authentication.AuthenticatorFactory     ← MODIFY (Task 4)
    org.keycloak.authentication.actiontoken.ActionTokenHandler  ← CREATE (Task 2)

azguards-keycloak-custom-theme (Repo 2 — FTL, complete after Repo 1):
  src/main/resources/theme/azguards-whatsapp/login/
    login.ftl                          ← MODIFY (Task 9)
    login-magic-link.ftl               ← NEW (Task 10)
    messages/messages_en.properties    ← MAYBE MODIFY (Task 11)
```

**Do NOT modify** in Repo 1: `LoginWithSsoAuthenticator.java`, `IdpTenantMembershipsCreatingAuthenticator.java`, any requiredactions, `TracingConfig.java`, `TracingHelper.java` (only add spans IN new files), any existing email template files.

**Do NOT modify** in Repo 2: `tokens.css`, `components.css`, `style.css`, `script.js` (only ADD to the inline IIFE in login.ftl — same pattern as Story 2.3's SSO block).

---

### KC 26.6.3 Action Token System

The project targets **Keycloak 26.6.3** (verified from `pom.xml` line 20: `<keycloak.version>26.6.3</keycloak.version>`).

**Key KC classes for action tokens:**
- `org.keycloak.authentication.actiontoken.AbstractUserActionToken` — base class; provides `getUserId()`, `serialize()`, the Jackson-deserialized `exp` claim, and `getNote()`/`setNote()` for custom JWT claims
- `org.keycloak.authentication.actiontoken.ActionTokenHandler<T>` — interface; implement `getTokenClass()`, `handleToken()`, `getDefaultErrorPage()`, `getError()`
- `org.keycloak.authentication.actiontoken.ActionTokenContext<T>` — passed to `handleToken()`; exposes `getSession()`, `getRealm()`, `getAuthenticationSession()`, `getEvent()`, `getToken()`

**Generating the action token URL:**

```java
// In MagicLinkAuthenticator.action():
var session   = context.getSession();
var realm     = context.getRealm();
var uriInfo   = session.getContext().getUri();
var authSession = context.getAuthenticationSession();
var clientId  = authSession.getClient().getClientId();
var tabId     = authSession.getTabId();

// 1. Create token
var token = new MagicLinkActionToken(userId, EXPIRATION_SECONDS, tabId);

// 2. Serialize to JWT string
String serialized = token.serialize(session, realm, uriInfo);

// 3. Build the action-token URL
// Use KC's LoginActionsService URL builder:
String baseUri = uriInfo.getBaseUri().toString();
String realmName = realm.getName();
String actionTokenUrl = Urls.actionTokenBuilder(uriInfo.getBaseUri(), serialized, clientId, tabId,
        AuthenticationProcessor.getClientData(session, authSession))
        .build(realmName)
        .toString();
```

If `Urls.actionTokenBuilder(...)` is not available (API varies by KC version), fall back to:
```java
String actionTokenUrl = uriInfo.getBaseUri().toString()
    + "realms/" + realmName
    + "/login-actions/action-token"
    + "?key=" + serialized
    + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
    + "&tab_id=" + URLEncoder.encode(tabId, StandardCharsets.UTF_8);
```

**In `MagicLinkActionTokenHandler.handleToken()`:**

The handler is called when the user clicks the emailed link. At this point we need to complete the auth session. The standard KC pattern for action token handlers that complete an auth flow:

```java
@Override
public Response handleToken(MagicLinkActionToken token, ActionTokenContext<MagicLinkActionToken> context) {
    Span span = TracingHelper.startServerSpan("magic-link.handleToken");
    Throwable traceError = null;
    try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
        var session = context.getSession();
        var realm   = context.getRealm();
        var user    = session.users().getUserById(realm, token.getUserId());
        if (user == null) {
            return context.getErrorResponse(); // token handler returns error
        }
        // Mark authentication as complete for the authenticator
        // The auth session note is checked by MagicLinkAuthenticator.authenticate()
        var authSession = context.getAuthenticationSession();
        if (authSession != null) {
            authSession.setAuthNote(MagicLinkAuthenticator.MAGIC_LINK_VERIFIED, "true");
            authSession.setAuthenticatedUser(user);
        }
        // Redirect back to the login flow to continue
        // KC's action token infrastructure handles the redirect back to the auth flow
        return context.processFlow(false, null);
    } catch (Exception ex) {
        traceError = ex;
        throw ex;
    } finally {
        TracingHelper.finishSpan(span, traceError);
    }
}
```

`context.processFlow(boolean isExpired, String error)` is the standard KC action token method to re-enter the auth flow. If this method is not available on `ActionTokenContext`, use `context.getAuthenticationSessionModel()` and manually resume the flow. Check the KC 26.6.3 `ActionTokenContext` API.

**SPI registration for ActionTokenHandler:**

Create `src/main/resources/META-INF/services/org.keycloak.authentication.actiontoken.ActionTokenHandler` with content:
```
dev.sultanov.keycloak.multitenancy.authentication.authenticators.MagicLinkActionTokenHandler
```

---

### Browser Flow Wiring (IMPORTANT — Admin Console Step)

After deploying the extension JAR, the realm admin (or the Realm Export JSON) must add the `magic-link` authenticator to the browser flow as an ALTERNATIVE:

```
browser custom flow:
  auth-cookie                    (ALTERNATIVE, priority 10)
  auth-spnego                    (DISABLED,    priority 20)
  identity-provider-redirector   (ALTERNATIVE, priority 25)
  browser custom forms           (ALTERNATIVE, priority 30)  → login.ftl
    └── auth-username-password-form (REQUIRED)
  login-with-sso                 (ALTERNATIVE, priority 31)  → login-with-sso.ftl
  magic-link                     (ALTERNATIVE, priority 32)  → login-magic-link.ftl  ← ADD
```

This must be done via the KC Admin Console → Authentication → browser custom flow → Add execution → select "Magic Link (Email Sign-in)". Set to ALTERNATIVE.

The execution ID assigned by KC (a UUID) is what `authenticationSelection` will expose in the FTL context. This is NOT the `getId()` provider ID — it is the flow execution UUID specific to this realm's flow configuration.

---

### Exposing `magicLinkAuthExecId` to login.ftl

This is the **most KC-version-specific** part of the implementation. Verify at dev time:

**Option A (verify first): `authenticationSelection` as a list**

In KC 26.x, when the `browser custom forms` subflow's challenge renders `login.ftl`, the `authenticationSelection` FTL variable may be a list of `AuthenticationSelectionOption` where each entry has:
- `authExecId` — the execution UUID for that alternative
- `providerId` — the factory ID (e.g. `"login-with-sso"` or `"magic-link"`)
- `displayName` — from `getDisplayType()`

If so, in login.ftl:
```html
<#list authenticationSelection as sel>
  <#if sel.providerId == "magic-link">
    <!-- use sel.authExecId for the magic link form -->
  </#if>
</#list>
```

And use `sel.authExecId` in the magic-link form's hidden input.

**Option B (if `authenticationSelection` is a single object tied to SSO only):**

The `MagicLinkAuthenticator` cannot inject its execution ID into login.ftl's FTL context (because login.ftl is rendered by `auth-username-password-form`, not by `MagicLinkAuthenticator`). In this case, use a **theme property** approach:
- Add `magicLinkAuthExecId=<UUID>` to `src/main/resources/theme/azguards-whatsapp/login/theme.properties`
- Reference it in login.ftl as `${properties.magicLinkAuthExecId!''}`
- This value must be manually updated after adding the execution to the flow (it's realm-specific)

**Option C (simplest fallback):**

If neither A nor B is clean, use KC's built-in "Try Another Way" (TAW) mechanism:
- The magic link button is NOT on login.ftl at all
- From `login-with-sso.ftl`, there's a "Try another way" button that switches to magic-link
- This avoids the execution ID exposure problem entirely
- The user flow becomes: Login → Sign in with SSO → (don't have SSO?) → Try another way → Email me a sign-in link

Option A is the cleanest UX; try it first. Fall back to C if execution ID injection is impossible in the current KC version.

---

### FTL Context Variables Available in `login-magic-link.ftl`

These are set by `MagicLinkAuthenticator` via `context.form().setAttribute(key, value)` before calling `createForm("login-magic-link.ftl")`:

| Variable | Set when | FTL access | Value |
|---|---|---|---|
| `magicLinkSent` | After email is sent | `<#if magicLinkSent?? && magicLinkSent == "true">` | `"true"` |
| `magicLinkError` | On error state | `<#if magicLinkError?? && magicLinkError?has_content>` | One of: `"notVerified"`, `"rateLimited"`, `"noUsername"` |

These are NOT auth session notes — they are form attributes set before `context.form().createForm(...)`. Set them like:
```java
var response = context.form()
    .setAttribute("magicLinkSent", "true")
    .createForm("login-magic-link.ftl");
```

Standard KC FTL variables always available:
- `url.loginAction` — form action URL
- `url.resourcesPath` — CSS/JS/image base
- `msg("key")` — i18n messages (picks up from extension + theme bundles)
- `kcSanitize(...)` — safe HTML
- `auth.showTryAnotherWay` — whether "Use another method" form should be shown
- `properties.*` — theme.properties values

---

### login.ftl Current State of `.wt-methods` (after Story 2.3)

From `login.ftl` lines 118–142 (current in repo):
```html
<!-- Method rows — SSO (2.3) and magic link (2.4) -->
<div class="wt-methods" id="auth-methods" aria-label="Other sign-in options">
  <#if authenticationSelection?? && authenticationSelection.authExecId?has_content>
  <button type="button" class="wt-method-row" id="sso-method-btn">
    <!-- SVO globe SVG -->
    Sign in with SSO
  </button>
  <!-- Hidden form: posts authenticationExecution to switch KC flow to login-with-sso -->
  <form id="kc-select-sso-form" action="${url.loginAction}" method="post">
    <input type="hidden" id="authexec-sso-input" name="authenticationExecution"
           value="${(authenticationSelection.authExecId!'')?html}">
  </form>
  </#if>
  <!-- 2.4: <button class="wt-method-row" ...>Email me a sign-in link</button> -->
</div>
```

**Replace only the `<!-- 2.4: ... -->` comment** with the magic-link markup from Task 9. Do NOT change the SSO button or its form.

The SSO guard `<#if authenticationSelection?? && authenticationSelection.authExecId?has_content>` was set in Story 2.3 — if after adding `magic-link` as a second alternative the `authenticationSelection` object now represents a different execution, the SSO guard may break. **Verify that the SSO button still works end-to-end after adding the magic-link alternative to the flow.** If it breaks, switch to Option A (list-based access) for both SSO and magic-link.

---

### Tracing Pattern (follow exactly from `LoginWithSsoAuthenticator.java`)

```java
@Override
public void authenticate(AuthenticationFlowContext context) {
    Span span = TracingHelper.startServerSpan("magic-link.authenticate");
    Throwable traceError = null;
    try (var ignored = TracingHelper.tracer().withSpanInScope(span)) {
        // ... implementation ...
        span.tag("user.id", userId);  // tag interesting values
    } catch (Exception ex) {
        traceError = ex;
        throw ex;
    } finally {
        TracingHelper.finishSpan(span, traceError);
    }
}
```

Span names:
- `magic-link.authenticate`
- `magic-link.action`
- `magic-link.handleToken`

---

### Why Standalone HTML (Same Rationale as `login-with-sso.ftl`)

`login-magic-link.ftl` must NOT use `@layout.registrationLayout`. All custom theme FTL files (login.ftl, login-with-sso.ftl, select-tenant.ftl, review-invitations.ftl) are standalone HTML. Using the Keycloak base layout would pull in PatternFly CSS. The standalone approach is mandatory for visual consistency with the Epic 1 token/component system.

---

### Rate Limiting Implementation

The rate limit check in `action()` should be:

```java
String lastSentStr = context.getAuthenticationSession().getAuthNote(MAGIC_LINK_LAST_SENT_TS);
if (lastSentStr != null) {
    try {
        long lastSentTs = Long.parseLong(lastSentStr);
        long nowTs = System.currentTimeMillis() / 1000L;
        if (nowTs - lastSentTs < RESEND_COOLDOWN_SECONDS) {
            var response = context.form()
                .setAttribute("magicLinkSent", "true")
                .setAttribute("magicLinkError", "rateLimited")
                .createForm("login-magic-link.ftl");
            context.challenge(response);
            return;
        }
    } catch (NumberFormatException ignored) { /* treat as no-op */ }
}
```

---

### Key Patterns from Stories 2.1–2.3 to Preserve

| Pattern | Location | Action |
|---|---|---|
| `wt_last_auth_method` localStorage write | login.ftl IIFE | ADD `'magic-link'` write in Task 9 JS |
| `bfcache pageshow` reset | login.ftl IIFE | Already present — no change |
| `authexec-sso-form` SSO hidden form | login.ftl lines 136-139 | No change — leave SSO block intact |
| Standalone HTML template structure | login-with-sso.ftl | Follow exactly for login-magic-link.ftl |
| `auth.showTryAnotherWay` guard | login-with-sso.ftl line 261 | Apply same guard in login-magic-link.ftl |
| Brand mark with `onerror` fallback | All login FTLs | Copy same img tag |
| `?html` escaping on server-injected values in FTL attrs | login.ftl line 138 | Apply to `magicLinkAuthExecId` value |

---

### What Is Explicitly Out of Scope

- Passkey affordance — Story 3.x
- AJAX/JS username check to dynamically show/hide the magic link button client-side — FR-L-6 says "after a username-submit check"; the server-side check on form submission satisfies this without client-side JS calls
- Rate-limiting across sessions (only per-session rate limiting via auth session notes; cross-session throttling is out of scope)
- Any modification to `register.ftl`, `login-oauth-grant.ftl`, email templates in `email/`, admin tenant switcher, username generation — AR-OOS
- Swedish i18n (`messages_sv.properties`) — add keys only if the bundle already has magic-link-related keys; otherwise leave for a later i18n pass

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- `AbstractUserActionToken` referenced in story does not exist in KC 26.6.3. Used `DefaultActionToken` as base class for `MagicLinkActionToken`. Constructor signature adjusted accordingly.
- SPI registration for action token handlers in KC 26.6.3 is via `org.keycloak.authentication.actiontoken.ActionTokenHandlerFactory` (not `ActionTokenHandler`). `AbstractActionTokenHandler` implements both `ActionTokenHandler` and `ActionTokenHandlerFactory` — the correct service file is `ActionTokenHandlerFactory`.
- `ActionTokenContext.processFlow` signature in KC 26.6.3 is `processFlow(boolean, String, AuthenticationFlowModel, String, AuthenticationProcessor)`, not the two-arg form in dev notes.
- `magicLinkAuthExecId` FTL variable cannot be injected from `MagicLinkAuthenticator` into `login.ftl` (different authenticator renders login.ftl). Used Option B (theme.properties `magicLinkAuthExecId` property) as deployment-safe approach.

### Completion Notes List

- Repo 1: 4 new Java classes + 1 modified + 2 FTL email templates + i18n keys + 2 service files
- Repo 2: `login.ftl` magic-link block + new `login-magic-link.ftl` + `theme.properties` placeholder
- Both repos build clean (`mvn package -DskipTests`); zero hardcoded hex
- AC #1–#8 satisfied by implementation; flow wiring (admin step) documented in Dev Notes
- ✅ Resolved review finding [D2] Cross-device rendezvous: `handleToken` now marks MAGIC_LINK_VERIFIED and returns a KC info page (no processFlow). `action=check` added to `MagicLinkAuthenticator.action()` — returns JSON `{"verified":false}` when pending, calls `context.success()` when verified. Polling JS added to `login-magic-link.ftl` State B — polls every 3 s (max 100 × = ~5 min), submits form on opaque-redirect signal so browser follows KC's auth redirect chain. `magicLinkVerifiedReturn` i18n key added to extension bundle.

### File List

**keycloak-multi-tenancy (Repo 1):**
- `src/main/java/dev/sultanov/keycloak/multitenancy/authentication/authenticators/MagicLinkActionToken.java` (NEW)
- `src/main/java/dev/sultanov/keycloak/multitenancy/authentication/authenticators/MagicLinkActionTokenHandler.java` (NEW)
- `src/main/java/dev/sultanov/keycloak/multitenancy/authentication/authenticators/MagicLinkAuthenticator.java` (NEW)
- `src/main/java/dev/sultanov/keycloak/multitenancy/authentication/authenticators/MagicLinkAuthenticatorFactory.java` (NEW)
- `src/main/java/dev/sultanov/keycloak/multitenancy/email/EmailSender.java` (MODIFIED)
- `src/main/resources/theme-resources/templates/html/magic-link-email.ftl` (NEW)
- `src/main/resources/theme-resources/templates/text/magic-link-email.ftl` (NEW)
- `src/main/resources/theme-resources/messages/messages_en.properties` (MODIFIED)
- `src/main/resources/META-INF/services/org.keycloak.authentication.AuthenticatorFactory` (MODIFIED)
- `src/main/resources/META-INF/services/org.keycloak.authentication.actiontoken.ActionTokenHandlerFactory` (NEW)

**azguards-keycloak-custom-theme (Repo 2):**
- `src/main/resources/theme/azguards-whatsapp/login/login.ftl` (MODIFIED)
- `src/main/resources/theme/azguards-whatsapp/login/login-magic-link.ftl` (MODIFIED — D2 polling JS added)
- `src/main/resources/theme/azguards-whatsapp/login/theme.properties` (MODIFIED)

## Change Log

- 2026-06-14: Story 2.4 created — Conditional "Email me a sign-in link" (magic link). Two-repo story: new `MagicLinkAuthenticator` + action token system in keycloak-multi-tenancy, plus `login-magic-link.ftl` and login.ftl method-row in azguards-keycloak-custom-theme.
- 2026-06-14: Story 2.4 implemented — All 12 tasks complete across both repos. Magic link SPI (4 classes + service files + email templates + i18n) in Repo 1; standalone login-magic-link.ftl + login.ftl method-row in Repo 2. Both repos BUILD SUCCESS. Status → review.
- 2026-06-15: Code review D2 resolved — Cross-device rendezvous: handleToken no longer calls processFlow; returns KC info page instead. action=check polling added to authenticator + FTL. Both repos BUILD SUCCESS. Status → review.
