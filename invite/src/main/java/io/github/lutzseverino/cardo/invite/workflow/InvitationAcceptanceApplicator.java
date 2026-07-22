package io.github.lutzseverino.cardo.invite.workflow;

import io.github.lutzseverino.cardo.authorization.grant.Grants;
import io.github.lutzseverino.cardo.invite.authorization.InvitationGrantPlanner;
import io.github.lutzseverino.cardo.invite.model.PendingInvitation;
import io.github.lutzseverino.cardo.invite.service.InvitationService;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class InvitationAcceptanceApplicator {

  private final Grants grants;
  private final InvitationGrantPlanner planner;
  private final InvitationService invitations;

  void apply(PendingInvitation invitation, OffsetDateTime acceptedAt) {
    var receipt =
        grants.stage(
            planner.acceptance(
                invitation.tenantId(),
                invitation.invitedAuthorizationSubject(),
                invitation.grants()));
    invitations.accept(invitation.id(), acceptedAt, receipt.id());
  }
}
