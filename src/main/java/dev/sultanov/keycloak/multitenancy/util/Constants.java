package dev.sultanov.keycloak.multitenancy.util;

public class Constants {

    public static final String TENANTS_MANAGEMENT_ROLE = "manage-tenants";

    public static final String TENANT_ADMIN_ROLE = "tenant-admin";
    public static final String TENANT_USER_ROLE = "tenant-user";

    public static final String ACTIVE_TENANT_ID_SESSION_NOTE = "active-tenant-id";
    public static final String ACTIVE_TENANT_ATTRIBUTE = "active_tenant";
    public static final String IDENTITY_PROVIDER_SESSION_NOTE = "identity_provider";

    public static final String TOAST_INVITER_NAME_NOTE = "toast.inviter.name";
    public static final String TOAST_TENANT_NAME_NOTE  = "toast.tenant.name";
    public static final String TOAST_TENANT_ID_NOTE    = "toast.tenant.id";

    /** Per-login passkey prompt choice (dismiss | enroll) — survives required-action re-evaluation. */
    public static final String PASSKEY_ENROLLMENT_CHOICE_NOTE = "passkey-enrollment-choice";

    public static final String USER_SERVICE_SYNC_RETRY_ATTR = "user-service-sync-retry-needed";
    public static final String USER_SERVICE_RETRY_LAST_ATTEMPT_ATTR = "user-service-retry-last-attempt";

    // Async user-service sync retry policy (OQ-3 assumptions — single source of truth, see story 5.4).
    public static final int USER_SERVICE_MAX_RETRIES = 3;
    public static final long USER_SERVICE_RETRY_BACKOFF_SECONDS = 30L;

    // Minimum interval between manual /user-service-retry requests per user, to prevent retry-storm amplification.
    public static final long USER_SERVICE_RETRY_COOLDOWN_MS = 60_000L;

    private Constants() {
        throw new AssertionError();
    }
}
