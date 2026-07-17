package io.github.lutzseverino.cardo.invite.client;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface InvitationsClient {

  CreatedInvitation create(CreateInvitation input);

  Invitation get(UUID invitationId);

  InvitationToken getByToken(String token);

  InvitationCompletion requestCompletion(String token);

  InvitationCompletion getCompletion(String token);

  Invitation accept(UUID invitationId, OffsetDateTime acceptedAt);

  Invitation revoke(UUID invitationId);
}
