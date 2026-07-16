package io.github.lutzseverino.cardo.authorization.grant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class RevocationsTest {

  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final Revocations revocations = new Revocations(events);

  @Test
  void publishesRevocationPlan() {
    RevocationPlan plan =
        new RevocationPlan(
            List.of(
                new RevocationPlan.ResourceRevocation(
                    "clinic", "clinic:clinic:123", "subject-1", List.of("read"))),
            List.of());

    revocations.stage(plan);

    verify(events).publishEvent(plan);
  }

  @Test
  void ignoresEmptyPlan() {
    revocations.stage(new RevocationPlan(List.of(), List.of()));

    verifyNoInteractions(events);
  }
}
