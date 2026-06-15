# Deferred Work

## Deferred from: code review of story-3.2 (2026-06-15)

- **Empty `webAuthnPolicyRpId` may break passkey validation across tenant hostnames** [`src/test/resources/realm-export.json:392`] — `webAuthnPolicyRpId` is `""`, so Keycloak defaults the RP ID to the request host. In a multi-tenant deployment served under multiple hostnames, a passkey registered while on one host's effective RP ID will fail the assertion (`navigator.credentials.get`) when the user later authenticates from a different hostname. Pre-existing realm config — not introduced by Story 3.2's flow-wiring/i18n change — but directly affects whether passkey-first auth works across tenants. Revisit when the deployment hostname strategy is finalized (set RP ID to the registrable parent domain).

## Deferred from: code review of story-3.1 (2026-06-15)

- **Dirty working tree — unrelated Story 2.4 / tenant-CRUD changes pollute the story diff** — Repo 1 (`keycloak-multi-tenancy`) carries many uncommitted, unrelated modified files (Story 2.4 magic-link + tenant-CRUD Java/tests) that were not stashed/committed before Story 3.1 started, contradicting the Epic 2 retro S1 lesson. Story 3.1's own touched files are correct, but the unrelated changes should be separated/committed before merge so the story diff is clean.
- **External logo dependency with no SRI in a security-critical auth flow** [`azguards-keycloak-custom-theme/.../login/webauthn-register.ftl:19-23`] — the brand mark loads from `https://www.whatatalk.com/...` with a local `onerror` fallback. Copied verbatim from the existing `login.ftl` pattern (consistent project-wide), so deferred as a pre-existing convention; revisit if self-hosting the primary logo / adding SRI becomes a broader hardening task.
- **Unsupported-browser / insecure-context dead-end** [`azguards-keycloak-custom-theme/.../login/webauthn-register.ftl:134, 152-153`] — no `window.PublicKeyCredential` feature-detect; on an unsupported browser or non-secure context `navigator.credentials.create` throws a synchronous `TypeError` outside the promise chain, leaving the primary button disabled and the status region stuck on "in progress…" with no fallback. Deferred by decision (2026-06-15): Story 3.3 (graceful passkey fallback / degradation) owns the comprehensive fix.

## Deferred from: code review of story-2.4 (2026-06-15, magic-link Repo 1 scope)

- **Magic-link email body text is hardcoded English** [`templates/html/magic-link-email.ftl`, `templates/text/magic-link-email.ftl`] — the email subject (`magicLinkEmailSubject`) and all UI strings are externalized in `messages_en.properties`, but the email body copy ("Use the link below to sign in…", brand wording) is hardcoded English in the templates. Inconsistent localization. Deferred to a broader i18n pass.
- **Resend cooldown is per-auth-session only → no cross-session throttle** [`MagicLinkAuthenticator.java:108-122`] — re-confirmed this round. `MAGIC_LINK_LAST_SENT_TS` lives in the auth session; a fresh login flow resets the 60s cooldown, so email-bombing a known verified address is not prevented. Deferred: spec Dev Notes mark cross-session throttling explicitly out of scope; proper fix needs a SingleUseObjectProvider / brute-force store keyed by user/email. (Duplicate of the 2026-06-14 entry; retained for traceability.)

## Deferred from: code review of story-2.4 (2026-06-14)

- **Rate-limit is per-auth-session only → email-bombing bypass** [`MagicLinkAuthenticator.java:88-101`] — the 60s resend cooldown is stored as an auth-session note; starting a fresh login flow resets it, allowing unbounded magic-link emails to a known verified address. No per-user/IP server-side throttle. Deferred: spec Dev Notes mark cross-session throttling explicitly out of scope; proper fix needs a SingleUseObjectProvider / brute-force store keyed by user/email.
- **Token serialization / Urls.actionTokenBuilder failure → generic 500** [`MagicLinkAuthenticator.java:123-131`] — if `token.serialize(...)` or the URL builder throws, the exception propagates to the AuthenticationProcessor as an internal server error instead of a handled "please try again" form. Minor; cooldown timestamp isn't set yet so retry isn't blocked.
- **Feature non-functional until Repo 2 theme + admin flow-wiring** [cross-repo] — AC#2/#6/#7 depend on `login.ftl` method-row + `login-magic-link.ftl` (azguards-keycloak-custom-theme, Repo 2), plus a manual admin step to add the `magic-link` execution to the browser flow as ALTERNATIVE and set the realm-specific `magicLinkAuthExecId` theme property. Repo 1 alone cannot activate the feature. Deployment/cross-repo dependency.

## Deferred from: code review of story-2.3 (2026-06-14)

- **No safety net if the SSO navigation stalls** [`login-with-sso.ftl:96-105`] — on a valid submit the form is hidden and the submit button disabled; if `ssoForm.submit()` never completes (network stall) and the return is not a bfcache restore, the user is left with a hidden form and no recovery control. Minor edge robustness — primary outcomes (IdP redirect / server error re-render) reset the DOM, so deferred.

## Deferred from: code review of story-2.2 (2026-06-14)

- **Decorative ⚠ icon exposed to assistive tech** [`style.css:127`] — `speak: never` is an obsolete no-op; the `.wt-login-error::before` glyph may be announced by screen readers. Needs a real element with `aria-hidden` to reliably silence. Spec-prescribed CSS.
- **Lockout message double-announced** [`login.ftl:104,189`] — `role="alert"` auto-announces on DOM insert and the focus IIFE also `.focus()`es the element, causing screen readers to read the lockout banner twice. Minor a11y.
- **`info`/`success` message types not rendered** [`login.ftl:29`] — `showError` only handles error/warning; info/success messages (logout confirmations, password-reset success) never display on the login page. Pre-existing (old template only showed `error`), out of story 2.2 scope.
- **No fallback focus when `wtA11y` is undefined** [`login.ftl:192`] — if `script.js` fails to load, the credentials-error branch moves no focus (only the locked branch is self-contained). Minor graceful-degradation gap.
- **Inconsistent error-summary contract** [`login.ftl` + `script.js:332`] — `focusFirstError` targets `.wt-form__error-summary` but login uses `.wt-login-error`; works via the `[aria-invalid]` fallback for credentials errors but is a maintainer trap. Naming inconsistency only.
- **bfcache restore doesn't re-run error focus** [`login.ftl:185`] — the parse-time focus IIFE doesn't re-fire on `pageshow`/back-navigation, so a restored locked page won't re-focus the banner. Minor edge case.

## Deferred from: code review of story-1.2 (2026-06-12)

- **Tokenization flattened decorative backgrounds + removed the mobile brand gradient** [`selectTenant.css:250`, `style.css:77`, `reviewTenant.css`] — the two-stop teal→green mobile header gradient is now flat `--wt-bg`, and overlay/band/card backgrounds all collapsed to the same `--wt-bg`, flattening layered depth. **Reason for deferral: needs surface-tint tokens** — restoring depth requires new surface-tint tokens not yet in the design system; belongs with Story 1.3 component styles.
- **Focus ring hard-codes `rgba(15,118,110,.25)` instead of `--wt-focus-ring`** [`style.css:814`] — dark-mode focus ring stays teal instead of the intended brighter `#2DD4BF`; `--wt-focus-ring` goes unused. Focus-style consumption is Story 1.3+ scope; AC#3 only bans hex literals (rgba passes). Needs a color-mix or rgba channel token.
- **Card/overlay `box-shadow` values use literal `rgba(0,0,0,…)` not `--wt-shadow-card`** — shadows are nearly invisible in dark mode; the dark `--wt-shadow-card` variant is defined but unused. Shadow consumption is not in this token-definition story's scope.
- **`font-family: var(--wt-font-family)` has no fallback** — if `tokens.css` fails to load, text falls back to browser-default serif (previously `Arial`). Low risk (tokens.css now linked on every page); add `, sans-serif` fallback opportunistically.
- **No `[data-theme="light"]` escape hatch** — a user on OS dark mode cannot be forced back to light via `data-theme`, since only `@media` + `[data-theme="dark"]` blocks exist. No AC requires forced-light override; future theming-toggle enhancement.

## Deferred from: code review of story-1.3 (2026-06-12)

- **Jenkinsfile: CodeArtifact auth token in workspace `settings.xml`** [`Jenkinsfile`] — token written plaintext, removed only in `post.always`; exposed for the full `mvn deploy` and leaks on hard-abort or workspace archival. Out of CSS-story scope — handle in a separate CI/security PR.
- **Jenkinsfile: AWS CLI `${HOME}/.local` PATH prepend vs conditional install** [`Jenkinsfile`] — non-deterministic across cold vs cached/containerized agents; `aws --version` can fail when HOME differs between Groovy and shell contexts. Out of scope.
- **Bootstrap utility classes not token-aware in dark mode** (`bg-light`, `alert-*`, `text-muted`, `btn-outline-secondary`) — wrong colors under `prefers-color-scheme: dark`. Bootstrap cleanup is Story 1.6 scope.
- **Password-strength label uses Bootstrap `text-success`/`text-danger`** [`login-update-password.ftl`] — not token-aware; diverges from palette in dark mode. Update-password polish.
- **`--wt-info` equals `--wt-primary` (#0F766E)** [`tokens.css`] — strength "strong" indicator is visually indistinct from the brand primary; the semantic weak→fair→good→strong ladder is degraded. Token-palette review.
- **`updatePassword.css` generic class names** (`.requirement`, `.strength-fill`, `.validation-error`) vs `.wt-` convention — cross-page style-bleed risk if loaded globally. Namespacing follow-up.
- **`--wt-danger-border` used inconsistently** [`components.css`/`updatePassword.css`] — ignored by `.wt-field--error` (uses `--wt-danger`), used by `.validation-error`; token is partly dead. Token-consistency cleanup.
- **`.wt-field__error::before { content: '⚠' }`** [`components.css`] — pseudo-element glyph as the sole error affordance: renders as color emoji on some platforms, tofu on minimal font stacks, and is unreliably exposed to assistive tech. Component polish (class not yet used by templates).
- **login-reset-password.ftl drops font-awesome** [`login-reset-password.ftl`] — sibling password screens keep an icon font; inconsistent icon availability across the three password screens.
- **`.wt-btn--submitting` mixes `display:block` + `inline-flex` with `width:100%`** [`components.css`] — inline-level box forced full width; behaves inconsistently across layout contexts. Prefer `display:flex`. Low risk.
- **Literal `rgba(0,0,0,…)` box-shadows remain** in `reviewTenant.css`/`selectTenant.css` — against Task-6 zero-hardcoded-color intent; mostly pre-1.3 work and structural shadows. Revisit during a tokenization sweep.

## Deferred from: code review of 1-5-accessibility-utilities-focus-management.md (2026-06-12)

- **Missing `'use strict'` on `wtToastInit` IIFE** [`script.js:281`] — deferred, pre-existing from Story 1.4.
- **AC #5 axe-core scan not executed** — spec-acknowledged limitation; no automated test infrastructure yet (Story 1.6 scope).

## Deferred from: code review of 1-6-production-hygiene-observability-test-harness-baseline.md (2026-06-13)

- **`failureChallenge` then fall-through to `doAuthenticate`→`success` on disabled IdP** [`IdpTenantMembershipsCreatingAuthenticator.java:44-47`] — missing `return`/`else` on the disabled-IdP path: the flow both fails-challenges and succeeds on the same context (undefined auth outcome). Pre-existing, preserved verbatim through the tracing refactor.
- **User-service status update failure swallowed, then local membership state mutated anyway** [`ReviewTenantInvitations.java:165-171`] — if the user-service REST call throws, the exception is only logged and execution continues to grant memberships / set ACTIVE / revoke invitations locally. Keycloak and the user-service diverge with no compensation. Pre-existing structure.
- **`performLogin` dereferences `getProviderFactory(...)` result without null check** [`LoginWithSsoAuthenticator.java`] — NPE if the provider id is not registered. Pre-existing.
- **In-memory filtering after full-table fetch in `getTenantsStream`** [`JpaTenantProvider.java`] — `nameOrIdQuery`/attribute predicates applied as Java stream filters after materializing the whole tenant table; `first`/`max` pagination then runs over the full result set (no SQL pushdown). Scalability concern; tied to the scope-creep feature decision — needs predicate pushdown rework.

## Deferred from: code review of 1-6-production-hygiene-observability-test-harness-baseline.md (2026-06-13, 2nd pass)

- **Span creation outside try/catch in all instrumented methods** [all 5 tracing classes] — `Span span = TracingHelper.startServerSpan(...)` is the first statement, outside the try block; if tracing-backend init throws it propagates into the login/required-action flow. Spec-dictated canonical pattern (matches `TenantResource`/`SwitchActiveTenant`). Proper fix is to harden `TracingHelper.startServerSpan` to be fail-safe (return a no-op span on backend failure) — systemic, not specific to this story.
- **`PageResolver.resolve` non-waiting visibility checks** [`PageResolver.java`] — waits only for an `h1`, then runs instantaneous `.first().isVisible()` per branch; under CI load can fall through to "Unexpected page" before the target heading paints. Flake risk; 39/39 pass locally.
- **`SelectTenantPage.signIn()` vestigial no-op + brittle `availableOptions()` selector** [`SelectTenantPage.java:14-28`] — `select()` now performs the actual submit via the per-card "Select" button; `signIn()` only re-resolves the page (name no longer matches behavior). `availableOptions()` reads a presentation-coupled `.tenant-info p strong` path. Test-only clarity cleanup.
- **`ReviewInvitationsPage` multi-accept has no inter-click wait** [`ReviewInvitationsPage.java`] — `acceptInvitation/rejectInvitation` click per-card with no wait for card state between clicks; chained multi-accept assumes the card list stays stable across re-render. Flake risk.
- **`doAuthenticate` dereferences `getIdpConfig()` unguarded** [`IdpTenantMembershipsCreatingAuthenticator.java:113-121`] — span-tag block null-guards `brokerContext.getIdpConfig()`, then the body dereferences it unguarded (NPE shape if config is null). Pre-existing; the new guard signals a nullability the body ignores.

## Deferred from: code review of story 2-1-refactored-login-screen (2026-06-13)

- **Jenkinsfile: unpinned AWS CLI download, no checksum/signature verification, `curl` without `-f`** [Jenkinsfile `Setup AWS CLI` stage] — `curl -s https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip | unzip | install` runs unpinned, unverified code in a CI runner holding AWS keys + CodeArtifact token. Use `curl -fsSL`, pin a version, verify the AWS GPG signature. CI hardening, out of this UI story's scope.
- **Bootstrap + Font Awesome CDN still loaded on reset/update/select/review templates** [login-reset-password.ftl, login-update-password.ftl, select-tenant.ftl, review-invitations.ftl] — these retain `cdn.jsdelivr.net` / `cdnjs.cloudflare.com` links alongside the new token CSS. Offline/CDN-down breaks their icons & layout, and only login.ftl was fully migrated. Full migration deferred to Epics 4/5.
- **Global `prefers-reduced-motion` universal `!important` reset** [style.css] — `*, *::before, *::after { animation-duration:.01ms !important; transition-duration:.01ms !important; }` applies site-wide to every themed page, neutralizing intended spinners/transitions (e.g. `.wt-btn__spinner`, tenant-card hover, error-container reveal). Accepted a11y pattern but broad; revisit scoping.
- **`togglePassword` inline onclick lacks null guards** [login-update-password.ftl] — the inline `onclick="togglePassword(...)"` path is independent of the DOMContentLoaded bail-out added in this diff; if markup renders the button without the matching input/icon ids it throws. Pre-existing, low risk.

## Deferred from: code review of 2-4-conditional-email-me-a-sign-in-link-magic-link (2026-06-15)

- **`getTenantsStream` full-table fetch + in-memory filtering** [JpaTenantProvider.java:114-134] — name/attribute filtering happens in application memory with no DB predicate or pagination pushdown; unbounded on large realms. Performance hardening.
- **Pagination bounds not validated** [TenantsResource.java:121-123] — `first`/`max` silently ignore negative/zero and have no upper cap; `max=0` returns the full list. Input-validation hardening.
- **Magic-link poll has no expiry signal** [MagicLinkAuthenticator.java:90-96] — `check` only ever returns `{"verified":false}`; no branch detects a now-expired token to prompt a fresh send. Depends on fixing the CRITICAL token-expiry bug first; resend button mitigates.
- **`SelectActiveTenant.requiredActionChallenge` logic drift** [SelectActiveTenant.java] — re-fetches and re-sets the active-tenant session note without the existing-note short-circuit that `evaluateTriggers` uses. Low-risk cleanup.
- **`magicLinkExpired` dead message key** [messages_en.properties] — defined but never rendered (expired UX uses KC `expiredActionToken*` overrides). Corroborates the known AC-4 resend-path gap.
- **TAW label wording** [Repo 2 login-magic-link.ftl] — always "Use password instead", including the AC-1 `notVerified` state where AC-1 specifies "Use another method". Copy tweak.

## Deferred from: code review of story-3.1 (2026-06-15)

- **No WebAuthn feature detection → silent dead-end** — `webauthn-register.ftl:146` — `navigator.credentials.create` throws a synchronous `TypeError` (bypasses `.catch`) on browsers without WebAuthn or in an insecure (plain-HTTP) context; the register button stays disabled and the status region stuck with no recovery. Deferred to Story 3.3 (graceful passkey fallback/degradation). Re-confirms the prior 2026-06-15 deferral.
- **Unhandled WebAuthn rejection types surfaced as opaque strings** — `webauthn-register.ftl:162-168` — only `NotAllowedError`/`AbortError` are handled; `InvalidStateError` (passkey already registered on this authenticator → confusing, can produce an infinite retry loop), `SecurityError` (rpId/origin mismatch), and `NotSupportedError` fall through to a generic retry with a raw `err.toString()`. Map known error names to friendly messages. Deferred to Story 3.3 graceful degradation.
- **`NotAllowedError` cancel→skip routing also catches timeouts** — `webauthn-register.ftl:162-167` — Patch 6 routes any `NotAllowedError`/`AbortError` to the silent `cancel-aia` skip; WebAuthn also throws `NotAllowedError` on ceremony timeout/platform failure, so a timed-out user is silently treated as "Not now" instead of offered retry. Spec makes cancel vs timeout indistinguishable client-side → no reliable split. Deferred to Story 3.3 graceful degradation. (Decision 2026-06-15)

## Deferred from: code review of story-3.1 (2026-06-15)

- **No WebAuthn feature/secure-context detection** (`webauthn-register.ftl:165`) — on a browser without WebAuthn or in an insecure (plain-HTTP) context, `navigator.credentials` is undefined and `.create` throws synchronously (bypassing `.catch`), leaving the button disabled and the status region stuck with no error. Owned by Story 3.3 (graceful passkey fallback/degradation).
- **Malformed server-rendered base64url → infinite retry loop in non-AIA flow** (`webauthn-register.ftl:153-157`) — corrupt `challenge`/`userid`/excluded-id makes decode throw; KC re-renders identical bad values, so retry re-fails with no skip path when `isAppInitiatedAction` is false. Requires a server-side bug to trigger; non-AIA is not this story's trigger path.
- **`authenticatorLabel` hardwired empty** (`webauthn-register.ftl:173`) — no "name your passkey" UI; KC auto-generates a default label so users cannot distinguish multiple passkeys. Minor UX enhancement, outside Story 3.1 scope.
