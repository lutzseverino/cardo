package io.github.lutzseverino.cardo.identity.mapper;

import io.github.lutzseverino.cardo.identity.model.AuthenticationMethod;
import io.github.lutzseverino.cardo.identity.model.UserStatus;
import org.springframework.stereotype.Component;

@Component
public class IdentityTransportConversions {

  public io.github.lutzseverino.cardo.identity.api.model.UserStatus toTransport(UserStatus status) {
    return status == null
        ? null
        : io.github.lutzseverino.cardo.identity.api.model.UserStatus.fromValue(status.wireValue());
  }

  public io.github.lutzseverino.cardo.identity.api.model.AuthenticationMethod toTransport(
      AuthenticationMethod method) {
    return method == null
        ? null
        : io.github.lutzseverino.cardo.identity.api.model.AuthenticationMethod.fromValue(
            method.wireValue());
  }
}
