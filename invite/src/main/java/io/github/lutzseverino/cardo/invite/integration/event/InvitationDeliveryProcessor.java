package io.github.lutzseverino.cardo.invite.integration.event;

import io.github.lutzseverino.cardo.invite.model.InvitationStatus;
import io.github.lutzseverino.cardo.invite.provider.InvitationSender;
import io.github.lutzseverino.cardo.invite.repository.InvitationProjection;
import io.github.lutzseverino.cardo.invite.repository.InvitationRepository;
import java.time.Clock;
import java.time.OffsetDateTime;

class InvitationDeliveryProcessor {

  private final Clock clock;
  private final InvitationSender sender;
  private final InvitationRepository invitations;

  InvitationDeliveryProcessor(InvitationSender sender, InvitationRepository invitations) {
    this.clock = Clock.systemUTC();
    this.sender = sender;
    this.invitations = invitations;
  }

  void deliver(InvitationDeliveryRequested delivery) {
    InvitationProjection invitation =
        invitations
            .findProjectedById(delivery.invitationId())
            .orElseThrow(() -> new IllegalStateException("Invitation delivery target not found."));
    if (!InvitationStatus.PENDING.equals(invitation.getStatus())
        || invitation.getExpiresAt().isBefore(OffsetDateTime.now(clock))) {
      return;
    }
    sender.send(
        invitation.getInvitedEmail(),
        invitationUrl(invitation.getAcceptUrlBase(), invitation.getToken()));
  }

  private String invitationUrl(String base, String token) {
    return (base.endsWith("/") ? base : base + "/") + token;
  }
}
