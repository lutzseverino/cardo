package com.odonta.authorization.grant;

public record ResourceGrantQuery(
    String resourceServerClientId, String resourceId, String requesterSubject, Boolean granted) {

  public ResourceGrantQuery {
    requireText(resourceServerClientId, "resourceServerClientId");
  }

  public static ResourceGrantQuery forRequester(
      String resourceServerClientId, String requesterSubject) {
    return new ResourceGrantQuery(resourceServerClientId, null, requesterSubject, true);
  }

  public static ResourceGrantQuery forResource(String resourceServerClientId, String resourceId) {
    return new ResourceGrantQuery(resourceServerClientId, resourceId, null, true);
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
