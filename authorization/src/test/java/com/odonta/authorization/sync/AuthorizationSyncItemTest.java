package com.odonta.authorization.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.odonta.authorization.resource.AuthorizationResource;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthorizationSyncItemTest {

  @Test
  void mapsProvisionOperationToDurableItem() {
    AuthorizationResource resource =
        new AuthorizationResource(
            "clinic", "clinic:clinic:123", "clinic:clinic", null, List.of("read", "write"));

    AuthorizationSyncItem item =
        AuthorizationSyncItem.from(
            AuthorizationSyncOperation.provision("resource:clinic:123", resource));

    assertThat(item.getUniqueKey()).isEqualTo("resource:clinic:123");
    assertThat(item.getOperation()).isEqualTo(AuthorizationSyncOperationType.PROVISION_RESOURCE);
    assertThat(item.getResourceServerClientId()).isEqualTo("clinic");
    assertThat(item.getResourceName()).isEqualTo("clinic:clinic:123");
    assertThat(item.getResourceType()).isEqualTo("clinic:clinic");
    assertThat(item.actionList()).containsExactly("read", "write");
  }

  @Test
  void mapsGrantOperationToDurableItem() {
    AuthorizationSyncItem item =
        AuthorizationSyncItem.from(
            AuthorizationSyncOperation.grant(
                "grant:clinic:123", "clinic", "clinic:clinic:123", "subject-1", List.of("read")));

    assertThat(item.getUniqueKey()).isEqualTo("grant:clinic:123");
    assertThat(item.getOperation())
        .isEqualTo(AuthorizationSyncOperationType.GRANT_RESOURCE_ACTIONS);
    assertThat(item.getRequesterSubject()).isEqualTo("subject-1");
    assertThat(item.actionList()).containsExactly("read");
  }

  @Test
  void mapsAuthorityAssignmentToDurableItem() {
    AuthorizationSyncItem item =
        AuthorizationSyncItem.from(
            AuthorizationSyncOperation.assignAuthorities(
                "authorities:identity:subject-1",
                "identity",
                "subject-1",
                List.of("profile:read")));

    assertThat(item.getUniqueKey()).isEqualTo("authorities:identity:subject-1");
    assertThat(item.getOperation()).isEqualTo(AuthorizationSyncOperationType.ASSIGN_AUTHORITIES);
    assertThat(item.getResourceServerClientId()).isEqualTo("identity");
    assertThat(item.getResourceName()).isEqualTo("identity:authorities");
    assertThat(item.getRequesterSubject()).isEqualTo("subject-1");
    assertThat(item.actionList()).containsExactly("profile:read");
  }
}
