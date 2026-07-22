package io.github.lutzseverino.cardo.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Expected production database and PostgreSQL role ownership for Identity. */
@ConfigurationProperties(prefix = "cardo.identity.datastore")
public record IdentityDatastoreProperties(
    String databaseName, String ownerRole, String applicationRole) {

  public IdentityDatastoreProperties {
    databaseName = valueOrDefault(databaseName, "cardo_identity", "database-name");
    ownerRole = valueOrDefault(ownerRole, "cardo_identity_owner", "owner-role");
    applicationRole = valueOrDefault(applicationRole, "cardo_identity_app", "application-role");
    if (ownerRole.equals(applicationRole)) {
      throw new IllegalArgumentException(
          "cardo.identity.datastore owner-role and application-role must be distinct.");
    }
  }

  private static String valueOrDefault(String value, String defaultValue, String property) {
    String resolved = value == null ? defaultValue : value.strip();
    if (resolved.isEmpty()) {
      throw new IllegalArgumentException(
          "cardo.identity.datastore." + property + " must not be blank.");
    }
    return resolved;
  }
}
