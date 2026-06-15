package com.odonta.invite.mapper;

import com.odonta.invite.model.InvitationStatus;
import java.net.URI;
import org.springframework.stereotype.Component;

@Component
public class InviteTransportConversions {

  public URI toUri(String value) {
    return value == null ? null : URI.create(value);
  }

  public com.odonta.invite.api.model.InvitationStatus toTransport(InvitationStatus status) {
    return status == null
        ? null
        : com.odonta.invite.api.model.InvitationStatus.fromValue(status.wireValue());
  }
}
