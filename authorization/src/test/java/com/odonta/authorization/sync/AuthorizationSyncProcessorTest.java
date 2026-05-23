package com.odonta.authorization.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.odonta.authorization.AuthorizationAdminClient;
import com.odonta.authorization.AuthorizationSyncStatus;
import com.odonta.authorization.grant.AuthorityGrant;
import com.odonta.authorization.grant.GrantedResourceAction;
import com.odonta.authorization.grant.ResourceActionGrant;
import com.odonta.authorization.resource.AuthorizationResource;
import com.odonta.authorization.resource.CreatedAuthorizationResource;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuthorizationSyncProcessorTest {

  private final AuthorizationAdminClient authorization = mock(AuthorizationAdminClient.class);
  private final AuthorizationSyncItemRepository items = mock(AuthorizationSyncItemRepository.class);
  private final AuthorizationSyncProcessor processor =
      new AuthorizationSyncProcessor(authorization, items);

  @Test
  void provisionsMissingResourceAndMarksItemSynced() {
    AuthorizationResource resource =
        new AuthorizationResource(
            "clinic", "clinic:clinic:123", "clinic:clinic", null, List.of("read", "write"));
    AuthorizationSyncItem item =
        AuthorizationSyncItem.from(
            AuthorizationSyncOperation.provision("resource:clinic:123", resource));
    when(items.findTop50ByStatusOrderByCreatedAtAsc(AuthorizationSyncStatus.PENDING))
        .thenReturn(List.of(item));
    when(authorization.findResourceByName("clinic", "clinic:clinic:123"))
        .thenReturn(Optional.empty());

    processor.processPending();

    verify(authorization).createResource(resource);
    assertThat(item.getStatus()).isEqualTo(AuthorizationSyncStatus.SYNCED);
  }

  @Test
  void resolvesResourceNameBeforeGrantingActions() {
    AuthorizationSyncItem item =
        AuthorizationSyncItem.from(
            AuthorizationSyncOperation.grant(
                "grant:clinic:123",
                "clinic",
                "clinic:clinic:123",
                "subject-1",
                List.of("read", "write")));
    when(items.findTop50ByStatusOrderByCreatedAtAsc(AuthorizationSyncStatus.PENDING))
        .thenReturn(List.of(item));
    when(authorization.findResourceByName("clinic", "clinic:clinic:123"))
        .thenReturn(
            Optional.of(
                new CreatedAuthorizationResource("keycloak-resource-1", "clinic:clinic:123")));
    when(authorization.findResourceActionGrants(any())).thenReturn(List.of());

    processor.processPending();

    ArgumentCaptor<ResourceActionGrant> grant = ArgumentCaptor.forClass(ResourceActionGrant.class);
    verify(authorization).grantResourceActions(grant.capture());
    assertThat(grant.getValue().resourceId()).isEqualTo("keycloak-resource-1");
    assertThat(grant.getValue().requesterSubject()).isEqualTo("subject-1");
    assertThat(grant.getValue().actions()).containsExactly("read", "write");
    assertThat(item.getStatus()).isEqualTo(AuthorizationSyncStatus.SYNCED);
  }

  @Test
  void skipsAlreadyGrantedActions() {
    AuthorizationSyncItem item =
        AuthorizationSyncItem.from(
            AuthorizationSyncOperation.grant(
                "grant:clinic:123", "clinic", "clinic:clinic:123", "subject-1", List.of("read")));
    when(items.findTop50ByStatusOrderByCreatedAtAsc(AuthorizationSyncStatus.PENDING))
        .thenReturn(List.of(item));
    when(authorization.findResourceByName("clinic", "clinic:clinic:123"))
        .thenReturn(
            Optional.of(
                new CreatedAuthorizationResource("keycloak-resource-1", "clinic:clinic:123")));
    when(authorization.findResourceActionGrants(any()))
        .thenReturn(
            List.of(
                new GrantedResourceAction(
                    "ticket-1",
                    "keycloak-resource-1",
                    "clinic:clinic:123",
                    "subject-1",
                    "read",
                    true)));

    processor.processPending();

    verify(authorization).findResourceByName("clinic", "clinic:clinic:123");
    verify(authorization).findResourceActionGrants(any());
    verifyNoMoreInteractions(authorization);
    assertThat(item.getStatus()).isEqualTo(AuthorizationSyncStatus.SYNCED);
  }

  @Test
  void marksItemFailedWhenExecutionFails() {
    AuthorizationSyncItem item =
        AuthorizationSyncItem.from(
            AuthorizationSyncOperation.grant(
                "grant:clinic:123", "clinic", "clinic:clinic:123", "subject-1", List.of("read")));
    when(items.findTop50ByStatusOrderByCreatedAtAsc(AuthorizationSyncStatus.PENDING))
        .thenReturn(List.of(item));
    when(authorization.findResourceByName(any(), any())).thenThrow(new RuntimeException("boom"));

    processor.processPending();

    assertThat(item.getStatus()).isEqualTo(AuthorizationSyncStatus.FAILED);
    assertThat(item.getLastError()).isEqualTo("boom");
  }

  @Test
  void assignsClientAuthorities() {
    AuthorizationSyncItem item =
        AuthorizationSyncItem.from(
            AuthorizationSyncOperation.assignAuthorities(
                "authorities:identity:subject-1",
                "identity",
                "subject-1",
                List.of("profile:read", "profile:write")));
    when(items.findTop50ByStatusOrderByCreatedAtAsc(AuthorizationSyncStatus.PENDING))
        .thenReturn(List.of(item));

    processor.processPending();

    ArgumentCaptor<AuthorityGrant> grant = ArgumentCaptor.forClass(AuthorityGrant.class);
    verify(authorization).ensureClientRolesAssigned(grant.capture());
    assertThat(grant.getValue().resourceServerClientId()).isEqualTo("identity");
    assertThat(grant.getValue().requesterSubject()).isEqualTo("subject-1");
    assertThat(grant.getValue().authorities()).containsExactly("profile:read", "profile:write");
    assertThat(item.getStatus()).isEqualTo(AuthorizationSyncStatus.SYNCED);
  }
}
