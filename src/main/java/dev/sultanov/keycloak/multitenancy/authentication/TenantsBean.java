package dev.sultanov.keycloak.multitenancy.authentication;

import dev.sultanov.keycloak.multitenancy.model.TenantInvitationModel;
import dev.sultanov.keycloak.multitenancy.model.TenantMembershipModel;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.keycloak.models.UserModel;

public class TenantsBean {

    private final List<Tenant> tenants;

    private TenantsBean(List<Tenant> tenants) {
        this.tenants = tenants;
    }

    public List<Tenant> getTenants() {
        return tenants;
    }

    public static TenantsBean fromInvitations(List<TenantInvitationModel> invitations) {
        List<Tenant> tenants = invitations.stream()
                .filter(invitation -> invitation.getTenant() != null)
                .map(invitation -> new Tenant(
                        invitation.getTenant().getId(),
                        invitation.getTenant().getName(),
                        invitation.getRoles(),
                        invitation.getLogoUrl() != null ? invitation.getLogoUrl() : "",
                        false,
                        computeInviterName(invitation.getInvitedBy())))
                .collect(Collectors.toList());
        return new TenantsBean(tenants);
    }

    private static String computeInviterName(UserModel invitedBy) {
        if (invitedBy == null) {
            return "Someone";
        }
        String firstName = invitedBy.getFirstName();
        String lastName = invitedBy.getLastName();
        if (firstName != null) firstName = firstName.trim();
        if (lastName != null) lastName = lastName.trim();
        boolean hasFirst = firstName != null && !firstName.isEmpty();
        boolean hasLast = lastName != null && !lastName.isEmpty();
        if (hasFirst || hasLast) {
            return (hasFirst ? firstName : "") + (hasFirst && hasLast ? " " : "") + (hasLast ? lastName : "");
        }
        String username = invitedBy.getUsername();
        return (username != null && !username.isBlank()) ? username : "Someone";
    }

    public static TenantsBean fromMembership(List<TenantMembershipModel> memberships, String lastUsedTenantId) {
        List<Tenant> tenants = memberships.stream()
                .filter(membership -> membership.getTenant() != null)
                .map(membership -> {
                    String tenantId = membership.getTenant().getId();
                    boolean lastUsed = tenantId.equals(lastUsedTenantId);
                    return new Tenant(
                            tenantId,
                            membership.getTenant().getName(),
                            membership.getRoles(),
                            Optional.ofNullable(membership.getTenant().getFirstAttribute("logoUrl"))
                                    .orElse(""),
                            lastUsed,
                            "");
                })
                .sorted(Comparator.comparing(Tenant::isLastUsed).reversed()
                        .thenComparing(Tenant::getName, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
        return new TenantsBean(tenants);
    }

    public static class Tenant {
        private final String id;
        private final String name;
        private final Set<String> roles;
        private final String logoUrl;
        private final boolean lastUsed;
        private final String inviterName;

        public Tenant(String id, String name, Set<String> roles, String logoUrl, boolean lastUsed, String inviterName) {
            this.id = id;
            this.name = name;
            this.roles = roles;
            this.logoUrl = logoUrl;
            this.lastUsed = lastUsed;
            this.inviterName = inviterName;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public Set<String> getRoles() {
            return roles;
        }

        public String getLogoUrl() {
            return logoUrl;
        }

        public boolean isLastUsed() {
            return lastUsed;
        }

        public String getInviterName() {
            return inviterName;
        }
    }
}