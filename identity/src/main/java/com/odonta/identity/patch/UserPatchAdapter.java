package com.odonta.identity.patch;

import com.odonta.identity.api.model.UpdateCurrentUserRequest;
import com.odonta.identity.api.model.UpdateUserRequest;
import com.odonta.identity.model.UpdateCurrentUserInput;
import com.odonta.identity.model.UpdateUserInput;
import com.odonta.identity.model.UserStatus;
import com.odonta.openapi.patch.PatchFields;
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

  private UserStatus toStatus(com.odonta.identity.api.model.UpdateUserStatus status) {
    if (status == null) {
      return null;
    }
    return switch (status) {
      case ACTIVE -> UserStatus.ACTIVE;
      case DISABLED -> UserStatus.DISABLED;
    };
  }
}
