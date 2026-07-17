package io.github.lutzseverino.cardo.invite.integration.event;

import io.github.lutzseverino.cardo.invite.provider.InvitationDelivery;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class DurableInvitationDelivery implements InvitationDelivery {

  private final ApplicationEventPublisher events;

  DurableInvitationDelivery(ApplicationEventPublisher events) {
    this.events = events;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void stage(UUID invitationId) {
    events.publishEvent(new InvitationDeliveryRequested(Objects.requireNonNull(invitationId)));
  }
}
