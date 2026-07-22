package io.github.lutzseverino.cardo.invite.integration.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;

class InvitationDeliveryListener {

  private static final Logger logger = LoggerFactory.getLogger(InvitationDeliveryListener.class);

  private final InvitationDeliveryProcessor processor;
  private final io.github.lutzseverino.cardo.invite.operations.InviteWorkflowMetrics metrics;

  InvitationDeliveryListener(
      InvitationDeliveryProcessor processor,
      io.github.lutzseverino.cardo.invite.operations.InviteWorkflowMetrics metrics) {
    this.processor = processor;
    this.metrics = metrics;
  }

  @ApplicationModuleListener(id = "invite.invitation-delivery")
  void deliver(InvitationDeliveryRequested delivery) {
    try {
      processor.deliver(delivery);
      metrics.delivery("success");
      logger
          .atInfo()
          .addKeyValue("operationId", delivery.invitationId())
          .addKeyValue("outcome", "success")
          .log("Invitation delivery processed");
    } catch (RuntimeException failure) {
      metrics.delivery("retry");
      logger
          .atWarn()
          .addKeyValue("operationId", delivery.invitationId())
          .addKeyValue("outcome", "retry")
          .addKeyValue("failureType", failure.getClass().getSimpleName())
          .log("Invitation delivery will be retried");
      throw failure;
    }
  }
}
