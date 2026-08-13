package dev.sultanov.keycloak.multitenancy.resource.representation;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

@Schema
@Data
public class TenantRepresentation {

    @Schema(readOnly = true)
    private String id;

    @Schema(required = true)
    private String name;

    @Schema(readOnly = true)
    private String realm;

    @Schema(description = "Mobile number of the tenant")
    private String mobileNumber;

    // Add these new fields
    @Schema(description = "Country code of the tenant's mobile number")
    private String countryCode;

    @Schema(description = "Status of the tenant (e.g., ACTIVE, INACTIVE, PENDING)")
    private String status;

    @Schema(description = "Attributes of the tenant")
    private Map<String, List<String>> attributes = new HashMap<>();

    @Schema(description = "Optional: grant tenant-admin membership to this user ID instead of the caller. "
            + "Only honored for callers holding the tenants-manager role; trusted service callers "
            + "(e.g. the application backend creating a tenant on behalf of an end user) use this "
            + "so the real user — not the calling service account — becomes the tenant owner.")
    private String ownerUserId;
}