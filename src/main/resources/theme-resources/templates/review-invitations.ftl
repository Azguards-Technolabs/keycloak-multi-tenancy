<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true displayInfo=true; section>
  <#if section == "header">
    ${kcSanitize(msg("reviewInvitationsHeader"))?no_esc}
  <#elseif section == "form">
    <div id="kc-form" class="tenant-invitations-container">
      <div id="kc-form-wrapper" class="tenant-invitations-wrapper">
        <p>${msg("reviewInvitationsInfo")}</p>

        <#if messages?has_content>
          <div class="${properties.kcAlertClass!} ${properties.kcAlertErrorClass!}">
            <span>${kcSanitize(messages.error)?no_esc}</span>
          </div>
        </#if>

        <#if data.tenants?has_content>
          <#list data.tenants as tenant>
            <!-- Tenant invitation card -->
            <div class="tenant-invitation-card" data-tenant-id="${tenant.id}">
              <div class="tenant-details">
                <#if tenant.logoUrl?? && tenant.logoUrl?length gt 0>
                  <img src="${tenant.logoUrl}" alt="${kcSanitize(tenant.name)} Logo" class="tenant-logo" />
                <#else>
                  <img src="${url.resourcesPath}/img/default-logo.png" alt="Default Logo" class="tenant-logo" />
                </#if>
                <div class="tenant-info">
                  <p><strong>${kcSanitize(tenant.name)}</strong> has invited you to join their tenant.</p>
                  <#if tenant.roles?has_content>
                    <p>Roles: ${kcSanitize(tenant.roles?join(", "))}</p>
                  <#else>
                    <p>Roles: None</p>
                  </#if>
                  <div class="invitation-status">
                    <span class="status-badge status-pending" data-status="pending">Pending</span>
                  </div>
                </div>
              </div>
              <div class="tenant-actions">
                <button
                  type="button"
                  class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} accept-button"
                  onclick="handleTenantAction('${tenant.id}', 'accept')"
                >
                  Accept
                </button>
                <button
                  type="button"
                  class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!} reject-button"
                  onclick="handleTenantAction('${tenant.id}', 'reject')"
                >
                  Reject
                </button>
              </div>
            </div>
          </#list>

          <!-- Single Proceed button -->
          <form id="proceed-invitations-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <input type="hidden" name="action" value="proceed" />
            <input type="hidden" name="acceptedTenants" id="acceptedTenants" value="" />
            <input type="hidden" name="rejectedTenants" id="rejectedTenants" value="" />
            <div class="tenant-actions proceed-form">
              <button
                type="submit"
                class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} proceed-button"
              >
                Proceed
              </button>
            </div>
          </form>

        <#else>
          <!-- No invitations case -->
          <div class="${properties.kcFormGroupClass!}">
            <p>No pending invitations available.</p>
          </div>
        </#if>
      </div>
    </div>

    <style>
      .tenant-invitations-container {
        background-color: #2c2f33;
        padding: 20px;
        border-radius: 8px;
        color: #ffffff;
        max-width: 600px;
        margin: 0 auto;
        box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
      }

      .tenant-invitations-wrapper p {
        font-size: 0.9em;
        margin-bottom: 15px;
        color: #b9bbbe;
      }

      .tenant-invitation-card {
        background-color: #23272a;
        padding: 15px;
        border-radius: 5px;
        margin-bottom: 15px;
      }

      .tenant-details {
        display: flex;
        align-items: center;
        margin-bottom: 10px;
      }

      .tenant-logo {
        max-width: 60px;
        height: auto;
        margin-right: 15px;
        border-radius: 5px;
        object-fit: contain;
      }

      .tenant-info p {
        margin: 0;
        color: #ffffff;
      }

      .tenant-info p strong {
        color: #00b4d8;
      }

      .tenant-actions {
        display: flex;
        gap: 10px;
        justify-content: flex-end;
      }

      .kc-button {
        padding: 8px 16px;
        border: none;
        border-radius: 4px;
        cursor: pointer;
        font-size: 0.9em;
        transition: background-color 0.3s;
      }

      .kc-button-primary {
        background-color: #00b4d8;
        color: #ffffff;
      }

      .kc-button-primary:hover {
        background-color: #0097c6;
      }

      .kc-button-default {
        background-color: #7289da;
        color: #ffffff;
      }

      .kc-button-default:hover {
        background-color: #5e73c2;
      }

      .kc-button:disabled {
        background-color: #4a4a4a;
        cursor: not-allowed;
      }

      .proceed-form {
        text-align: center;
        margin-top: 20px;
      }

      .proceed-button {
        min-width: 150px;
        padding: 10px 20px !important;
        font-size: 1em !important;
      }

      .invitation-status {
        margin-top: 5px;
      }

      .status-badge {
        display: inline-block;
        padding: 3px 8px;
        border-radius: 3px;
        font-size: 0.8em;
        font-weight: bold;
      }

      .status-accepted {
        background-color: #43b581;
        color: #ffffff;
      }

      .status-rejected {
        background-color: #f04747;
        color: #ffffff;
      }

      .status-pending {
        background-color: #747f8d;
        color: #ffffff;
      }

      @media (max-width: 480px) {
        .tenant-invitations-container {
          padding: 15px;
          max-width: 100%;
        }

        .tenant-details {
          flex-direction: column;
          align-items: flex-start;
        }

        .tenant-logo {
          margin-bottom: 10px;
        }

        .tenant-actions {
          flex-direction: column;
          gap: 5px;
        }

        .kc-button {
          width: 100%;
          text-align: center;
        }
      }
    </style>

    <script>
      // Initialize tenant states
      let tenantStates = {};

      // Load from sessionStorage if available
      if (sessionStorage.getItem('tenantStates')) {
        tenantStates = JSON.parse(sessionStorage.getItem('tenantStates'));
      }

      // Function to handle tenant actions (accept/reject)
      function handleTenantAction(tenantId, action) {
        const card = document.querySelector(`.tenant-invitation-card[data-tenant-id="${tenantId}"]`);
        const statusBadge = card.querySelector('.status-badge');
        const acceptButton = card.querySelector('.accept-button');
        const rejectButton = card.querySelector('.reject-button');

        // Update tenant state
        tenantStates[tenantId] = action;

        // Update UI
        if (action === 'accept') {
          statusBadge.textContent = 'Accepted';
          statusBadge.className = 'status-badge status-accepted';
          acceptButton.disabled = true;
          rejectButton.disabled = false;
        } else if (action === 'reject') {
          statusBadge.textContent = 'Rejected';
          statusBadge.className = 'status-badge status-rejected';
          acceptButton.disabled = false;
          rejectButton.disabled = true;
        }

        // Save to sessionStorage
        sessionStorage.setItem('tenantStates', JSON.stringify(tenantStates));

        // Update hidden inputs for proceed form
        updateProceedForm();
      }

      // Function to update hidden inputs in proceed form
      function updateProceedForm() {
        const acceptedTenants = [];
        const rejectedTenants = [];

        for (const [tenantId, action] of Object.entries(tenantStates)) {
          if (action === 'accept') {
            acceptedTenants.push(tenantId);
          } else if (action === 'reject') {
            rejectedTenants.push(tenantId);
          }
        }

        document.getElementById('acceptedTenants').value = acceptedTenants.join(',');
        document.getElementById('rejectedTenants').value = rejectedTenants.join(',');
      }

      // Initialize UI based on sessionStorage
      document.addEventListener('DOMContentLoaded', () => {
        for (const [tenantId, action] of Object.entries(tenantStates)) {
          const card = document.querySelector(`.tenant-invitation-card[data-tenant-id="${tenantId}"]`);
          if (card) {
            const statusBadge = card.querySelector('.status-badge');
            const acceptButton = card.querySelector('.accept-button');
            const rejectButton = card.querySelector('.reject-button');

            if (action === 'accept') {
              statusBadge.textContent = 'Accepted';
              statusBadge.className = 'status-badge status-accepted';
              acceptButton.disabled = true;
              rejectButton.disabled = false;
            } else if (action === 'reject') {
              statusBadge.textContent = 'Rejected';
              statusBadge.className = 'status-badge status-rejected';
              acceptButton.disabled = false;
              rejectButton.disabled = true;
            }
          }
        }
        updateProceedForm();
      });
    </script>
  </#if>
</@layout.registrationLayout>