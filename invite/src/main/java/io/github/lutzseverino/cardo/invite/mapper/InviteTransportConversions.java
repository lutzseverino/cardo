package io.github.lutzseverino.cardo.invite.mapper;

import io.github.lutzseverino.cardo.invite.model.InvitationStatus;
import org.springframework.stereotype.Component;

@Component
public class InviteTransportConversions {

  public io.github.lutzseverino.cardo.invite.api.model.InvitationStatus toTransport(
      InvitationStatus status) {
    return status == null
        ? null
        : io.github.lutzseverino.cardo.invite.api.model.InvitationStatus.fromValue(
            status.wireValue());
  }
}
