package io.github.lutzseverino.cardo.common.runtime;

import java.sql.SQLException;
import javax.sql.DataSource;

/** Verifies the PostgreSQL database, owner, and direct application-role authentication. */
public final class DatastoreOwnershipVerifier {

  private static final String OWNERSHIP_QUERY =
      """
      select current_database(), session_user, current_user, pg_get_userbyid(datdba)
      from pg_database
      where datname = current_database()
      """;

  private DatastoreOwnershipVerifier() {}

  public static void verify(
      DataSource dataSource,
      String propertyPrefix,
      String expectedDatabase,
      String expectedOwnerRole,
      String expectedApplicationRole) {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(OWNERSHIP_QUERY);
        var result = statement.executeQuery()) {
      if (!result.next()) {
        throw invalid(propertyPrefix, "could not read the effective datastore ownership");
      }
      String database = result.getString(1);
      String sessionRole = result.getString(2);
      String applicationRole = result.getString(3);
      String ownerRole = result.getString(4);
      if (!expectedDatabase.equals(database)) {
        throw invalid(propertyPrefix + ".database-name", "must match the connected database");
      }
      if (!expectedApplicationRole.equals(sessionRole)
          || !expectedApplicationRole.equals(applicationRole)) {
        throw invalid(
            propertyPrefix + ".application-role",
            "must match both the authenticated and effective database roles");
      }
      if (!expectedOwnerRole.equals(ownerRole)) {
        throw invalid(propertyPrefix + ".owner-role", "must match the database owner");
      }
      if (ownerRole.equals(applicationRole)) {
        throw invalid(
            propertyPrefix, "application-role must not own the production service database");
      }
    } catch (SQLException exception) {
      throw invalid(propertyPrefix, "ownership verification query failed", exception);
    }
  }

  private static IllegalStateException invalid(String property, String requirement) {
    return new IllegalStateException(
        "Invalid production property " + property + ": " + requirement + ".");
  }

  private static IllegalStateException invalid(
      String property, String requirement, SQLException cause) {
    return new IllegalStateException(
        "Invalid production property " + property + ": " + requirement + ".", cause);
  }
}
