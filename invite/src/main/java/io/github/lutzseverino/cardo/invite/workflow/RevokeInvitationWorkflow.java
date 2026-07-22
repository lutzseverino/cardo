package io.github.lutzseverino.cardo.invite.workflow;

import io.github.lutzseverino.cardo.invite.model.InvitationResult;
import io.github.lutzseverino.cardo.invite.service.InvitationCompletionService;
import io.github.lutzseverino.cardo.invite.service.InvitationService;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Validated
@Component
@RequiredArgsConstructor
public class RevokeInvitationWorkflow {

  private final InvitationCompletionService completions;
  private final InvitationService invitations;

  @Transactional
  public InvitationResult revoke(UUID invitationId, @NotBlank String product) {
    invitations.revoke(invitationId, product);
    completions.revoke(invitationId);
    return invitations.get(invitationId, product);
  }
}
