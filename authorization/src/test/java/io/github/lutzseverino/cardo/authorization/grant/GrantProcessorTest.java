package io.github.lutzseverino.cardo.authorization.grant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.authorization.AuthorizationAdminClient;
import io.github.lutzseverino.cardo.authorization.resource.AuthorizationResource;
import io.github.lutzseverino.cardo.authorization.resource.CreatedAuthorizationResource;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GrantProcessorTest {

  private final AuthorizationAdminClient authorization = mock(AuthorizationAdminClient.class);
  private final GrantProcessor processor = new GrantProcessor(authorization);

  @Test
  void provisionsMissingResource() {
    AuthorizationResource resource = resource();
    when(authorization.ensureResource(resource))
        .thenReturn(new CreatedAuthorizationResource("resource-1", resource.name()));

    processor.apply(new GrantPlan(List.of(resource), List.of(), List.of()));

    verify(authorization).ensureResource(resource);
  }

  @Test
  void grantsOnlyMissingResourceActions() {
    AuthorizationResource resource = resource();
    GrantPlan.ResourceGrant grant =
        new GrantPlan.ResourceGrant(
            "clinic", resource.name(), "subject-1", List.of("read", "write"));
    when(authorization.ensureResource(resource))
        .thenReturn(new CreatedAuthorizationResource("resource-1", resource.name()));
    when(authorization.findResourceActionGrants(any()))
        .thenReturn(
            List.of(
                new GrantedResourceAction(
                    "ticket-1", "resource-1", resource.name(), "subject-1", "read", true)));

    processor.apply(new GrantPlan(List.of(resource), List.of(grant), List.of()));

    ArgumentCaptor<ResourceActionAssignment> applied =
        ArgumentCaptor.forClass(ResourceActionAssignment.class);
    verify(authorization).grantResourceActions(applied.capture());
    org.assertj.core.api.Assertions.assertThat(applied.getValue().actions())
        .containsExactly("write");
  }

  @Test
  void skipsGrantedResourceActions() {
    AuthorizationResource resource = resource();
    GrantPlan.ResourceGrant grant =
        new GrantPlan.ResourceGrant("clinic", resource.name(), "subject-1", List.of("read"));
    when(authorization.ensureResource(resource))
        .thenReturn(new CreatedAuthorizationResource("resource-1", resource.name()));
    when(authorization.findResourceActionGrants(any()))
        .thenReturn(
            List.of(
                new GrantedResourceAction(
                    "ticket-1", "resource-1", resource.name(), "subject-1", "read", true)));

    processor.apply(new GrantPlan(List.of(resource), List.of(grant), List.of()));

    verify(authorization).ensureResource(resource);
    verify(authorization).findResourceActionGrants(any());
    verifyNoMoreInteractions(authorization);
  }

  @Test
  void grantsAuthorities() {
    GrantPlan.AuthorityGrant grant =
        new GrantPlan.AuthorityGrant(
            "identity", "subject-1", List.of("profile:read", "profile:write"));

    processor.apply(new GrantPlan(List.of(), List.of(), List.of(grant)));

    verify(authorization)
        .ensureClientRolesAssigned(
            new ClientRoleAssignment(
                "identity", "subject-1", List.of("profile:read", "profile:write")));
  }

  @Test
  void propagatesProviderFailureForDurableRetry() {
    AuthorizationResource resource = resource();
    when(authorization.ensureResource(resource))
        .thenThrow(new IllegalStateException("provider unavailable"));

    assertThatThrownBy(
            () -> processor.apply(new GrantPlan(List.of(resource), List.of(), List.of())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("provider unavailable");
  }

  private AuthorizationResource resource() {
    return new AuthorizationResource(
        "clinic", "clinic:clinic:123", "clinic:clinic", null, List.of("read", "write"));
  }
}
