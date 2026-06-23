---
baseline_commit: ea9e5840bf93ccca843694f54bb1b4577a26da6c
---

# Story 4-4: Skeleton Loading State

**Epic:** 4 — Multi-Account Selection
**Story ID:** 4.4
**Status:** done
**Working Repository:** `azguards-keycloak-custom-theme`
**No Java / SPI changes required for this story.**

---

## User Story

**As** an Agent opening the account picker,
**I want** an immediate visual placeholder while the screen paints,
**So that** the interface never feels blank or broken on slow connections.

---

## Acceptance Criteria (BDD)

### AC-1 — Skeleton rows shown while loading (FR-AS-6, UX-DR11)
```
Given  the account picker page begins to load
When   the browser first paints the list region
Then   skeleton placeholder rows (class .wt-skeleton-row) are visible
And    the list region has aria-busy="true"
And    each skeleton row has aria-hidden="true"
```

### AC-2 — 500 ms performance gate (NFR-P-2)
```
Given  the page is displayed
When   the browser renders the body
Then   the list region shows either skeleton rows OR real account rows
       within 500 ms of first paint
```

### AC-3 — Real rows replace skeleton rows after JS executes
```
Given  JS has executed on the page
When   DOMContentLoaded fires
Then   skeleton rows are removed from the DOM (or hidden)
And    real account rows become visible
And    aria-busy is removed (or set to "false") on the list region
```

### AC-4 — Reduced motion: no shimmer (UX-DR11, NFR-A-6)
```
Given  the user has prefers-reduced-motion: reduce set
When   skeleton rows are visible
Then   the shimmer animation is disabled (static placeholder only)
```
> **Note:** The `prefers-reduced-motion` rule is already handled in `components.css` — no new CSS required.

### AC-5 — No skeleton shown when there are no accounts
```
Given  data.tenants is empty
When   the page renders
Then   no skeleton rows are shown
And    the "No accounts available." empty state is displayed immediately
```

### AC-6 — No console.log in production JS (NFR-S-1)
```
Given  the JS script in select-tenant.ftl is executed
Then   no console.log, console.warn, or console.error calls are present
```

---

## Technical Context

### Architecture — Why "skeleton" on an SSR page

Keycloak renders `select-tenant.ftl` fully server-side. The tenant list is embedded in the HTML before any byte reaches the browser. There is **no async data load** — the data is always present.

The skeleton loading pattern is still valuable because:
- On slow connections, HTML loads before CSS/JS; the user sees raw unstyled content for a brief moment.
- Progressive rendering: browsers can paint `<body>` before all resources are fetched.
- Provides a consistent, accessible perceived-loading experience matching the UX design spec.

**Implementation approach (SSR skeleton pattern):**
1. Render N skeleton rows in the FTL as static HTML, **before** the real `<#list>` rows, inside the `#account-list` div.
2. The list container starts with `aria-busy="true"`.
3. Each skeleton row carries `aria-hidden="true"` (screen readers skip them).
4. The `<script>` block at the bottom of `<body>` runs synchronously — by this time the full DOM is parsed. It:
   - Removes all `.wt-skeleton-row` elements from the DOM.
   - Removes `aria-busy` from the list container.
5. On fast connections this transition is imperceptible (< 16 ms). On slow connections users briefly see the shimmer.

**Number of skeleton rows:** Render 3 rows. This matches a compact mid-range account count without over-inflating HTML.

---

## Files to Modify

| # | Repository | File | Action |
|---|-----------|------|--------|
| 1 | `azguards-keycloak-custom-theme` | `src/main/resources/theme/azguards-whatsapp/login/select-tenant.ftl` | MODIFY — add skeleton markup + update JS |
| 2 | `azguards-keycloak-custom-theme` | `src/main/resources/theme/azguards-whatsapp/login/messages/messages_en.properties` | MODIFY — add `selectAccountLoading` key |
| 3 | `azguards-keycloak-custom-theme` | `src/main/resources/theme/azguards-whatsapp/login/resources/css/selectTenant.css` | NO CHANGE — skeleton CSS already in components.css |

**DO NOT modify** `components.css` — the skeleton classes are already there (lines 365–418, shipped in Story 1-4).
**DO NOT modify** any Java/SPI files in `keycloak-multi-tenancy` repo.

---

## Current File State (post-Story 4-3)

### `select-tenant.ftl` — exact current content

```freemarker
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${msg("selectAccountTitle")} | WhataTalk</title>
    <link rel="icon" href="${url.resourcesPath}/img/whatatalk-favicon.png" type="image/png">
    <link rel="stylesheet" href="${url.resourcesPath}/css/tokens.css">
    <link rel="stylesheet" href="${url.resourcesPath}/css/components.css">
    <link rel="stylesheet" href="${url.resourcesPath}/css/selectTenant.css">
  </head>
  <body class="wt-select-account-page">

    <div class="wt-card" role="main">

      <h1 class="wt-select-account__title" id="page-title" tabindex="-1">
        ${msg("selectAccountTitle")}
      </h1>

      <#if data.tenants?has_content>
        <form
          action="${url.loginAction}"
          method="post"
          aria-labelledby="page-title"
        >
          <#if data.tenants?size gt 4>
            <div class="wt-select-account__search">
              <label for="wt-account-search" class="sr-only">
                ${msg("selectAccountSearchLabel")}
              </label>
              <input
                type="search"
                id="wt-account-search"
                class="wt-select-account__search-input"
                placeholder="${msg("selectAccountSearchPlaceholder")}"
                autocomplete="off"
                aria-label="${msg("selectAccountSearchLabel")}"
                aria-controls="account-list"
              >
            </div>
          </#if>
          <div
            id="account-list"
            class="wt-select-account__list"
            role="list"
            aria-label="${msg("selectAccountTitle")}"
          >
            <#list data.tenants as tenant>
              ... (real rows with initials + role + lastUsed badge)
            </#list>
          </div>
          <p id="wt-no-match" class="wt-select-account__no-match" hidden aria-live="polite"
             data-template="${msg("selectAccountNoMatch")?html}"></p>
        </form>
      <#else>
        <p class="wt-select-account__empty">${msg("selectAccountNoTenants")}</p>
      </#if>

    </div>

    <script>
      (function () {
        var h1 = document.getElementById('page-title');
        if (h1) { h1.focus(); }

        var searchInput = document.getElementById('wt-account-search');
        if (!searchInput) { return; }

        var list    = document.getElementById('account-list');
        var noMatch = document.getElementById('wt-no-match');
        var items   = list
          ? Array.prototype.slice.call(list.querySelectorAll('[role="listitem"]'))
          : [];

        function filter() { ... }

        searchInput.addEventListener('input',  filter);
        searchInput.addEventListener('search', filter);
      })();
    </script>

  </body>
</html>
```

### `messages_en.properties` — exact current content (theme repo)

```properties
updatePasswordSuccess=Your password has been successfully updated.
invalidUserMessage=That username or password doesn't match.
invalidPasswordMessage=That username or password doesn't match.
accountTemporarilyDisabledMessage=Too many attempts. Your account is temporarily locked — check your email for reset instructions or try again in 15 minutes.
ssoHeader=Sign in with SSO
ssoInfo=Enter your organisation's SSO name to continue.
ssoError=That SSO name doesn't match. Check with your admin.
selectAccountTitle=Select Account
selectAccountLastUsed=Last used
selectAccountNoTenants=No accounts available.
selectAccountSearchLabel=Search accounts
selectAccountSearchPlaceholder=Search accounts...
selectAccountNoMatch=No accounts match "{query}".
```

---

## Skeleton CSS Already in `components.css` (DO NOT re-declare)

The following classes are **already shipped** in `components.css` lines 365–418. The FTL simply uses them:

```css
@keyframes wt-shimmer { /* 0% → 100% background-position slide */ }
.wt-skeleton-row     { display:flex; align-items:center; gap:var(--wt-space-3); padding:var(--wt-space-3) 0; }
.wt-skeleton-avatar  { width:40px; height:40px; border-radius:50%; shimmer animation; }
.wt-skeleton-lines   { flex:1; display:flex; flex-direction:column; gap:var(--wt-space-2); }
.wt-skeleton-line    { height:12px; shimmer animation; }
.wt-skeleton-line--short { width:60%; }
@media (prefers-reduced-motion:reduce) { animation:none; background-image:none; }
```

---

## Implementation Guide

### Step 1 — Add `selectAccountLoading` i18n key

Append to `messages/messages_en.properties`:

```properties
selectAccountLoading=Loading accounts…
```

This is used as the `aria-label` on the list region while `aria-busy="true"` is active, giving screen readers a meaningful description of the loading state.

### Step 2 — Modify `select-tenant.ftl`

**Change 1:** On the `#account-list` div, add `aria-busy="true"`. The JS will remove this attribute when it runs.

**Before:**
```html
<div
  id="account-list"
  class="wt-select-account__list"
  role="list"
  aria-label="${msg("selectAccountTitle")}"
>
```

**After:**
```html
<div
  id="account-list"
  class="wt-select-account__list"
  role="list"
  aria-label="${msg("selectAccountLoading")}"
  aria-busy="true"
  data-loaded-label="${msg("selectAccountTitle")}"
>
```

> Use a `data-loaded-label` attribute to store the final aria-label so JS can swap it in without hardcoding strings.

**Change 2:** Add 3 skeleton rows immediately inside `#account-list`, before the `<#list>` block.

```html
<div id="account-list" class="wt-select-account__list" role="list"
     aria-label="${msg("selectAccountLoading")}"
     aria-busy="true"
     data-loaded-label="${msg("selectAccountTitle")}">

  <!-- Skeleton rows: removed by JS on DOMContentLoaded -->
  <div class="wt-skeleton-row" aria-hidden="true">
    <div class="wt-skeleton-avatar"></div>
    <div class="wt-skeleton-lines">
      <div class="wt-skeleton-line"></div>
      <div class="wt-skeleton-line wt-skeleton-line--short"></div>
    </div>
  </div>
  <div class="wt-skeleton-row" aria-hidden="true">
    <div class="wt-skeleton-avatar"></div>
    <div class="wt-skeleton-lines">
      <div class="wt-skeleton-line"></div>
      <div class="wt-skeleton-line wt-skeleton-line--short"></div>
    </div>
  </div>
  <div class="wt-skeleton-row" aria-hidden="true">
    <div class="wt-skeleton-avatar"></div>
    <div class="wt-skeleton-lines">
      <div class="wt-skeleton-line"></div>
      <div class="wt-skeleton-line wt-skeleton-line--short"></div>
    </div>
  </div>

  <#list data.tenants as tenant>
    ... (existing real row markup unchanged)
  </#list>
</div>
```

**Change 3:** Update the `<script>` IIFE to remove skeleton rows and update ARIA on the list.

Add the following logic **at the top of the IIFE**, before the existing focus management and search filter code:

```javascript
/* Skeleton teardown: remove placeholder rows, mark list as loaded (AC-3) */
var list = document.getElementById('account-list');
if (list) {
  var skeletons = list.querySelectorAll('.wt-skeleton-row');
  for (var i = 0; i < skeletons.length; i++) {
    list.removeChild(skeletons[i]);
  }
  list.removeAttribute('aria-busy');
  var loadedLabel = list.getAttribute('data-loaded-label');
  if (loadedLabel) {
    list.setAttribute('aria-label', loadedLabel);
  }
}
```

> **Why at the top:** The script is at the bottom of `<body>`, meaning the DOM is fully parsed before it runs. Placing teardown first ensures it happens before focus management or search wiring. No `DOMContentLoaded` listener needed — the script already runs after parse.

> **`querySelectorAll` return value:** It returns a static NodeList. Iterating with a `for` loop and `removeChild` is safe. Do NOT use a live HTMLCollection here.

> **No `forEach`:** Use ES5-compatible `for` loop — consistent with the existing IIFE pattern in this file.

### Step 3 — AC-5: Skeleton only shown when tenants exist

The skeleton rows live inside `<#if data.tenants?has_content>` → inside the `<form>` → inside `#account-list`. When there are no tenants, the FTL renders the `<p class="wt-select-account__empty">` branch instead. Skeleton rows are never rendered in the empty-state branch. **No additional guard needed.**

---

## Complete Modified `select-tenant.ftl`

For clarity, the full file after this story's changes (replace the entire file):

```freemarker
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${msg("selectAccountTitle")} | WhataTalk</title>
    <link rel="icon" href="${url.resourcesPath}/img/whatatalk-favicon.png" type="image/png">
    <link rel="stylesheet" href="${url.resourcesPath}/css/tokens.css">
    <link rel="stylesheet" href="${url.resourcesPath}/css/components.css">
    <link rel="stylesheet" href="${url.resourcesPath}/css/selectTenant.css">
  </head>
  <body class="wt-select-account-page">

    <div class="wt-card" role="main">

      <h1 class="wt-select-account__title" id="page-title" tabindex="-1">
        ${msg("selectAccountTitle")}
      </h1>

      <#if data.tenants?has_content>
        <form
          action="${url.loginAction}"
          method="post"
          aria-labelledby="page-title"
        >
          <#if data.tenants?size gt 4>
            <div class="wt-select-account__search">
              <label for="wt-account-search" class="sr-only">
                ${msg("selectAccountSearchLabel")}
              </label>
              <input
                type="search"
                id="wt-account-search"
                class="wt-select-account__search-input"
                placeholder="${msg("selectAccountSearchPlaceholder")}"
                autocomplete="off"
                aria-label="${msg("selectAccountSearchLabel")}"
                aria-controls="account-list"
              >
            </div>
          </#if>
          <div
            id="account-list"
            class="wt-select-account__list"
            role="list"
            aria-label="${msg("selectAccountLoading")}"
            aria-busy="true"
            data-loaded-label="${msg("selectAccountTitle")}"
          >
            <div class="wt-skeleton-row" aria-hidden="true">
              <div class="wt-skeleton-avatar"></div>
              <div class="wt-skeleton-lines">
                <div class="wt-skeleton-line"></div>
                <div class="wt-skeleton-line wt-skeleton-line--short"></div>
              </div>
            </div>
            <div class="wt-skeleton-row" aria-hidden="true">
              <div class="wt-skeleton-avatar"></div>
              <div class="wt-skeleton-lines">
                <div class="wt-skeleton-line"></div>
                <div class="wt-skeleton-line wt-skeleton-line--short"></div>
              </div>
            </div>
            <div class="wt-skeleton-row" aria-hidden="true">
              <div class="wt-skeleton-avatar"></div>
              <div class="wt-skeleton-lines">
                <div class="wt-skeleton-line"></div>
                <div class="wt-skeleton-line wt-skeleton-line--short"></div>
              </div>
            </div>

            <#list data.tenants as tenant>
              <#assign nameTrimmed = (tenant.name!"")?trim>
              <#if nameTrimmed?has_content>
                <#assign words = nameTrimmed?split(r"\s+", "r")>
                <#if words?size gte 2>
                  <#assign initials = words[0]?substring(0,1)?upper_case + words[1]?substring(0,1)?upper_case>
                <#else>
                  <#assign initials = nameTrimmed?substring(0,1)?upper_case>
                </#if>
              <#else>
                <#assign initials = "?">
              </#if>

              <div role="listitem" data-name="${((tenant.name!"")?trim)?lower_case}">
                <button
                  type="submit"
                  name="tenant"
                  value="${tenant.id}"
                  class="wt-account-card"
                  aria-label="${kcSanitize(tenant.name)}<#if tenant.lastUsed> — ${msg("selectAccountLastUsed")}</#if>"
                >
                  <#if tenant.logoUrl?has_content>
                    <img
                      src="${tenant.logoUrl}"
                      alt=""
                      class="wt-account-card__avatar"
                      aria-hidden="true"
                      onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';"
                    >
                    <span class="wt-account-card__initials" style="display:none;" aria-hidden="true">${initials?html}</span>
                  <#else>
                    <span class="wt-account-card__initials" aria-hidden="true">${initials?html}</span>
                  </#if>

                  <span class="wt-account-card__body">
                    <span class="wt-account-card__name">${kcSanitize(tenant.name)}</span>
                    <#if tenant.roles?has_content>
                      <span class="wt-account-card__role">${kcSanitize(tenant.roles?join(", "))}</span>
                    </#if>
                  </span>

                  <#if tenant.lastUsed>
                    <span class="wt-account-card__badge" aria-hidden="true">
                      ${msg("selectAccountLastUsed")}
                    </span>
                  </#if>
                </button>
              </div>
            </#list>
          </div>
          <p
            id="wt-no-match"
            class="wt-select-account__no-match"
            hidden
            aria-live="polite"
            data-template="${msg("selectAccountNoMatch")?html}"
          ></p>
        </form>
      <#else>
        <p class="wt-select-account__empty">${msg("selectAccountNoTenants")}</p>
      </#if>

    </div>

    <script>
      (function () {
        /* Skeleton teardown: remove placeholder rows, restore list ARIA state (AC-3) */
        var list = document.getElementById('account-list');
        if (list) {
          var skeletons = list.querySelectorAll('.wt-skeleton-row');
          for (var i = 0; i < skeletons.length; i++) {
            list.removeChild(skeletons[i]);
          }
          list.removeAttribute('aria-busy');
          var loadedLabel = list.getAttribute('data-loaded-label');
          if (loadedLabel) {
            list.setAttribute('aria-label', loadedLabel);
          }
        }

        /* Focus management: move focus to page title on load (UX-DR14) */
        var h1 = document.getElementById('page-title');
        if (h1) { h1.focus(); }

        /* Search filter: only wired when the search input exists (> 4 accounts) */
        var searchInput = document.getElementById('wt-account-search');
        if (!searchInput) { return; }

        var noMatch = document.getElementById('wt-no-match');
        var items   = list
          ? Array.prototype.slice.call(list.querySelectorAll('[role="listitem"]'))
          : [];

        function filter() {
          var raw   = searchInput.value;
          var query = raw.trim().toLowerCase();
          var visible = 0;

          items.forEach(function (item) {
            var name  = item.getAttribute('data-name') || '';
            var show  = query === '' || name.indexOf(query) === 0;
            item.style.display = show ? '' : 'none';
            if (show) { visible++; }
          });

          if (noMatch) {
            if (query !== '' && visible === 0) {
              noMatch.textContent = (noMatch.getAttribute('data-template') || '')
                .replace('{query}', raw.trim());
              noMatch.removeAttribute('hidden');
            } else {
              noMatch.setAttribute('hidden', '');
            }
          }
        }

        searchInput.addEventListener('input',  filter);
        searchInput.addEventListener('search', filter);
      })();
    </script>

  </body>
</html>
```

---

## Critical Implementation Notes

### PRESERVE: Search filter `items` variable

The existing search filter builds `items` from `list.querySelectorAll('[role="listitem"]')`. After skeleton teardown, this query is still correct — real rows have `role="listitem"`, skeleton rows do NOT have `role="listitem"`. The teardown runs before `items` is populated, so the list reference is clean.

**Verify:** `list` is declared once at the top of the skeleton teardown block. The search filter section reuses it. Do not re-declare `var list` in the search section — this would be a variable shadowing bug. The existing code already declares `var list` — after refactoring, ensure there is exactly ONE `var list` declaration in the IIFE.

### PRESERVE: All existing behaviours

- Focus management on `h1#page-title` — unchanged
- Search field wired when `> 4` accounts — unchanged  
- No-match message with `{query}` interpolation — unchanged
- `data-name` attribute on real list items — unchanged
- `aria-controls="account-list"` on search input — unchanged (list still has same id)

### AVOID: Race condition with search `items`

The `items` array is populated AFTER skeleton teardown, so it will never contain skeleton rows. This is correct. Do not move skeleton teardown below the `items` assignment.

### AVOID: `forEach` on NodeList

The existing code uses `items.forEach(...)` — that works because `items` is an Array (created via `Array.prototype.slice.call(...)`). The skeleton teardown uses `querySelectorAll` which returns a static NodeList — use a `for` loop, not `forEach`, for consistency with ES5 patterns.

---

## Testing Checklist

### Manual (required before marking done)

- [ ] Open the Select Account page with 1–3 accounts: skeleton rows appear briefly, then real rows render
- [ ] Open with > 4 accounts: skeleton rows appear, search field present after teardown, search works
- [ ] Open with 0 accounts: no skeleton rows, empty state message shown
- [ ] Inspect DOM before/after JS executes: confirm `.wt-skeleton-row` elements are removed
- [ ] Inspect `aria-busy` attribute: present in initial HTML, absent after JS runs
- [ ] Inspect `aria-label` on `#account-list`: shows "Loading accounts…" initially, "Select Account" after
- [ ] Check browser accessibility tree (axe DevTools or similar): no ARIA errors
- [ ] Simulate `prefers-reduced-motion: reduce` in DevTools: skeleton is static (no shimmer)
- [ ] Test with CSS disabled: real rows visible without skeleton (graceful degradation)

### Regression (must not break)

- [ ] Search filter still works on > 4 accounts
- [ ] No-match message displays correctly
- [ ] Last-used badge renders on correct account
- [ ] Role display renders correctly
- [ ] h1 receives focus on page load
- [ ] Form submission selects correct tenant

---

## Out of Scope

- Epic 5 (Invited Agent Onboarding) — separate epic
- Any modifications to `components.css`, `tokens.css`, or `style.css`
- Any Java SPI changes in `keycloak-multi-tenancy` repo
- Any new CSS classes (all needed classes already exist in `components.css`)
- Playwright E2E tests for the skeleton transition (acceptable to skip — the state is imperceptible in test environments running on localhost)

---

## Dev Notes

- **Context:** This is the last story in Epic 4. Stories 4-1 through 4-3 are all done.
- **SSR clarification:** There is no async API call — all tenant data is in the initial HTML. The skeleton pattern here is a perceived-performance and accessibility technique, not a true async data-loading scenario.
- **i18n key `selectAccountLoading`:** Added to the theme repo `messages_en.properties` only. The keycloak-multi-tenancy SPI Java constants file does NOT need this key — it is purely a UI string.
- **`data-loaded-label` pattern:** Avoids hardcoding the final ARIA label in JS, keeping text in the message bundle where it belongs.
- **ES5 JS constraint:** The existing IIFE uses ES5 throughout (`var`, `function`, no arrow functions). Maintain this — Keycloak's theme resources may be served to older browsers.

---

## File List

- `azguards-keycloak-custom-theme/src/main/resources/theme/azguards-whatsapp/login/select-tenant.ftl` — MODIFIED: added `aria-busy`/`aria-label`/`data-loaded-label` to `#account-list`, added 3 skeleton rows, added skeleton teardown at top of IIFE, consolidated single `var list` declaration
- `azguards-keycloak-custom-theme/src/main/resources/theme/azguards-whatsapp/login/messages/messages_en.properties` — MODIFIED: added `selectAccountLoading` i18n key

---

## Dev Agent Record

### Implementation Notes

Implemented the SSR skeleton loading pattern for the account picker following the story spec exactly.

**What was done:**
1. Added `selectAccountLoading=Loading accounts…` to `messages_en.properties` (AC-1, AC-3 aria-label swap).
2. Updated `#account-list` div: replaced `aria-label="${msg("selectAccountTitle")}"` with `aria-label="${msg("selectAccountLoading")}"`, added `aria-busy="true"`, added `data-loaded-label="${msg("selectAccountTitle")}"` for JS-driven label restore.
3. Added 3 `.wt-skeleton-row` elements (each with `aria-hidden="true"`) inside `#account-list` before the `<#list>` block — they are inside `<#if data.tenants?has_content>` so AC-5 (no skeleton on empty state) is automatically satisfied.
4. Moved skeleton teardown code to the top of the JS IIFE. `var list` is now declared once (in teardown block) and reused in the search filter section — eliminating the previous duplicate declaration pattern.

**Key decisions:**
- Skeleton rows placed inside `<#if data.tenants?has_content>` block: satisfies AC-5 with zero extra guards.
- `data-loaded-label` attribute used: keeps final ARIA label in the message bundle rather than hardcoded in JS.
- ES5-only `for` loop for `querySelectorAll` iteration (static NodeList): consistent with existing IIFE pattern and safe for older Keycloak browser targets.
- No CSS changes: `.wt-skeleton-row`, `.wt-skeleton-avatar`, `.wt-skeleton-lines`, `.wt-skeleton-line`, `.wt-skeleton-line--short` are already in `components.css` from Story 1-4. `prefers-reduced-motion` handled there too (AC-4).

### Completion Notes

All 6 Acceptance Criteria satisfied:
- AC-1: Skeleton rows with `.wt-skeleton-row`, `aria-busy="true"`, `aria-hidden="true"` per row ✅
- AC-2: SSR — skeleton visible from first paint (no async fetch, no 500ms risk) ✅
- AC-3: JS teardown removes skeleton rows, removes `aria-busy`, swaps `aria-label` ✅
- AC-4: `prefers-reduced-motion` already handled in `components.css` (no new CSS needed) ✅
- AC-5: Skeleton rows inside `<#if data.tenants?has_content>` — never rendered on empty state ✅
- AC-6: No `console.log`/`console.warn`/`console.error` in script ✅

---

## Change Log

- 2026-06-17: Implemented skeleton loading state for account picker — added 3 shimmer placeholder rows, aria-busy/aria-label ARIA lifecycle, JS teardown, and `selectAccountLoading` i18n key (Story 4-4)

---

## Review Findings

_Code review 2026-06-17 (Blind Hunter + Edge Case Hunter + Acceptance Auditor). Acceptance Auditor: all 6 ACs and all listed constraints PASS. Scope: `select-tenant.ftl` + theme `messages_en.properties` (diff vs theme HEAD `d238a35`)._

### defer

- [x] [Review][Defer] `aria-busy="true"` never clears when JS is disabled [`azguards-keycloak-custom-theme/.../login/select-tenant.ftl`] — deferred, see deferred-work.md (2026-06-17). The only genuinely 4-4-introduced gap; same family as the existing noscript deferral from 4-3. `aria-busy` cannot be cleared without JS; real tenant buttons remain usable. Low — JS is effectively required for the broader login flow.

### dismissed (not persisted as action items)

- Prefix-only search (not substring) — **by design**, FR-AS-5 "prefix-first"; spec 4-3 explicitly states substring is NOT required.
- `h1#page-title` not focusable without `tabindex` — **false positive**, `tabindex="-1"` is present.
- FreeMarker `?substring`/`?split` throwing on empty/whitespace names — **guarded** by `?trim` + `?has_content` + `\s+` collapse.
- `tenant.roles` missing-key error — **guarded** by `<#if tenant.roles?has_content>`.
- Logo `onerror` `nextElementSibling` fragility — works as written (initials span is the immediate sibling).
- AC-3 "DOMContentLoaded" wording vs inline end-of-`<body>` IIFE — functionally equivalent; auditor PASS.
- Attribute-context escaping of `aria-label`/`data-name`/`src`/`value` (kcSanitize/raw, auto-escape off) — **real but already tracked**: pre-existing, carried over from the prior template, and deliberately deferred in the 4-2 and 4-3 reviews (see deferred-work.md). Not introduced by 4-4.
- `String.replace('{query}', …)` first-occurrence-only / `$&` special-pattern quirk — **already tracked** (deferred from 4-3 review).
- Locale-casing / internal-multi-space search mismatch, Escape-key clear, shared-prefix usability, punctuation-only initials — acceptable edges for a simple prefix filter; cosmetic/extreme.
