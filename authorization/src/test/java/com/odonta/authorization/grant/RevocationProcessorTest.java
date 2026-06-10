package com.odonta.authorization.grant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.odonta.authorization.AuthorizationAdminClient;
import java.util.List;
import org.junit.jupiter.api.Test;

class RevocationProcessorTest {

  private final AuthorizationAdminClient authorization = mock(AuthorizationAdminClient.class);
  private final RevocationProcessor processor = new RevocationProcessor(authorization);

  @Test
  void revokesOnlySelectedGrantedResourceActions() {
    RevocationPlan.ResourceRevocation revocation =
        new RevocationPlan.ResourceRevocation(
            "clinic", "clinic:clinic:123", "subject-1", List.of("read"));
    when(authorization.findResourceActionGrants(
            ResourceGrantQuery.forResourceName("clinic", "clinic:clinic:123", "subject-1")))
        .thenReturn(
            List.of(
                action("ticket-1", "read", true),
                action("ticket-2", "write", true),
                action("ticket-3", "read", false)));

    processor.apply(new RevocationPlan(List.of(revocation), List.of()));

    verify(authorization).revokeResourceActionGrant("ticket-1");
  }

  @Test
  void skipsMissingResourceActionsOnReplay() {
    RevocationPlan.ResourceRevocation revocation =
        new RevocationPlan.ResourceRevocation(
            "clinic", "clinic:clinic:123", "subject-1", List.of("read"));

    processor.apply(new RevocationPlan(List.of(revocation), List.of()));

    verify(authorization)
        .findResourceActionGrants(
            ResourceGrantQuery.forResourceName("clinic", "clinic:clinic:123", "subject-1"));
    verifyNoMoreInteractions(authorization);
  }

  @Test
  void revokesAuthorities() {
    RevocationPlan.AuthorityRevocation revocation =
        new RevocationPlan.AuthorityRevocation(
            "identity", "subject-1", List.of("profile:read", "profile:write"));

    processor.apply(new RevocationPlan(List.of(), List.of(revocation)));

    verify(authorization)
        .removeClientRoles(
            new ClientRoleRevocation(
                "identity", "subject-1", List.of("profile:read", "profile:write")));
  }

  @Test
  void propagatesProviderFailureForDurableRetry() {
    RevocationPlan.ResourceRevocation revocation =
        new RevocationPlan.ResourceRevocation(
            "clinic", "clinic:clinic:123", "subject-1", List.of("read"));
    when(authorization.findResourceActionGrants(
            ResourceGrantQuery.forResourceName("clinic", "clinic:clinic:123", "subject-1")))
        .thenThrow(new IllegalStateException("provider unavailable"));

    assertThatThrownBy(() -> processor.apply(new RevocationPlan(List.of(revocation), List.of())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("provider unavailable");
  }

  private GrantedResourceAction action(String id, String action, boolean granted) {
    return new GrantedResourceAction(
        id, "resource-1", "clinic:clinic:123", "subject-1", action, granted);
  }
}
