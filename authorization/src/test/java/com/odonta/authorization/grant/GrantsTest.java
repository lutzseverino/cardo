package com.odonta.authorization.grant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class GrantsTest {

  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final Grants grants = new Grants(events);

  @Test
  void publishesGrantPlan() {
    GrantPlan plan =
        new GrantPlan(
            List.of(),
            List.of(),
            List.of(
                new GrantPlan.AuthorityGrant("identity", "subject-1", List.of("profile:read"))));

    grants.stage(plan);

    verify(events).publishEvent(plan);
  }

  @Test
  void ignoresEmptyPlan() {
    grants.stage(new GrantPlan(List.of(), List.of(), List.of()));

    verifyNoInteractions(events);
  }
}
