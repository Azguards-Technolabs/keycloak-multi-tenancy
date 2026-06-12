---
stepsCompleted: [1, 2, 3, 4, 5, 6]
inputDocuments: []
workflowType: 'research'
lastStep: 1
research_type: 'market'
research_topic: 'Login & Onboarding UX for Keycloak-based B2B SaaS'
research_goals: 'Identify how leading SaaS and identity products design login + onboarding flows, what users expect, and which patterns to adopt given a Keycloak backend — to redesign our login/onboarding for a more user-friendly experience.'
user_name: 'Asif'
date: '2026-06-11'
web_research_enabled: true
source_verification: true
---

# Research Report: market

**Date:** 2026-06-11
**Author:** Asif
**Research Type:** market

---

## Research Overview

This report investigates how to redesign the login and onboarding flows of a brownfield, Keycloak-based multi-tenant B2B SaaS (with WhatsApp integration) for a more user-friendly experience. It draws on current (2025–2026) web sources covering user behavior, login/onboarding pain points, flow-design best practices, the competitive identity landscape, and current Keycloak capabilities. All four target segments are in scope — end users, tenant admins, self-serve signups, and WhatsApp-channel users — with reducing friction/drop-off as the primary lens.

The central finding: users now treat authentication as pure friction (87% have abandoned a signup over login difficulty; 92% abandon rather than reset a password), the market has converged on a clear playbook (passwordless-first login, ≤3-field signup, social/SSO primary, 3–5 step role-branched onboarding, invites-as-activation, visible progress), and — critically — our backend is **already single-realm multi-tenant** via a custom Keycloak extension (`anarsultanov/keycloak-multi-tenancy`, on Keycloak 26.0.7), so the realm-per-tenant pain points don't apply and users have one identity. The redesign is therefore primarily **(a) a Keycloak upgrade to 26.4+ for native passkeys, (b) login theme/flow work, and (c) UX evolution of the extension's existing tenant-selection and invitation flows** — not a re-architecture onto Organizations — with WhatsApp-native auth as a differentiator.

See the **Strategic Synthesis & Recommendations** section below for the full executive summary, prioritized recommendations, phased roadmap, and success metrics.

---

<!-- Content will be appended sequentially through research workflow steps -->

# Market Research: Login & Onboarding UX for Keycloak-based B2B SaaS

## Research Initialization

### Research Understanding Confirmed

**Topic**: Login & Onboarding UX for Keycloak-based B2B SaaS
**Goals**: Identify how leading SaaS and identity products design login + onboarding flows, what users expect, and which patterns to adopt given a Keycloak backend — to redesign our login/onboarding for a more user-friendly experience.
**Research Type**: Market Research
**Date**: 2026-06-11

### Research Scope

**Market Analysis Focus Areas:**

- Login UX patterns: passwordless, magic links, social/SSO login, passkeys/WebAuthn, MFA UX
- Onboarding patterns: progressive onboarding, first-run experience, time-to-value, empty states, guided setup
- Multi-tenant B2B specifics: org/workspace selection, tenant discovery, invite flows, SSO-per-tenant
- Customer expectations and behavior: friction points, drop-off causes, conversion benchmarks
- Competitive landscape: how leading SaaS + identity products (and Keycloak alternatives) design these flows
- Keycloak feasibility: which patterns are achievable via themes, authentication flows, SPIs, and Account Console

**Primary User Segments (all in scope):**

- End users (tenant members) — fast, low-friction repeat login
- Tenant admins — tenant setup, SSO config, team invites
- New signups / self-serve — activation and time-to-value
- WhatsApp-channel users — auth/onboarding via the WhatsApp integration

**Primary optimization lens:** Reduce friction / drop-off — while flagging time-to-value, security↔UX balance, and Keycloak feasibility throughout.

**Research Methodology:**

- Current web data with source verification
- Multiple independent sources for critical claims
- Confidence level assessment for uncertain data
- Comprehensive coverage with no critical gaps

### Next Steps

**Research Workflow:**

1. ✅ Initialization and scope setting (current step)
2. Customer Insights and Behavior Analysis
3. Competitive Landscape Analysis
4. Strategic Synthesis and Recommendations

**Research Status**: Scope confirmed by user on 2026-06-11, ready to proceed with detailed market analysis

---

## Customer Behavior and Segments

### Login & Authentication Behavior Patterns

Users treat authentication as pure friction — a tax to pay before value, not part of the value. The data is stark:

- **87% of Americans have abandoned a sign-up or purchase due to login difficulties** (Frontegg, Apr 2025).
- **92% of users will abandon a website rather than reset a forgotten password**; **78% forget a password at least once a month**.
- Password-entry steps show **15–25% abandonment at that single step**, and **25% abandon account creation when forced to set a password**.
- **55%** have abandoned an account or created a *new* one just to dodge a password reset.
- Customers with regular password difficulty are **3–4× more likely to churn within 6 months**.

_Behavior Drivers: avoidance of memory burden, impatience with multi-step flows, low tolerance for reset loops._
_Interaction Preferences: instant re-entry; users expect to "just be logged in" on return._
_Decision Habits: friction at the auth gate is attributed to the product, not to security._
_Sources: [Frontegg study via SmallBizTrends](https://smallbiztrends.com/frontegg-login-study-password-reset-abandonment/), [MojoAuth — Authentication Friction & Conversion](https://mojoauth.com/blog/how-authentication-friction-affects-conversion-rates-the-data-behind-frictionless-login), [Corbado — Login Friction Kills Conversion](https://www.corbado.com/blog/login-friction-kills-conversion)_

### Onboarding Behavior Patterns

The first session decides retention. The behavioral window is measured in days, not weeks:

- **40–60% of users drop off after signup** with inadequate onboarding; **75% abandon within the first week**.
- **72% abandon during onboarding if it requires too many steps** — step reduction is the single highest-leverage fix.
- Users not engaging within the **first 3 days have a ~90% chance of churning**; **>98% churn within two weeks if they never hit a value milestone**.
- Users who **complete an onboarding flow are 5× more likely to convert**.

_Behavior Drivers: desire for immediate, visible value; intolerance of setup busywork._
_Interaction Preferences: guided but skippable; progress visible; defer non-essential config._
_Decision Habits: a fast "aha" in session one predicts long-term use._
_Sources: [SaaSFactor — Science of SaaS Onboarding](https://www.saasfactor.co/blogs/the-science-of-saas-onboarding-a-comprehensive-framework-for-reducing-friction-improving-activation-and-preventing-churn), [Appcues — Onboarding Metrics](https://www.appcues.com/blog/user-onboarding-metrics-and-kpis), [Shno — SaaS Onboarding Statistics 2026](https://www.shno.co/marketing-statistics/saas-onboarding-statistics)_

### Authentication Method Preferences

User preference has decisively shifted to passwordless:

- **94.3% of users prefer passwordless methods over passwords**; **69% now hold at least one passkey**.
- Passwordless now accounts for **73% of all authentications** (+64% YoY).
- **Passkeys deliver a 93% login success rate vs 63% for traditional auth**; biometric unlock takes 3–5 seconds.
- **Magic links remain the most-deployed method (41% of implementations)** for universal compatibility; **passkey adoption surged 412% in 2025** (fastest-growing).
- **89% of new enterprise deployments choose passwordless-first**; 45% of orgs have deployed passkeys.

_Values: convenience and "it just works" outweigh perceived security trade-offs (54% find passkeys more convenient, 53% more secure)._
_Source: [Authsignal — Passkeys went mainstream 2025](https://www.authsignal.com/blog/articles/passwordless-authentication-in-2025-the-year-passkeys-went-mainstream), [MojoAuth — State of Passwordless 2026](https://mojoauth.com/data-and-research-reports/state-of-passwordless-2026/), [Help Net Security — Passkey adoption 2025](https://www.helpnetsecurity.com/2025/10/31/passkey-adoption-trends-2025/), [Descope — Passwordless Trends](https://www.descope.com/blog/post/passwordless-authentication-trends)_

### Customer Segment Profiles (mapped to our four segments)

- **End users (tenant members):** Highest sensitivity to repeat-login friction. Want session persistence, biometric/passkey re-entry, zero password recall. Churn risk from reset loops.
- **Tenant admins:** Tolerate more steps *if* setup feels like progress toward go-live. Care about SSO config, team invites, and a clear "you're set up" milestone. Time-to-value = "my team can log in."
- **New signups / self-serve:** Most fragile cohort — the 72%-abandon-on-too-many-steps and 90%-churn-by-day-3 dynamics hit hardest here. Need minimal-field signup and a sub-15-minute first value.
- **WhatsApp-channel users:** Mobile-first, expect OTP/magic-link style flows native to the messaging context; high abandonment if redirected to heavy web forms.

### Activation & Time-to-Value Benchmarks

- Average B2B SaaS **activation rate is just 37.5%** — two-thirds never reach core value.
- **Time-to-first-value should be <15 minutes**; best PLG products hit 5–15 min; onboarding should deliver first value within 14 days.
- **Day-7 return ≥7% = top-quartile activation**; ~69% of strong day-7 products also retain strongly at 3 months.
- 85% of customers reaching value within 10 days continue long-term (Bain).

_Sources: [AgileGrowthLabs — Activation Benchmarks 2025](https://www.agilegrowthlabs.com/blog/user-activation-rate-benchmarks-2025/), [DigitalApplied — Time to Value 2026 Framework](https://www.digitalapplied.com/blog/customer-onboarding-time-to-value-2026-saas-metrics-framework), [ProductLed — State of B2B SaaS 2025](https://productled.com/blog/state-of-b2b-saas-2025-report)_

### Confidence & Gaps

- **High confidence:** directional pull toward passwordless and step-reduction (consistent across many independent sources).
- **Medium confidence:** specific abandonment percentages (vendor-published surveys — directionally reliable, exact figures vary by methodology).
- **Gap to fill in later steps:** multi-tenant-specific behavior (tenant discovery, invite acceptance) and messaging-channel auth behavior are under-reported in general benchmarks; will address via competitive analysis.

---

## Customer Pain Points and Needs

### Login & Authentication Pain Points

_Primary Frustrations:_
- **Password reset loops** — the single biggest abandonment trigger (92% abandon rather than reset).
- **MFA fatigue** — constant OTP prompts, app-switching, slow SMS; recovery flows when a device is lost/changed are "often confusing" and become a hard barrier.
- **Forced password creation** during signup (25% abandon at that step).

_Usage Barriers:_ device changes break 2FA; no biometric/passkey re-entry forces full re-auth each session.
_Frequency:_ 78% hit a forgotten-password event monthly — so reset friction is a recurring, not edge-case, cost.
_Source: [Silverfort — MFA Fatigue](https://www.silverfort.com/glossary/mfa-fatigue/), [WorkOS — UX best practices for MFA](https://workos.com/blog/ux-best-practices-for-mfa), [Corbado — Login Friction](https://www.corbado.com/blog/login-friction-kills-conversion)_

### Multi-Tenant–Specific Pain Points (high relevance to your product)

_Tenant Discovery:_ users arrive without telling the system which org they want — the app must infer it from email domain, invite link, subdomain/custom domain, or an **org-picker after auth**. A clumsy picker is a recurring friction point for multi-org users (consultants, agencies, cross-workspace employees).

_Active-Tenant Selection & Switching:_ users in multiple tenants need an explicit, fast "active tenant" switch; switching should mint a new token, not reuse the old — done poorly it causes confusion and broken sessions.

_Per-Tenant Auth Policy:_ one tenant may mandate SSO+MFA while another allows email+password — auth config must be tenant-owned, not global. Hard to express cleanly in UX.

_Workspace-Provisioning Latency:_ creating a tenant schema adds seconds-to-minutes to signup; without a "getting your workspace ready" state, users perceive a hang.
_Source: [WorkOS — Multi-tenant architecture guide](https://workos.com/blog/developers-guide-saas-multi-tenant-architecture), [Descope — Auth for multi-tenant B2B SaaS](https://www.descope.com/blog/post/auth-multi-tenant-b2b-saas), [Clerk — Multitenant SaaS architecture](https://clerk.com/blog/how-to-design-multitenant-saas-architecture)_

### Keycloak-Specific Constraints & Pain Points (your backend reality)

_Realm-per-tenant degrades at scale:_ Keycloak **performance degrades with hundreds of realms**; a user in multiple realms gets **separate logins/passwords** (two distinct identities for the same email), requiring inter-realm IdP federation that adds UX friction.

_Management overhead:_ updating theming/roles/settings across many realms is tedious and error-prone.

_Theming technology limits:_ login themes use **FreeMarker — "very old technology"** that makes advanced customization difficult. Default approach is per-realm, but you can theme **per-client** (link each client to an organization) to get per-tenant login screens without realm-per-tenant.

_Implication (general market):_ For Keycloak shops, either the **Organizations** feature or a **custom single-realm extension** (our approach) avoids realm-per-tenant pain. Per-client theming gives branded login in both cases. See "Keycloak Capability Reality (OUR ACTUAL BACKEND)" below for our specifics.
_Source: [PhaseTwo — Multi-tenancy options in Keycloak](https://phasetwo.io/blog/multi-tenancy-options-keycloak/), [Keycloak — Working with themes](https://www.keycloak.org/ui-customization/themes), [Cloud-IAM — Multitenant on Keycloak](https://documentation.cloud-iam.com/how-to-guides/multitenant-with-keycloak.html)_

### Onboarding & Team-Invite Pain Points

_Invited users get the wrong experience:_ they're dropped into an already-configured workspace but receive cold-signup "welcome" flows that don't fit. Invited-user onboarding must be **context-aware**, not generic.

_Sign-up vs log-in confusion:_ on clicking an invite link, users are unsure whether to register or log in — a top conversion leak. Acceptance flow must handle both existing and new users seamlessly.

_Acceptance-rate benchmark:_ healthy team-invite acceptance is **50–70%**; below 50% signals a weak invite email and/or high-friction acceptance flow. Inviter context + clear CTA + 2–3 well-timed reminders (e.g., day 3 and day 6 of a 7-day expiry) lift it.
_Source: [Userpilot — Onboard invited users](https://userpilot.com/blog/onboard-invited-users-saas/), [WorkOS — B2B SaaS onboarding](https://workos.com/blog/b2b-saas-onboarding-organizations-users), [Auth0 — B2B SaaS onboarding strategies](https://auth0.com/blog/user-onboarding-strategies-b2b-saas/)_

### Pain Point Prioritization (impact × opportunity)

| Priority | Pain Point | Why |
|---|---|---|
| 🔴 High | Password reset loops & forced passwords | Largest single abandonment driver; passwordless directly solves it |
| 🔴 High | MFA fatigue & broken recovery | Recurring friction; adaptive/step-up MFA + passkeys solve it |
| 🔴 High | Invite sign-up/login confusion + cold onboarding for invitees | Direct hit to admin & self-serve activation; cheap to fix |
| 🟠 Medium | Tenant discovery & active-tenant switching | Affects multi-org users; needs deliberate org-picker UX |
| 🟠 Medium | Tenant-selection & theming UX in our extension | N/A as architecture (we're already single-realm); work is per-client theming + evolving the extension's required actions |
| 🟡 Lower | Workspace-provisioning latency | Solved with async provisioning + "preparing workspace" state |

### Emotional Impact & Retention Risk

Friction at the auth gate and first session is attributed to *the product*, not to security. Loyalty risk is real: password-difficulty users churn at 3–4× the rate, and >98% of users who never hit a value milestone in two weeks are gone. The emotional arc — frustration → workaround (new account) → abandonment — repeats monthly for the same users.
_Source: [MojoAuth — Password Fatigue & Retention](https://mojoauth.com/white-papers/password-fatigue-customer-retention/)_

---

## Customer Journey & Flow-Design Best Practices

### The Login/Onboarding Journey (mapped to stages)

**Stage 1 — Arrival / Login screen.** Clean, single-focus layout; brand subtle, not competing. 2026 B2B baseline: **passwordless and accessible by default** — primary CTA "Sign in with your organization" (SSO), passkeys as a peer option, password/email-OTP as fallback. Email-first flows route the user to the right method.

**Stage 2 — Signup (self-serve).** Ask only what the first session needs. **3-field forms convert ~25%; 6+ fields drop to ~15%.** Cutting 4→3 fields can lift conversion ~50%; removing "confirm password" (use unmask toggle) lifted one case **+56.3%**. **Social login as the primary option → ~20% signup boost.** Defer email verification — land the user in the product immediately, verify async.

**Stage 3 — Tenant context.** Infer org from email domain / invite link / subdomain; show an explicit org-picker only for multi-tenant users. Provision the workspace asynchronously behind a "getting your workspace ready" state.

**Stage 4 — First-run / activation.** Get to **first value in 2–5 minutes**; full onboarding **5–15 minutes**. Limit to **3–5 essential steps** — each extra step adds **10–20% abandonment risk**. Use a welcome survey (2–3 questions) to branch by role/goal — **role-based flows lift activation 30–50%**.

**Stage 5 — Guided progress.** Checklists + progress bars give a roadmap and exploit the Zeigarnik effect; **progress indicators raise completion 30–50%**. Guided empty states beat blank screens. Celebrate milestones to reinforce momentum.

_Source: [Authgear — Login/Signup UX 2025](https://www.authgear.com/post/login-signup-ux-guide/), [Eleken — Sign-up flows that convert](https://www.eleken.co/blog-posts/sign-up-flow), [ProcreatorDesign — SaaS onboarding checklist](https://procreator.design/blog/saas-onboarding-design-ultimate-checklist/), [Appcues — Onboarding screens](https://www.appcues.com/blog/saas-onboarding-screens)_

### Decision Factors — what makes users commit vs. bail

_Primary drivers (commit):_ instant access (no password recall), visible progress, fast first value, choice of familiar auth method (Google/SSO/passkey).
_Friction drivers (bail):_ forced password creation, too many form fields, too many onboarding steps, sign-up/login ambiguity on invites, dead blank screens.
_Cognitive limit:_ working memory handles only **3–4 chunks** — progressive disclosure is not optional.
_Source: [Lollypop — SaaS onboarding UX](https://lollypop.design/blog/2025/may/saas-onboarding-ux-design/), [Userpilot — Signup flow UX](https://userpilot.com/blog/saas-signup-flow/)_

### Touchpoints & Channels (incl. WhatsApp relevance)

_Digital touchpoints:_ login page, OAuth/SSO redirect, magic-link email, OTP (SMS/WhatsApp), in-app checklist, invite email.
_For WhatsApp users:_ OTP/magic-link patterns native to messaging fit better than redirecting to heavy web forms; keep the auth handshake inside the channel where possible.
_Trusted sources of confidence:_ recognizable SSO providers, branded (per-tenant) login screens, clear inviter context on invites.

### Journey Optimizations (actionable levers)

| Lever | Expected effect |
|---|---|
| Social/SSO as primary login | ~20% signup lift |
| Reduce signup to 3 fields | up to ~50% conversion lift |
| Remove confirm-password (unmask toggle) | +56% in one case study |
| Passwordless (passkeys/magic link) | eliminates reset loops; 93% vs 63% login success |
| Defer email verification | user reaches value in session one |
| 3–5 step onboarding + progress bar | 30–50% higher completion |
| Role-based branching | 30–50% higher activation |
| Async provisioning + "preparing" state | removes perceived hang |

_Confidence: High on direction; specific % are vendor/case-study figures — treat as directional._

---

## Competitive Landscape

### Key Market Players (auth/identity UX leaders)

| Player | Login/Onboarding UX strengths | Relevance to us |
|---|---|---|
| **WorkOS (AuthKit)** | Hosted, customizable auth UI; React widgets for **org switching**, profile, session, security settings; free to 1M MAU; SSO/SCIM as paid connections. B2B-first. | Gold standard for **multi-tenant org-switching UX** — model our tenant-picker on AuthKit. |
| **Clerk** | Fastest setup; polished **drop-in UI components**, org management, multi-tenant B2B. Weak on enterprise IdP/SCIM. | Reference for **component polish & self-serve flows**; less for enterprise SSO. |
| **Auth0** | 20+ IdPs, mature, strong security; but **dated UI**, customization needs CSS overrides, Lock widget stagnant. | Cautionary tale — feature-rich but UX feels old; don't replicate. |
| **Stytch** | Passwordless-native: magic links, OTP, WebAuthn, biometrics + fraud detection. (Acquired by Twilio 2025.) | Reference for **passwordless-first flows** and fraud-aware auth. |

_Source: [WorkOS — vs Auth0 vs Clerk](https://workos.com/blog/workos-vs-auth0-vs-clerk), [LoginRadius — Clerk alternatives](https://www.loginradius.com/blog/identity/top-clerk-alternatives), [MojoAuth — Stytch alternatives](https://mojoauth.com/blog/stytch-alternatives-for-passwordless-authentication)_

### Product Onboarding Exemplars (what to steal)

- **Slack** — compresses identity + workspace setup + config into one **linear flow with sensible defaults** (pre-made channels, Slackbot). **Invitations are a core activation event**: composing a message prompts you to invite teammates. Onboarding tips delivered *through using the product*, not modal tours.
- **Notion** — **jobs-based questionnaire** ("what are you trying to do?") branches into role-fit setups, **pre-populated with example content** so value is visible immediately (progressive disclosure of advanced features).
- **Linear** — **team-scoped roles** (team owners) so delegation doesn't require workspace admin — clean multi-tenant permission model. Invitations again the central activation event.

_Common thread: initial simplicity → progressive complexity, with **invites as the activation moment** and **sensible defaults over upfront config**._
_Source: [UserGuiding — Slack onboarding teardown](https://userguiding.com/blog/slack-user-onboarding-teardown), [Venue — PLG onboarding playbook](https://venue.cloud/news/insights/from-signup-to-sticky-slack-notion-canva-s-plg-onboarding-playbook), [WorkOS — Slack/Notion/Linear permissions](https://workos.com/blog/multi-tenant-permissions-slack-notion-linear)_

### 🔑 Keycloak Capability Reality (OUR ACTUAL BACKEND)

**Our architecture:** the `anarsultanov/keycloak-multi-tenancy` **custom extension (SPI)** implementing multi-tenancy **inside a single realm** — *not* Keycloak Organizations and *not* realm-per-tenant. Running on **Keycloak 26.0.7** (extension v26.0.16). Tenancy is composed of: a *Create tenant* required action at registration, a tenant **Invitations API** + *Review invitations* required action, **active-tenant selection on login** (required action), customizable tenant attributes, and tenant-specific IDP authenticators.

What this means for the UX plan:

- ✅ **Realm-per-tenant pain points DO NOT apply to us.** We already use a single realm, so users have **one identity** (no per-realm duplicate accounts, no inter-realm IdP federation friction, no hundreds-of-realms performance hit). This is a strong starting position.
- ⚠️ **We are NOT on Keycloak Organizations.** The Organizations-based recommendations (per-org theming via org-linked clients, org membership model) are *not* our path — we have our own tenant model. Adopting Organizations would be a re-architecture and is **not recommended** given a working custom extension; instead, evolve the extension's UX.
- 🎯 **"Active tenant selection on login" IS our tenant-picker.** This existing required action is the prime UX surface to redesign (model the experience on WorkOS AuthKit's org switcher).
- 🎯 **Invitations already exist** (API + required action) — the work is UX/flow polish (Slack-style invite-as-activation, context-aware invitee onboarding), not building it from scratch.
- ⚠️ **Passkeys are NOT native on 26.0.7.** Native passkeys landed in **Keycloak 26.4 (Sept 2025)**. To get them without custom work, **upgrade Keycloak (and align the extension) to 26.4+**. The extension "aims to support the most recent Keycloak version," so an upgrade path exists.
- **Per-tenant branded login** without Organizations: theme **per-client** (clients still exist per app/tenant) or extend the login theme/flow to read the active-tenant context the extension already resolves.

**Implication:** passwordless-first login, branded per-tenant screens, and a polished tenant-picker are all achievable — but via **(a) a Keycloak upgrade to 26.4+ for native passkeys, (b) theme/flow work, and (c) UX evolution of the existing custom extension's required actions**, NOT by adopting Organizations.
_Source: [anarsultanov/keycloak-multi-tenancy (our extension)](https://github.com/anarsultanov/keycloak-multi-tenancy), [Keycloak — Passkeys in 26.4](https://www.keycloak.org/2025/09/passkeys-support-26-4), [Keycloak — Working with themes (per-client)](https://www.keycloak.org/ui-customization/themes)_

### Differentiation Opportunities (for us)

1. **Passwordless-first on Keycloak** — most Keycloak-based products still ship password-first; leading with passkeys + magic link is a visible differentiator and removes the #1 abandonment cause.
2. **Branded per-tenant login** — via per-client theming, or by extending the login theme to read the active-tenant context our extension already resolves. White-label screens that feel native to each tenant (no Organizations dependency).
3. **Invite-as-activation** — Slack-style: make inviting teammates a core, in-flow action, with context-aware invitee onboarding (not cold welcome).
4. **WhatsApp-native auth** — OTP/magic-link delivered in-channel for WhatsApp users is a genuine edge given your existing WhatsApp integration; few competitors do messaging-native auth well.

### Competitive Threats / Risks

- BaaS players (WorkOS, Clerk, Stytch) set a **very high UX bar** — users now expect that polish. A clunky Keycloak login reads as dated by comparison.
- Keycloak's **FreeMarker theming** still limits how far the stock login can be pushed; advanced UX may need a custom front-end calling Keycloak via standard OIDC + the newer theming options.
- We rely on a **third-party custom extension** (`anarsultanov/keycloak-multi-tenancy`) — Keycloak upgrades (e.g. → 26.4+ for passkeys) must be matched by a compatible extension version; budget for that coupling.
- UX improvements to tenant-selection/invitations mean **modifying/extending the extension's required actions & flows**, not just front-end theming — factor in Java/SPI work.

### Opportunities Summary

The market has converged on a clear playbook (passwordless-first, ≤3-field signup, social/SSO primary, 3–5 step role-branched onboarding, invites-as-activation, progress UI). Our single-realm custom extension can deliver most of it. The win is **adopting the playbook on top of our existing extension + a Keycloak 26.4+ upgrade for native passkeys**, with WhatsApp-native auth as a differentiator.

---

## Strategic Synthesis & Recommendations

### Executive Summary

Users treat login and onboarding as friction to be minimized, and the bar has been set by best-in-class B2B identity products (WorkOS, Clerk, Stytch) and PLG exemplars (Slack, Notion, Linear). The data is unambiguous: forced passwords and multi-step flows are the dominant abandonment causes, passwordless is now the user-preferred default, and the first 3 days (ideally first 5–15 minutes) determine retention. For our product, the strategically important point is that **we already have single-realm multi-tenancy** via the `anarsultanov/keycloak-multi-tenancy` extension (one identity per user, no realm-per-tenant penalty) — so this is a focused UX + configuration + targeted-extension-evolution project, not a re-architecture. The main platform dependency is a **Keycloak 26.0.7 → 26.4+ upgrade** to get native passkeys, with WhatsApp-native authentication as a credible differentiator few competitors execute well.

### Strategic Recommendations (prioritized)

**1. Go passwordless-first (highest ROI).** Make passkeys + magic link the primary login, password a fallback. Directly removes the #1 abandonment cause (reset loops) and matches the 94% user preference. Native passkeys require **Keycloak 26.4+** (we're on 26.0.7) — plan the upgrade, then enable via WebAuthn Passwordless Policy. Magic link can be added independently.

**2. Keep the custom extension; do NOT migrate to Organizations.** We already have single-realm multi-tenancy (one identity per user) via the `anarsultanov/keycloak-multi-tenancy` extension — the realm-per-tenant pain points don't apply. Invest in **evolving the extension's UX** (especially the active-tenant-selection required action → a polished tenant-picker modeled on WorkOS AuthKit) rather than re-architecting onto Organizations. **Upgrade Keycloak 26.0.7 → 26.4+** to unlock native passkeys (see #1).

**3. Strip the signup form.** ≤3 fields, social/SSO as the *primary* button (not secondary), defer email verification so users land in-product immediately. Expected: meaningful signup-conversion lift.

**4. Redesign first-run as 3–5 steps with role branching.** Add a 2–3 question "what are you here to do?" welcome survey (Notion-style), branch flows by role (admin vs member vs self-serve), pre-populate example content, show a progress checklist. Target first value in <15 min.

**5. Make invites an activation event (Slack-style).** Context-aware invitee onboarding (don't send cold-welcome flows to people joining a live workspace); fix sign-up/login ambiguity on invite links; inviter context + 2–3 timed reminders. Target 50–70% invite acceptance.

**6. Build WhatsApp-native auth (differentiator).** Deliver OTP/magic-link inside the WhatsApp channel for WhatsApp-origin users rather than bouncing them to heavy web forms.

**7. Add adaptive/step-up MFA.** Avoid MFA fatigue by only stepping up on risk signals; ensure a clear, tested device-loss recovery flow.

### Phased Implementation Roadmap

- **Phase 0 — Ground truth (1–2 wks):** Document the *current* login/onboarding flow and Keycloak version/topology (run BMad `Document Project` / `Generate Project Context`). Instrument funnel analytics to get a friction baseline.
- **Phase 1 — Quick wins (2–4 wks):** Strip signup fields, social/SSO primary, deferred verification, remove confirm-password, clearer error states, progress bar on existing onboarding. Mostly theme/front-end.
- **Phase 2 — Passwordless + branding (4–8 wks):** Upgrade Keycloak to 26.4+ (align extension version), enable passkeys, add magic link, roll out per-client theming for branded login.
- **Phase 3 — Multi-tenant UX (parallel/after):** Redesign the extension's **active-tenant-selection** required action into a polished tenant-picker/switcher (model on WorkOS AuthKit); async provisioning + "preparing workspace" state. (Stays on the custom extension — no Organizations migration.)
- **Phase 4 — Onboarding & invites (4–6 wks):** Role-branched first-run, welcome survey, example content, Slack-style invite-as-activation, context-aware invitee onboarding.
- **Phase 5 — Differentiator + hardening:** WhatsApp-native auth; adaptive/step-up MFA + recovery flows.

### Success Metrics / KPIs

- **Login success rate** (target ≥90%, passkey-led) · **password-reset rate** (↓) · **auth-step abandonment** (↓ from 15–25%)
- **Signup conversion** (target 3-field ~25%+) · **activation rate** (move toward/above 37.5% median) · **time-to-first-value** (<15 min)
- **Day-7 return** (≥7% = top quartile) · **invite acceptance** (50–70%) · **onboarding completion** (↑ with progress bar)

### Risks & Mitigation

- **FreeMarker theming limits** advanced login UX → consider a custom front-end on standard OIDC for screens needing rich UX; use stock theming where sufficient.
- **Keycloak 26.0.7 → 26.4+ upgrade** must be matched by a compatible extension version → validate the extension against the target Keycloak in staging before rollout; budget for the version coupling.
- **Passkey edge cases / device loss** → always ship magic-link/OTP fallback + tested recovery.
- **Vendor-sourced stats are directional** → validate against your own funnel analytics (Phase 0) before over-indexing on specific percentages.

### Recommended Next Steps in BMad

1. **`Document Project` / `Generate Project Context`** — capture the current flow + Keycloak topology so design is grounded (Phase 0).
2. **`bmad-ux` (Create UX)** — turn these findings into concrete UX specs/wireframes for the new flows.
3. **`bmad-prd` (Create PRD)** — formalize scope, then Architecture → Epics/Stories for implementation.

---

**Market Research Completion Date:** 2026-06-11
**Source Verification:** All claims cited with current (2025–2026) sources
**Confidence Level:** High on direction; specific percentages are vendor/case-study figures — validate against internal analytics.




