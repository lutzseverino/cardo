package io.github.lutzseverino.cardo.invite.integration.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class DurableInvitationDeliveryTest {

  @Test
  void stagesAReferenceToTheCommittedInvitation() {
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    UUID invitationId = UUID.randomUUID();

    new DurableInvitationDelivery(events).stage(invitationId);

    verify(events).publishEvent(new InvitationDeliveryRequested(invitationId));
  }
}
