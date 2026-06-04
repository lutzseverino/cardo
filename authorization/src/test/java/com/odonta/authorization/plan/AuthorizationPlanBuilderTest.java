package com.odonta.authorization.plan;

import static com.odonta.authorization.plan.AuthorizationPlanBuilder.authorizationPlan;
import static org.assertj.core.api.Assertions.assertThat;

import com.odonta.authorization.access.AccessProfileGrantProjection;
import com.odonta.authorization.resource.AuthorizationResource;
import com.odonta.authorization.resource.AuthorizationResourceType;
import com.odonta.authorization.sync.AssignAuthorizationAuthorities;
import com.odonta.authorization.sync.AuthorizationPlan;
import com.odonta.authorization.sync.AuthorizationSyncOperation;
import com.odonta.authorization.sync.GrantAuthorizationResourceActions;
import com.odonta.authorization.sync.ProvisionAuthorizationResource;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthorizationPlanBuilderTest {

  private static final AuthorizationResourceType CLINIC_RESOURCE =
      AuthorizationResourceType.of("clinic", "clinic", List.of("read", "write"));

  @Test
  void provisionsAndGrantsFullResourceAccess() {
    AuthorizationResource resource =
        new AuthorizationResource(
            "clinic", "clinic:clinic:123", "clinic:clinic", null, List.of("read", "write"));

    AuthorizationPlan plan =
        authorizationPlan()
            .grantFullResourceAccess("owner-registration", "subject-1", resource)
            .build();

    assertThat(plan.operations()).hasSize(2);
    ProvisionAuthorizationResource provision =
        (ProvisionAuthorizationResource) plan.operations().getFirst();
    GrantAuthorizationResourceActions grant =
        (GrantAuthorizationResourceActions) plan.operations().get(1);

    assertThat(provision.uniqueKey()).isEqualTo("resource:clinic:clinic:clinic:123");
    assertThat(grant.uniqueKey())
        .isEqualTo("grant:owner-registration:subject-1:clinic:clinic:clinic:123:read,write");
    assertThat(grant.actions()).containsExactly("read", "write");
  }

  @Test
  void assignsAuthoritiesWithCanonicalKey() {
    AuthorizationPlan plan =
        authorizationPlan()
            .assignAuthorities("subject-1", "identity", List.of("profile:read", "profile:write"))
            .build();

    AssignAuthorizationAuthorities operation =
        (AssignAuthorizationAuthorities) plan.operations().getFirst();

    assertThat(operation.uniqueKey())
        .isEqualTo("authorities:identity:subject-1:profile:read,profile:write");
    assertThat(operation.authorities()).containsExactly("profile:read", "profile:write");
  }

  @Test
  void appliesWildcardProfileGrantsToTenantScopedResources() {
    UUID clinicId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID childResourceId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    AuthorizationPlan plan =
        authorizationPlan()
            .applyTenantResourceProfile(
                "access-profile",
                "subject-1",
                CLINIC_RESOURCE,
                clinicId,
                List.of(
                    grant("clinic:clinic", null, "read"),
                    grant("clinic:clinic", childResourceId, "write"),
                    grant("clinic:chairs", null, "read"),
                    grant("clinic:chairs", null, "read")))
            .build();

    assertThat(plan.operations()).hasSize(4);
    assertThat(plan.operations().getFirst()).isInstanceOf(ProvisionAuthorizationResource.class);
    isClinicReadGrant(plan.operations().get(1));
    assertThat(plan.operations().get(2)).isInstanceOf(ProvisionAuthorizationResource.class);
    isChairsReadGrant(plan.operations().get(3));
  }

  private static void isClinicReadGrant(AuthorizationSyncOperation operation) {
    GrantAuthorizationResourceActions grant = (GrantAuthorizationResourceActions) operation;

    assertThat(grant.uniqueKey())
        .isEqualTo(
            "grant:access-profile:subject-1:clinic:"
                + "clinic:clinic:11111111-1111-1111-1111-111111111111:read");
    assertThat(grant.resourceServerClientId()).isEqualTo("clinic");
    assertThat(grant.resourceName())
        .isEqualTo("clinic:clinic:11111111-1111-1111-1111-111111111111");
    assertThat(grant.actions()).containsExactly("read");
  }

  private static void isChairsReadGrant(AuthorizationSyncOperation operation) {
    GrantAuthorizationResourceActions grant = (GrantAuthorizationResourceActions) operation;

    assertThat(grant.uniqueKey())
        .isEqualTo(
            "grant:access-profile:subject-1:clinic:"
                + "clinic:chairs:11111111-1111-1111-1111-111111111111:read");
    assertThat(grant.resourceServerClientId()).isEqualTo("clinic");
    assertThat(grant.resourceName())
        .isEqualTo("clinic:chairs:11111111-1111-1111-1111-111111111111");
    assertThat(grant.actions()).containsExactly("read");
  }

  private static AccessProfileGrantProjection grant(
      String resourceType, UUID resourceId, String action) {
    return new TestAccessProfileGrant(resourceType, resourceId, action);
  }

  private record TestAccessProfileGrant(String resourceType, UUID resourceId, String action)
      implements AccessProfileGrantProjection {

    @Override
    public UUID getId() {
      return UUID.randomUUID();
    }

    @Override
    public UUID getProfileId() {
      return UUID.randomUUID();
    }

    @Override
    public String getResourceType() {
      return resourceType;
    }

    @Override
    public UUID getResourceId() {
      return resourceId;
    }

    @Override
    public String getAction() {
      return action;
    }
  }
}
