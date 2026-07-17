package io.github.lutzseverino.cardo.invite.provider;

import java.util.UUID;

public interface InvitationDelivery {

  void stage(UUID invitationId);
}
