package io.github.lutzseverino.cardo.invite.integration.event;

import org.springframework.modulith.events.ApplicationModuleListener;

class InvitationDeliveryListener {

  private final InvitationDeliveryProcessor processor;

  InvitationDeliveryListener(InvitationDeliveryProcessor processor) {
    this.processor = processor;
  }

  @ApplicationModuleListener(id = "invite.invitation-delivery")
  void deliver(InvitationDeliveryRequested delivery) {
    processor.deliver(delivery);
  }
}
