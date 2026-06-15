package com.odonta.invite.mapper;

import com.odonta.invite.model.InvitationStatus;
import org.springframework.stereotype.Component;

@Component
public class InviteTransportConversions {

  public com.odonta.invite.api.model.InvitationStatus toTransport(InvitationStatus status) {
    return status == null
        ? null
        : com.odonta.invite.api.model.InvitationStatus.fromValue(status.wireValue());
  }
}
