package io.github.lutzseverino.cardo.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Expected production database and PostgreSQL role ownership for Billing. */
@ConfigurationProperties(prefix = "cardo.billing.datastore")
public record BillingDatastoreProperties(
    String databaseName, String ownerRole, String applicationRole) {

  public BillingDatastoreProperties {
    databaseName = valueOrDefault(databaseName, "cardo_billing", "database-name");
    ownerRole = valueOrDefault(ownerRole, "cardo_billing_owner", "owner-role");
    applicationRole = valueOrDefault(applicationRole, "cardo_billing_app", "application-role");
    if (ownerRole.equals(applicationRole)) {
      throw new IllegalArgumentException(
          "cardo.billing.datastore owner-role and application-role must be distinct.");
    }
  }

  private static String valueOrDefault(String value, String defaultValue, String property) {
    String resolved = value == null ? defaultValue : value.strip();
    if (resolved.isEmpty()) {
      throw new IllegalArgumentException(
          "cardo.billing.datastore." + property + " must not be blank.");
    }
    return resolved;
  }
}
