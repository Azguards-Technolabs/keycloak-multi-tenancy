---
title: "WhataTalk Login UX Redesign — Technical Addendum"
status: draft
created: 2026-06-11
updated: 2026-06-11
parent-prd: prd.md
purpose: >
  This addendum holds implementation-level detail that would clutter the PRD but
  is needed by engineering during design and sprint planning. It is not a
  substitute for a technical design doc — it is a bridge document.
---

# Technical Addendum

## 1. Keycloak Upgrade: 26.0.7 → 26.4+

### Why the upgrade is required
Keycloak 26.0.7 does not include the finalized WebAuthn/passkeys SPI required to bind a passkey credential to a specific username in a multi-tenant context. Version 26.4+ ships the `webauthn-register` and `webauthn-authenticate` required-action providers and the `WebAuthnAuthenticator` SPI in a stable form suitable for production use.

### Extension compatibility
The `anarsultanov/keycloak-multi-tenancy` extension must be upgraded to a version compiled against Keycloak 26.4+ APIs. The upgrade should be validated in a staging environment before production deployment. Key extension SPI interfaces to verify:

- `AuthenticatorFactory` — confirm no breaking API changes in the target version.
- `RequiredActionProvider` — the invite acceptance action relies on this.
- FreeMarker template resolution path — verify theme override lookup order is unchanged.

### Rollout considerations
A full replacement (not a rolling upgrade) is expected given Keycloak's single-node auth server model. A maintenance window with a documented rollback procedure is required. A blue-green or canary deployment may be used if the infrastructure supports it.

---

## 2. Backend Deltas

The following table maps PRD functional requirements to backend changes. This is a summary for planning — detailed design belongs in the engineering tech spec.

| Area | Change | Linked FRs |
|------|--------|------------|
| **KC Upgrade** | Upgrade Keycloak runtime from 26.0.7 to 26.4+. Recompile extension against new APIs. | FR-PK-4 |
| **Passkey SPI** | Implement or enable `WebAuthnAuthenticator` SPI bound to username credential. Add passkey registration as a post-login optional required action. | FR-PK-1, FR-PK-2, FR-PK-3, FR-PK-5 |
| **Magic Link** | Add optional `MagicLinkAuthenticator` that gates on `emailVerified` attribute. Expose as conditional step in the browser flow. | FR-L-6 |
| **Invite Link** | On invite link click, set `emailVerified = true` on the Keycloak user before redirecting to login. This must happen at the link-verification endpoint, not post-login. | FR-INV-1 |
| **Auto-accept logic** | In the `review-tenant-invitations` required action: if `pendingInvites.size() == 1`, auto-accept without rendering the list UI. Render the toast confirmation screen instead. | FR-INV-2, FR-INV-3 |
| **Async User-Service call** | Refactor the synchronous User-Service HTTP call in the invite acceptance SPI to a fire-and-forget async call (e.g., submitted to a thread pool executor). The required action must complete and allow KC to proceed regardless of User-Service response. Store a retry flag if the call fails. | FR-INV-5, FR-INV-6, NFR-S-3 |
| **Retry banner signal** | Expose a session note or Keycloak user attribute that the FTL template can read to conditionally render the quiet retry banner. | FR-INV-6 |
| **i18n cleanup** | Audit all in-scope `messages_*.properties` files for duplicate keys. Remove duplicates. | NFR-S-2 |

---

## 3. FreeMarker Template Changes

### In-scope templates (to be refactored)

| Template | Change Type |
|----------|-------------|
| `login.ftl` | Full refactor: semantic tokens, passkey affordance, SSO link, magic-link option, inline errors, microcopy update |
| `login-select-authenticator.ftl` | Replace raw SSO alias input with a clean "Sign in with SSO" secondary link pattern |
| `select-authenticator.ftl` | Visual token update |
| `review-tenant-invitations.ftl` | Auto-accept single-invite path (renders toast only); multi-invite picker redesign |
| `webauthn-authenticate.ftl` | New or refactored template for passkey prompt |
| `webauthn-register.ftl` | New or refactored template for passkey setup prompt post-login |
| `select-tenant.ftl` (Account Picker) | Full refactor: skeleton state, search field (conditional on >4 accounts), last-used pin, role display |

### Out-of-scope templates (do not modify)

- `register.ftl` and all registration flow templates.
- `login-oauth-grant.ftl`
- Email templates (`email/` directory)

---

## 4. CSS Design Token System

All in-scope FreeMarker templates must reference CSS custom properties (tokens) rather than hardcoded values. The token system should be defined in a shared CSS file loaded by the Keycloak theme.

### Core tokens

```css
:root {
  /* Brand */
  --color-primary:         #0F766E;
  --color-primary-hover:   #0d6b63;
  --color-focus-ring:      #0F766E;

  /* Surface */
  --color-background:      #f0fdf8;   /* soft mint */
  --color-card-bg:         #ffffff;
  --color-card-shadow:     0 1px 4px rgba(0, 0, 0, 0.08);

  /* Text */
  --color-text-primary:    #111827;
  --color-text-secondary:  #6b7280;
  --color-text-danger:     #dc2626;

  /* Border */
  --color-border:          #d1d5db;
  --color-border-focus:    #0F766E;

  /* Interactive */
  --color-btn-primary-bg:  #0F766E;
  --color-btn-primary-text:#ffffff;

  /* Layout */
  --card-max-width:        400px;
  --card-border-radius:    12px;
  --focus-ring-width:      2px;
  --focus-ring-offset:     2px;
}
```

### Dark mode

Dark mode overrides are applied via `@media (prefers-color-scheme: dark)` or a `data-theme="dark"` attribute on `<html>`. The same token names are reused; only their values change.

```css
@media (prefers-color-scheme: dark) {
  :root {
    --color-background:    #0f1a18;
    --color-card-bg:       #1a2b29;
    --color-text-primary:  #f9fafb;
    --color-text-secondary:#9ca3af;
    --color-border:        #374151;
  }
}
```

### Tokens to eliminate

The following hardcoded values appear in existing templates and must be replaced with the token equivalents above:

| Hardcoded value | Replacement token |
|-----------------|-------------------|
| `#2c2f33` | `--color-card-bg` (dark) or `--color-background` |
| `#00b4d8` | `--color-primary` |
| Any inline `color: #...` on auth form elements | Corresponding token |

---

## 5. Accessibility Implementation Notes

These notes supplement the NFRs in the PRD with enough detail for a developer to implement correctly without additional research.

### Focus management
- After a form submission error, focus must be programmatically moved to the first invalid field or the inline error summary.
- On screen transitions (e.g., login → account picker), focus must be set to the page heading (`h1`) of the new screen.

### Error announcement
```html
<!-- Pattern for inline field errors -->
<div id="username-error" role="alert" aria-live="polite" class="error-text">
  That username or password doesn't match
</div>
<input
  id="username"
  type="text"
  autocomplete="username webauthn"
  aria-describedby="username-error"
  aria-invalid="true"
/>
```

### Reduced motion
```css
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    transition-duration: 0.01ms !important;
  }
}
```

### Skeleton loader accessibility
Skeleton rows must include `aria-hidden="true"` and the containing region must carry `aria-busy="true"` while loading. Remove both attributes when content renders.

---

## 6. Microcopy Reference

This is the canonical copy for in-scope screens. It must match exactly in FTL templates and i18n message bundles.

| Location | Copy |
|----------|------|
| Login screen title | Login to Agent Account |
| Login primary button | Login |
| Password error (wrong credentials) | That username or password doesn't match |
| Passkey primary affordance | Use your passkey |
| Passkey post-login prompt | Sign in faster next time — set up a passkey. |
| Magic link option | Email me a sign-in link |
| SSO option | Sign in with SSO |
| Account picker title | Select Account |
| Account picker — last used tag | Last used |
| Account picker — role display | ADMIN / AGENT (display as chip or label) |
| Invite toast | {Inviter} invited you to join {Account} |
| Invite toast — decline action | Not you? Decline |
| Rate-limit / lockout message | [TBD — see OQ in PRD; must include recovery path] |

---

## 7. Production Hygiene Checklist

Before launch, engineering must verify:

- [ ] No `console.log`, `console.debug`, or `console.error` statements remain in any in-scope `.js` or `.ftl` file.
- [ ] All in-scope `messages_*.properties` files have been audited for duplicate keys and duplicates removed.
- [ ] All hardcoded hex color values (`#2c2f33`, `#00b4d8`, and equivalents) have been replaced with CSS tokens.
- [ ] The User-Service call in the invite acceptance SPI is asynchronous and does not block the Keycloak required-action completion.
- [ ] Passkey affordance is absent (not broken) when no passkey is registered for the user.
- [ ] WCAG 2.1 AA automated scan (axe-core or equivalent) shows zero critical/serious violations on all in-scope screens.
- [ ] Keycloak 26.4+ upgrade has been validated in a staging environment with production-equivalent data volume.
- [ ] Extension version compatibility with KC 26.4+ has been confirmed by running the full login and invite flows in staging.
