package com.odonta.identity.mapper;

import com.odonta.identity.model.AuthenticationMethod;
import com.odonta.identity.model.UserStatus;
import org.springframework.stereotype.Component;

@Component
public class IdentityTransportConversions {

  public com.odonta.identity.api.model.UserStatus toTransport(UserStatus status) {
    return status == null
        ? null
        : com.odonta.identity.api.model.UserStatus.fromValue(status.wireValue());
  }

  public com.odonta.identity.api.model.AuthenticationMethod toTransport(
      AuthenticationMethod method) {
    return method == null
        ? null
        : com.odonta.identity.api.model.AuthenticationMethod.fromValue(method.wireValue());
  }
}
