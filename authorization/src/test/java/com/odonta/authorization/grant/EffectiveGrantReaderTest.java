package com.odonta.authorization.grant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.odonta.authorization.AuthorizationAdminClient;
import com.odonta.authorization.resource.AuthorizationResourceType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EffectiveGrantReaderTest {

  private static final UUID CLINIC_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final AuthorizationResourceType CLINIC =
      AuthorizationResourceType.of("clinic", "clinic", List.of("read", "write"));
  private static final AuthorizationResourceType PATIENTS =
      AuthorizationResourceType.of("clinic", "patients", List.of("read", "write"));

  private final AuthorizationAdminClient authorization = mock(AuthorizationAdminClient.class);
  private final EffectiveGrantReader reader = new EffectiveGrantReader(authorization);

  @Test
  void listsResourceGrantsGroupedBySubject() {
    when(authorization.findResourceActionGrants(
            ResourceGrantQuery.forResourceName("clinic", "clinic:clinic:" + CLINIC_ID)))
        .thenReturn(
            List.of(
                action("ticket-1", "clinic:clinic:" + CLINIC_ID, "subject-1", "read"),
                action("ticket-2", "clinic:clinic:" + CLINIC_ID, "subject-1", "write"),
                action("ticket-3", "clinic:clinic:" + CLINIC_ID, "subject-2", "read")));
    when(authorization.findResourceActionGrants(
            ResourceGrantQuery.forResourceName("clinic", "clinic:patients:" + CLINIC_ID)))
        .thenReturn(
            List.of(action("ticket-4", "clinic:patients:" + CLINIC_ID, "subject-1", "read")));

    List<SubjectGrants> grants = reader.list(List.of(CLINIC, PATIENTS), CLINIC_ID);

    assertThat(grants)
        .containsExactly(
            new SubjectGrants(
                "subject-1",
                List.of(
                    new EffectiveGrant(
                        new GrantedResource("clinic:clinic", CLINIC_ID.toString()),
                        List.of("read", "write")),
                    new EffectiveGrant(
                        new GrantedResource("clinic:patients", CLINIC_ID.toString()),
                        List.of("read")))),
            new SubjectGrants(
                "subject-2",
                List.of(
                    new EffectiveGrant(
                        new GrantedResource("clinic:clinic", CLINIC_ID.toString()),
                        List.of("read")))));
  }

  private GrantedResourceAction action(
      String id, String resourceName, String subject, String action) {
    return new GrantedResourceAction(id, "resource-id", resourceName, subject, action, true);
  }
}
