package io.github.lutzseverino.cardo.invite.workflow;

import io.github.lutzseverino.cardo.invite.model.InvitationResult;
import io.github.lutzseverino.cardo.invite.model.InvitationStatus;
import io.github.lutzseverino.cardo.invite.model.PendingInvitation;
import io.github.lutzseverino.cardo.invite.service.InvitationService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Validated
@Component
@RequiredArgsConstructor
public class AcceptInvitationWorkflow {

  private final InvitationAcceptanceApplicator acceptance;
  private final InvitationService invitations;

  @Transactional
  public InvitationResult accept(
      UUID invitationId, @NotBlank String product, @NotNull OffsetDateTime acceptedAt) {
    InvitationResult current = invitations.get(invitationId, product);
    if (InvitationStatus.ACCEPTED.equals(current.status())) {
      return current;
    }
    PendingInvitation invitation = invitations.requirePending(invitationId, product, acceptedAt);
    acceptance.apply(invitation, acceptedAt);
    return invitations.get(invitationId, product);
  }
}
