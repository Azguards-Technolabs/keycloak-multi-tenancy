# KC 26.0.7 → 26.4.6 Upgrade & Rollback

## Compatibility Record

| Component | Version | Notes |
|---|---|---|
| Keycloak runtime (Docker) | 26.4.6 | `quay.io/keycloak/keycloak:26.4.6` |
| Extension JAR (compile target) | KC 26.2.5 SPI | KC 26.x SPIs are binary compatible within major |
| Extension artifact | 26.4.1 | `dev.sultanov:keycloak-multi-tenancy:26.4.1` |
| Previous KC runtime | 26.0.2 (Docker) / 26.0.7 (SPI compile) | |
| Previous extension artifact | 26.0.16 | |
| Validated in staging | _fill in date after staging run_ | |

## Deployment (via Jenkins)

Trigger the `keycloak-identity-service` pipeline with parameter `MT_VERSION=26.4.1`.
The `Dockerfile.template` already references `quay.io/keycloak/keycloak:26.4.6` after this upgrade.

### Staging checklist (manual, post-deployment)

1. KC startup logs — no `ERROR` lines, no `Unable to find template` for FTL files.
2. Admin UI → Realm Settings → Authentication → Required Actions: `Webauthn Register` and `Webauthn Authenticate` are listed.
3. End-to-end flow smoke tests:
   - Password login → account selection (single tenant auto-skip; multi-tenant picker)
   - Invitation review (accept + reject)
   - SSO login via `login-with-sso` authenticator
   - Tenant switch: verify `active_tenant` attribute, `active-tenant-id` session note, `oidc-active-tenant` / `oidc-all-tenants` token claims survive re-mint

## Rollback Procedure (single-node, full replacement model)

1. Schedule maintenance window — auth traffic drops to zero.
2. Stop Keycloak: `docker stop keycloak && docker rm keycloak`.
3. Re-trigger the `keycloak-identity-service` pipeline targeting the previous image tag (e.g. `dev-<previous_build_number>`) with `MT_VERSION=26.0.16`.
   Alternatively, pull and run the archived image directly:
   ```bash
   docker pull asifazguards/keycloak-identity-service:<previous-tag>
   docker run -d --restart unless-stopped --name keycloak \
     --env-file keycloak.env \
     --add-host=host.docker.internal:host-gateway \
     -p 8070:8080 \
     asifazguards/keycloak-identity-service:<previous-tag> \
     start --optimized
   ```
4. Smoke-test: login + tenant selection + invitation flow.
5. End maintenance window.

## Notes

- The Liquibase DB schema changes are additive. Rolling back the KC runtime does not require a DB rollback for the multi-tenancy extension tables.
- Verify KC's own schema compatibility before rolling back if KC 26.4.x ran and committed KC-internal migrations.
- The `KC_SPI_THEME_CACHE_THEMES=false` and related env vars remain unchanged; no theme cache invalidation needed for this upgrade alone.
