package io.github.lutzseverino.cardo.invite.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Expected production database and PostgreSQL role ownership for Invite. */
@ConfigurationProperties(prefix = "cardo.invite.datastore")
public record InviteDatastoreProperties(
    String databaseName, String ownerRole, String applicationRole) {

  public InviteDatastoreProperties {
    databaseName = valueOrDefault(databaseName, "cardo_invite", "database-name");
    ownerRole = valueOrDefault(ownerRole, "cardo_invite_owner", "owner-role");
    applicationRole = valueOrDefault(applicationRole, "cardo_invite_app", "application-role");
    if (ownerRole.equals(applicationRole)) {
      throw new IllegalArgumentException(
          "cardo.invite.datastore owner-role and application-role must be distinct.");
    }
  }

  private static String valueOrDefault(String value, String defaultValue, String property) {
    String resolved = value == null ? defaultValue : value.strip();
    if (resolved.isEmpty()) {
      throw new IllegalArgumentException(
          "cardo.invite.datastore." + property + " must not be blank.");
    }
    return resolved;
  }
}
