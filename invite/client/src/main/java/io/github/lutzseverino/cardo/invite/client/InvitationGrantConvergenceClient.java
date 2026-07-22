package io.github.lutzseverino.cardo.invite.client;

import java.util.UUID;

public interface InvitationGrantConvergenceClient {

  InvitationGrantConvergence get(UUID invitationId);
}
