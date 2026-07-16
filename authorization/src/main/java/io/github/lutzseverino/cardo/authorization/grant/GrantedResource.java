package io.github.lutzseverino.cardo.authorization.grant;

public record GrantedResource(String type, String id) {

  public GrantedResource {
    requireText(type, "type");
    requireText(id, "id");
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
