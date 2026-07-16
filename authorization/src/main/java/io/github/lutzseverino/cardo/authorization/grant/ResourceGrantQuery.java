package io.github.lutzseverino.cardo.authorization.grant;

public record ResourceGrantQuery(
    String resourceServerClientId,
    String resourceId,
    String resourceName,
    String requesterSubject,
    Boolean granted) {

  public ResourceGrantQuery {
    requireText(resourceServerClientId, "resourceServerClientId");
    if (resourceId != null && resourceName != null) {
      throw new IllegalArgumentException("resourceId and resourceName are mutually exclusive");
    }
  }

  public static ResourceGrantQuery forResourceId(
      String resourceServerClientId, String resourceId, String requesterSubject) {
    requireText(resourceId, "resourceId");
    return new ResourceGrantQuery(resourceServerClientId, resourceId, null, requesterSubject, true);
  }

  public static ResourceGrantQuery forResourceName(
      String resourceServerClientId, String resourceName) {
    return forResourceName(resourceServerClientId, resourceName, null);
  }

  public static ResourceGrantQuery forResourceName(
      String resourceServerClientId, String resourceName, String requesterSubject) {
    requireText(resourceName, "resourceName");
    return new ResourceGrantQuery(
        resourceServerClientId, null, resourceName, requesterSubject, true);
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
