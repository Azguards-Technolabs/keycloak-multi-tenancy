---
baseline_commit: ea9e5840bf93ccca843694f54bb1b4577a26da6c
---

# Story 4.3: Conditional Search & No-Match State

Status: done

## Story

As an Agent with many Accounts,
I want to filter by name,
So that I'm not scanning a long list.

## Acceptance Criteria

1. **Given** the Agent belongs to more than four Accounts
   **When** the picker renders
   **Then** a search field appears above the list (UX-DR6); when four or fewer, no search field is shown (FR-AS-5)

2. **Given** the search field
   **When** the Agent types
   **Then** matching is by Account name only, case-insensitive, prefix-first (FR-AS-5)

3. **Given** a query with no matches
   **When** filtering completes
   **Then** "No accounts match '{query}'." is shown (FR-AS-5)

## Tasks / Subtasks

---

### REPO: `azguards-keycloak-custom-theme`

> **This is the ONLY repository for this story.** The full Account list is already in the `select-tenant.ftl` FreeMarker context from Story 4.2 — no new Java SPI changes are required.
>
> Working directory: `~/WorkSpace/azguards-whatsapp/azguards-keycloak-custom-theme`

---

- [x] **Task 1: Add search field + no-match styles to `selectTenant.css`** (AC: #1, #3)
  - [x] Open `src/main/resources/theme/azguards-whatsapp/login/resources/css/selectTenant.css`
  - [x] **Append** the following block **at the end of the file** (after the `.wt-select-account__empty` block):
    ```css
    /* ── Search field wrapper ───────────────────────── */
    /* UX-DR6: appears above the list only when count > 4 */
    .wt-select-account__search {
      margin-bottom: var(--wt-space-3);
    }

    .wt-select-account__search-input {
      height: var(--wt-field-height);            /* 46px */
      width: 100%;
      box-sizing: border-box;
      border-radius: var(--wt-radius-field);     /* 10px */
      border: 1px solid var(--wt-border-strong);
      background-color: var(--wt-surface);
      color: var(--wt-ink);
      font-family: var(--wt-font-family), sans-serif;
      font-size: var(--wt-text-body-size);
      line-height: var(--wt-text-body-line);
      /* leading search icon via inline SVG background (no icon font required) */
      padding: 0 var(--wt-space-3) 0 calc(var(--wt-space-3) + 20px + var(--wt-space-2));
      background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='16' height='16' fill='none' viewBox='0 0 16 16'%3E%3Ccircle cx='6.5' cy='6.5' r='5' stroke='%2364748B' stroke-width='1.5'/%3E%3Cline x1='10.5' y1='10.5' x2='14' y2='14' stroke='%2364748B' stroke-width='1.5' stroke-linecap='round'/%3E%3C/svg%3E");
      background-repeat: no-repeat;
      background-position: var(--wt-space-3) center;
      transition: border-color var(--wt-duration) var(--wt-easing),
                  background-color var(--wt-duration) var(--wt-easing);
      /* remove default search input chrome (Safari clears button) */
      -webkit-appearance: none;
      appearance: none;
    }

    .wt-select-account__search-input::placeholder {
      color: var(--wt-muted);
    }

    .wt-select-account__search-input:focus {
      outline: none;
      border-color: var(--wt-primary);
      box-shadow: 0 0 0 var(--wt-focus-width) var(--wt-focus-ring-halo);
    }

    @media (prefers-reduced-motion: reduce) {
      .wt-select-account__search-input { transition: none; }
    }

    /* ── No-match message ───────────────────────────── */
    .wt-select-account__no-match {
      font-size: var(--wt-text-body-size);
      color: var(--wt-muted);
      text-align: center;
      padding: var(--wt-space-6) 0;
      margin: 0;
    }
    ```
    > **Token reference:** `--wt-focus-ring-halo` is defined in `tokens.css` as a translucent focus halo (same as `.wt-field__input:focus` in `components.css` line 158). Do NOT redefine it. The SVG search icon uses `%2364748B` (URL-encoded `#64748B`) which is the `--wt-muted` hex value — hardcoded here because SVG data URIs cannot reference CSS custom properties. This is the only permitted hardcoded color in this story; document it with the inline comment.

---

- [x] **Task 2: Add i18n keys to `messages_en.properties`** (AC: #1, #2, #3)
  - [x] Open `src/main/resources/theme/azguards-whatsapp/login/messages/messages_en.properties`
  - [x] **Append** at the end of the file (after `selectAccountNoTenants`):
    ```properties
    selectAccountSearchLabel=Search accounts
    selectAccountSearchPlaceholder=Search accounts...
    selectAccountNoMatch=No accounts match "{query}".
    ```
    > **Why `{query}` (not `{0}`):** Keycloak's FTL `msg()` helper does a simple property lookup without MessageFormat positional substitution. The `{query}` token is stored as a literal string in the HTML `data-template` attribute and replaced by JavaScript at filter time. Do NOT use `{0}` (MessageFormat positional) as `msg("selectAccountNoMatch", someArg)` is NOT called from FTL for this key.

---

- [x] **Task 3: Update `select-tenant.ftl`** (AC: #1, #2, #3)
  - [x] Open `src/main/resources/theme/azguards-whatsapp/login/select-tenant.ftl`

  **This task has three surgical edits — do NOT do a full rewrite.** The file was fully rewritten in Story 4.2; apply only the diffs below.

  **Edit 3a — add `id` + `data-name` attributes (for JS targeting):**

  Find the `<div class="wt-select-account__list" ...>` opening tag and add `id="account-list"`:
  ```diff
  -          <div
  -            class="wt-select-account__list"
  -            role="list"
  -            aria-label="${msg("selectAccountTitle")}"
  -          >
  +          <div
  +            id="account-list"
  +            class="wt-select-account__list"
  +            role="list"
  +            aria-label="${msg("selectAccountTitle")}"
  +          >
  ```

  Find each `<div role="listitem">` and add a `data-name` attribute carrying the **lowercased, trimmed** tenant name (for JS prefix comparison):
  ```diff
  -                  <div role="listitem">
  +                  <div role="listitem" data-name="${((tenant.name!"")?trim)?lower_case}">
  ```
  > `(tenant.name!"")?trim?lower_case` — safe: `!""` guards null, `?trim` removes leading/trailing whitespace, `?lower_case` pre-lowercases so JS never needs to lowercase the attribute value.

  **Edit 3b — add conditional search field above the list (inside the `<form>`, before the `<div id="account-list"` line):**

  Find the line `<form` ... `>` (opening form tag). After the form opening tag and before the `<div id="account-list"` line, insert:
  ```ftl
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
  ```
  > `data.tenants?size gt 4` evaluates server-side — the search field is not rendered at all for ≤ 4 accounts; no JS needed to show/hide it. `sr-only` is not a class in `components.css` — add it to `selectTenant.css` (Task 1 already handles `.wt-select-account__search`; see note below about `sr-only`).
  >
  > **`sr-only` note:** The `<label for="wt-account-search">` is visually hidden but announced by screen readers. Since we cannot add a project-wide `.sr-only` utility in this story (that belongs to Epic 1's Story 1.5 utilities), and Epic 1 Story 1.5 (`script.js`) is already delivered — check whether a `.sr-only` class already exists in `components.css`. **If NOT present**, append it to `selectTenant.css` instead:
  > ```css
  > .sr-only {
  >   position: absolute;
  >   width: 1px;
  >   height: 1px;
  >   padding: 0;
  >   margin: -1px;
  >   overflow: hidden;
  >   clip: rect(0, 0, 0, 0);
  >   white-space: nowrap;
  >   border-width: 0;
  > }
  > ```
  > If `.sr-only` is already present in `components.css`, do NOT redefine it.

  **Edit 3c — add no-match paragraph (after the closing `</div>` of `id="account-list"`, still inside `<form>`):**

  Find `</div>` that closes `<div id="account-list"...>` and insert after it:
  ```ftl
              <p
                id="wt-no-match"
                class="wt-select-account__no-match"
                hidden
                aria-live="polite"
                data-template="${msg("selectAccountNoMatch")}"
              ></p>
  ```
  > `hidden` is the HTML boolean attribute — the paragraph is invisible until JS sets `noMatch.removeAttribute('hidden')`. `aria-live="polite"` announces the message when it appears. `data-template` stores the i18n string (e.g. `No accounts match "{query}".`) — JS replaces `{query}` with the actual typed value at runtime.

  **Edit 3d — update the inline `<script>` block:**

  The current script in `select-tenant.ftl` is:
  ```javascript
  <script>
    (function () {
      var h1 = document.getElementById('page-title');
      if (h1) { h1.focus(); }
    })();
  </script>
  ```

  **Replace with** (expanded IIFE — preserves h1 focus logic, adds search filter):
  ```javascript
  <script>
    (function () {
      /* Focus management: move focus to page title on load (UX-DR14) */
      var h1 = document.getElementById('page-title');
      if (h1) { h1.focus(); }

      /* Search filter: only wired when the search input exists (> 4 accounts) */
      var searchInput = document.getElementById('wt-account-search');
      if (!searchInput) { return; }

      var list    = document.getElementById('account-list');
      var noMatch = document.getElementById('wt-no-match');
      var items   = list
        ? Array.prototype.slice.call(list.querySelectorAll('[role="listitem"]'))
        : [];

      function filter() {
        var raw   = searchInput.value;
        var query = raw.trim().toLowerCase();
        var visible = 0;

        items.forEach(function (item) {
          /* prefix match: data-name is pre-lowercased in FTL */
          var name  = item.getAttribute('data-name') || '';
          var show  = query === '' || name.indexOf(query) === 0;
          item.style.display = show ? '' : 'none';
          if (show) { visible++; }
        });

        if (noMatch) {
          if (query !== '' && visible === 0) {
            /* Safe: textContent assignment is XSS-free */
            noMatch.textContent = (noMatch.getAttribute('data-template') || '')
              .replace('{query}', raw.trim());
            noMatch.removeAttribute('hidden');
          } else {
            noMatch.setAttribute('hidden', '');
          }
        }
      }

      searchInput.addEventListener('input',  filter);
      searchInput.addEventListener('search', filter); /* handles Safari clear-button */
    })();
  </script>
  ```
  > **Why `item.style.display`:** toggling `display` is simpler and faster than `hidden` attribute for list items; avoids layout shift artifacts from `hidden` + `display:flex` interaction on the list container. `visible = 0` correctly counts only rows passing the current filter.
  >
  > **Why `addEventListener('search', filter)`:** Safari/Chrome render a clear (×) button on `<input type="search">`; clicking it fires a `search` event but not always `input`. Binding both ensures the list resets when the Agent clears the field.
  >
  > **Why `data-name` is pre-lowercased in FTL:** avoids a `.toLowerCase()` call per keystroke per item (minor perf), and aligns casing once at render time rather than repeatedly in JS.

---

- [x] **Task 4: Build and verify** (AC: #1–#3)
  - [x] `mvn package` in the theme repo → BUILD SUCCESS
  - [x] Verify search input is not present for ≤ 4 accounts (FTL conditional, not verifiable by grep — confirm by inspection or local Keycloak run)
  - [x] `grep -n "wt-account-search\|wt-select-account__search\|wt-no-match\|data-name\|data-template" src/main/resources/theme/azguards-whatsapp/login/select-tenant.ftl` → ≥ 5 matches
  - [x] `grep -n "selectAccountSearch\|selectAccountNoMatch" src/main/resources/theme/azguards-whatsapp/login/messages/messages_en.properties` → 3 matches
  - [x] `grep -c "wt-select-account__search\|wt-select-account__no-match" src/main/resources/theme/azguards-whatsapp/login/resources/css/selectTenant.css` → non-zero
  - [x] `grep -i "bootstrap\|cdn.jsdelivr\|font-awesome\|flaticon" src/main/resources/theme/azguards-whatsapp/login/select-tenant.ftl` → 0 matches (no regressions)

---

## Dev Notes

### Working Repository

```
REPO: azguards-keycloak-custom-theme ONLY
  src/main/resources/theme/azguards-whatsapp/login/resources/css/selectTenant.css  MODIFY (append search + no-match styles)
  src/main/resources/theme/azguards-whatsapp/login/messages/messages_en.properties MODIFY (append 3 new keys)
  src/main/resources/theme/azguards-whatsapp/login/select-tenant.ftl               MODIFY (4 surgical edits — NOT a full rewrite)
```

**Do NOT modify:**
- `components.css` — the search field is screen-specific; only add to `selectTenant.css`
- `tokens.css` — no new tokens needed; existing tokens cover all values
- Any Java SPI files — the full tenant list is already in the FreeMarker context from Story 4.2
- `register.ftl`, `login-oauth-grant.ftl`, email templates, admin switcher (AR-OOS for entire project)
- Any other FTL or CSS file not listed above

---

### How Conditional Search Works End-to-End

```
Server side (FTL render):
  data.tenants is a List<TenantsBean.Tenant> already sorted (lastUsed first, then alphabetical)
  <#if data.tenants?size gt 4>           ← renders <input id="wt-account-search"> only for >4
  <div role="listitem" data-name="...">  ← pre-lowercased tenant name; JS reads this attr

Browser (JS, runs after DOMContentLoaded — IIFE):
  getElementById('wt-account-search') → null for ≤4 accounts → returns early (no-op)
  For >4 accounts:
    input/search event → filter() → iterate items → prefix match on data-name
    visible=0 + query!="" → show no-match paragraph with textContent replacement
    visible>0 or query="" → hide no-match paragraph
```

**Prefix match semantics:** `name.indexOf(query) === 0` — the `data-name` value starts with the typed query. Examples:
- query `"ac"` → matches `"acme corp"`, `"acme"` → does NOT match `"pacific"` or `"my acme"`
- query `""` (empty / cleared) → all rows shown (guard: `query === '' || ...`)

This matches FR-AS-5 "prefix-first" as a strict prefix filter (substring search is NOT required). If the product later requires substring search with prefix ordering, the filter function is the only touchpoint.

---

### Current State of `select-tenant.ftl` (what Story 4.2 delivered)

Story 4.2 fully rewrote `select-tenant.ftl`. Key structural facts relevant to this story:

- All rows are `<button type="submit" name="tenant" value="${tenant.id}" class="wt-account-card">` inside `<div role="listitem">` items
- The outer list container is `<div class="wt-select-account__list" role="list" aria-label="...">` — **no `id` yet** (Task 3a adds `id="account-list"`)
- The FTL already has `<#list data.tenants as tenant>` iterating over `TenantsBean.Tenant` objects
- The `tenant.name` is accessed directly; `(tenant.name!"")?trim?lower_case` is the safe pattern for data-name
- The inline `<script>` currently only does `h1.focus()` — Task 3d expands it

---

### Why `data-name` is on the `[role="listitem"]` div, not the `<button>`

The filter JS does `item.style.display = 'none'` to hide a non-matching row. Hiding the outer `[role="listitem"]` div collapses the entire row including spacing. Hiding only the inner `<button>` would leave an empty listitem in the DOM that could confuse screen readers and produce invisible gaps. Always hide the outermost row element.

---

### SR-only Class

Story 1.5 delivered focus-management JS helpers but did not explicitly add a `.sr-only` CSS utility class (it added ARIA patterns). Check `components.css` for `.sr-only` before adding it to `selectTenant.css`.

```bash
grep -n "sr-only" src/main/resources/theme/azguards-whatsapp/login/resources/css/components.css
```

- If matches ≥ 1: the class exists — use it as-is, skip adding to `selectTenant.css`
- If 0 matches: add the `.sr-only` block to `selectTenant.css` as specified in Task 3b

---

### Mandatory Code Patterns (inherited from Epic 2 Retro + Stories 4.1 / 4.2)

1. **`wt-*` class prefix:** All new CSS classes start with `wt-`. The only exception is `.sr-only` which is a well-known utility name.

2. **No hardcoded hex in CSS** — the ONE exception in this story is the SVG data URL search icon (`%2364748B`). Comment it clearly: `/* hex for --wt-muted; SVG data URIs cannot use CSS custom properties */`. Do not introduce any other hardcoded color.

3. **No console.* in JS** — NFR-S-1. The filter function must not emit any `console.log`/`console.debug`/`console.error`.

4. **No Bootstrap or CDN** — the search field is built entirely from design-system CSS tokens. Do not add any external resource links to `select-tenant.ftl`.

5. **`textContent` not `innerHTML`** for the no-match message — `textContent` is XSS-safe; the user-typed query is reflected into the DOM this way.

---

### Token Reference (do not hardcode values)

All tokens are defined in `tokens.css` and accessible as `var(--wt-*)`:

| Token | Value |
|-------|-------|
| `--wt-field-height` | 46px |
| `--wt-radius-field` | 10px |
| `--wt-border-strong` | `#94A3B8` |
| `--wt-surface` | card / input background |
| `--wt-ink` | body text |
| `--wt-muted` | `#64748B` (secondary text) — also the SVG icon color |
| `--wt-primary` | `#0F766E` |
| `--wt-focus-width` | 2px |
| `--wt-focus-ring-halo` | translucent teal (used in `.wt-field__input:focus` in components.css) |
| `--wt-duration` | transition duration |
| `--wt-easing` | transition easing |
| `--wt-space-2` | 8px |
| `--wt-space-3` | 12px |
| `--wt-space-6` | 24px |
| `--wt-text-body-size/line` | 15px / 1.5 |

---

### What Is Out of Scope for Story 4.3

- **Skeleton loading state** — Story 4.4 (separate story)
- **Debouncing the search input** — not required; the list is client-side and typically small (< 100 rows); debounce would add latency without benefit
- **Server-side search** — the full list is already rendered by FTL; no API calls required
- **Substring search with ordering** — the AC says "prefix-first"; this story implements a strict prefix filter which satisfies the requirement; substring logic is deferred if needed
- **Human-readable role mapping** (tenant-admin → "ADMIN") — deferred from Story 4.2
- **`-webkit-search-cancel-button` custom styling** — the default Safari clear button is functional; cosmetic override is out of scope
- **Any Java SPI changes** — the `TenantsBean` / `SelectActiveTenant` SPI is unchanged; all Account data is already available in FTL context

---

### Deferred Items Relevant to This Story

From `deferred-work.md`:
- No deferred items directly affect Story 4.3. The `attribute-context escaping` note (attribute-context `"` encoding for `aria-label`) applies to `data-name` as well — `data-name` uses `((tenant.name!"")?trim)?lower_case` which does not escape HTML, but `data-name` is only read by JS via `.getAttribute('data-name')`, never reflected into HTML attributes, so it is safe for its intended use. A broader attribute-escaping hardening pass remains deferred.

---

### References

- Epics file: `_bmad-output/planning-artifacts/epics.md` — Epic 4, Story 4.3 (FR-AS-5, UX-DR6)
- Previous story: `_bmad-output/implementation-artifacts/4-2-account-picker-rows-with-role-last-used-pin.md`
  - Full `select-tenant.ftl` and `selectTenant.css` current state documented
  - Mandatory code patterns (Zipkin N/A — no Java changes; `wt-*` prefix; no CDN; no hardcoded hex)
- Architecture: `_bmad-output/planning-artifacts/architecture.md` — AR-11 (preserve token/session contracts — not touched by this story)
- `components.css` lines 120–192: `.wt-field` component (reference for search input styling pattern)
- `components.css` lines 365–418: `.wt-skeleton-row` (consumed by Story 4.4, not this one)
- Sprint status: `_bmad-output/implementation-artifacts/sprint-status.yaml`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

- Task 1: Appended `.wt-select-account__search`, `.wt-select-account__search-input` (with SVG icon background), `.wt-select-account__no-match`, and `.sr-only` blocks to `selectTenant.css`. Confirmed `.sr-only` was absent from `components.css` before adding it.
- Task 2: Appended 3 i18n keys (`selectAccountSearchLabel`, `selectAccountSearchPlaceholder`, `selectAccountNoMatch`) to `messages_en.properties`. Used `{query}` token (not `{0}`) since replacement is done by JS, not FTL `msg()`.
- Task 3: Applied 4 surgical edits to `select-tenant.ftl`: (3a) added `id="account-list"` to list div and `data-name` (pre-lowercased) to each listitem; (3b) added `<#if data.tenants?size gt 4>` conditional search field; (3c) added `<p id="wt-no-match" hidden aria-live="polite" data-template="...">` after list; (3d) expanded IIFE to wire search filter with prefix-match logic and Safari clear-button support.
- Task 4: `mvn package` → BUILD SUCCESS. All grep verification checks passed (FTL: 13 matches, messages: 3 matches, CSS: 6 matches, no CDN/Bootstrap regressions).

### File List

**Repo: azguards-keycloak-custom-theme:**
- `src/main/resources/theme/azguards-whatsapp/login/resources/css/selectTenant.css`
- `src/main/resources/theme/azguards-whatsapp/login/messages/messages_en.properties`
- `src/main/resources/theme/azguards-whatsapp/login/select-tenant.ftl`

## Change Log

- 2026-06-17: Story created — ready for dev.
- 2026-06-17: Implementation complete — CSS styles, i18n keys, and FTL edits applied; build verified; status → review.
- 2026-06-17: Code review run (Blind Hunter + Edge Case Hunter + Acceptance Auditor). All 3 ACs PASS; 2 patch findings, 5 deferred. See Review Findings.

## Review Findings

> Reviewed against working-tree diff in `azguards-keycloak-custom-theme` (the diff folds in Story 4.2's uncommitted rewrite + 4.3's additions; 4.3-specific surface assessed against ACs). All 3 Acceptance Criteria PASS; all Mandatory Code Patterns hold.

### Patch (action required)

- [x] [Review][Patch] No-match `data-template` attribute is broken by the literal `"` in the message — AC3 runtime violation — FIXED 2026-06-17 (`?html` applied in theme repo select-tenant.ftl:106) [select-tenant.ftl:~106 / messages_en.properties:14] — `data-template="${msg("selectAccountNoMatch")}"` renders raw as `data-template="No accounts match "{query}"."`; the second `"` terminates the attribute, so `getAttribute('data-template')` returns only `No accounts match ` and the `{query}` echo is silently lost. No `#ftl output_format` / auto-escape configured (verified in theme.properties). Fix: escape the output — `data-template="${msg("selectAccountNoMatch")?html}"` (getAttribute decodes `&quot;` back to `"`, so JS `.replace('{query}', …)` works correctly).

### Deferred (pre-existing / hardening — tracked in deferred-work.md)

- [x] [Review][Defer] Unguarded `kcSanitize(tenant.name)` in `aria-label`/name span (vs guarded `data-name`/initials) [select-tenant.ftl:~70,~86] — deferred, NOT reachable: `TenantEntity.NAME` is `@Column(nullable = false)` (per existing 4.2 deferred note). Defensive consistency only — `kcSanitize(tenant.name!"")`.
- [x] [Review][Defer] Broader attribute-context escaping pass: `data-name`, `aria-label`, and `value="${tenant.id}"` are not `?html`-escaped [select-tenant.ftl] — deferred, pre-existing (spec lines 140/401 explicitly acknowledge and defer; IDs are UUIDs, names domain-required).
- [x] [Review][Defer] `kcSanitize(name)` (displayed) vs `?lower_case` (data-name) can diverge, so a visible row may not match its own searchable token [select-tenant.ftl] — deferred, edge case for names containing markup kcSanitize strips.
- [x] [Review][Defer] `aria-live="polite"` no-match `<p>` toggled from `hidden` may not announce in some screen readers [select-tenant.ftl] — deferred, a11y enhancement.
- [x] [Review][Defer] No `<noscript>` fallback: search box renders with JS disabled but does nothing (rows still clickable via native submit) [select-tenant.ftl] — deferred, graceful-degradation gap.
- [x] [Review][Defer] `String.replace('{query}', …)` substitutes only the first occurrence [select-tenant.ftl] — deferred, only matters if a future/localized message contains `{query}` more than once.
