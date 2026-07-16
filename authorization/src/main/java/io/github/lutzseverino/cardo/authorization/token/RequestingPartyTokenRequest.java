package io.github.lutzseverino.cardo.authorization.token;

import java.util.List;
import java.util.Objects;

public record RequestingPartyTokenRequest(
    String accessToken, String resourceServerClientId, List<RequestedPermission> permissions) {

  public RequestingPartyTokenRequest {
    requireText(accessToken, "accessToken");
    requireText(resourceServerClientId, "resourceServerClientId");
    permissions = List.copyOf(Objects.requireNonNull(permissions, "permissions"));
  }

  public static RequestingPartyTokenRequest allPermissions(
      String accessToken, String resourceServerClientId) {
    return new RequestingPartyTokenRequest(accessToken, resourceServerClientId, List.of());
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
