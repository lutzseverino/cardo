package io.github.lutzseverino.cardo.identity.patch;

import io.github.lutzseverino.cardo.identity.api.model.UpdateCurrentUserRequest;
import io.github.lutzseverino.cardo.identity.api.model.UpdateUserRequest;
import io.github.lutzseverino.cardo.identity.model.UpdateCurrentUserInput;
import io.github.lutzseverino.cardo.identity.model.UpdateUserInput;
import io.github.lutzseverino.cardo.identity.model.UserStatus;
import io.github.lutzseverino.cardo.openapi.patch.PatchFields;
import java.net.URI;
import org.springframework.stereotype.Component;

@Component
public class UserPatchAdapter {

  public UpdateUserInput toInput(UpdateUserRequest request) {
    return new UpdateUserInput(
        request.getName(),
        PatchFields.update(request.getAvatarUrl(), URI::toString),
        toStatus(request.getStatus()));
  }

  public UpdateCurrentUserInput toInput(UpdateCurrentUserRequest request) {
    return new UpdateCurrentUserInput(
        request.getName(), PatchFields.update(request.getAvatarUrl(), URI::toString));
  }

  private UserStatus toStatus(
      io.github.lutzseverino.cardo.identity.api.model.UpdateUserStatus status) {
    if (status == null) {
      return null;
    }
    return switch (status) {
      case ACTIVE -> UserStatus.ACTIVE;
      case DISABLED -> UserStatus.DISABLED;
    };
  }
}
