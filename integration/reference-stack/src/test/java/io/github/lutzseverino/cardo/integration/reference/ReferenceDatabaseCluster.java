package io.github.lutzseverino.cardo.integration.reference;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.testcontainers.postgresql.PostgreSQLContainer;

final class ReferenceDatabaseCluster implements AutoCloseable {

  private static final String ADMIN_USER = "reference_admin";
  private static final String ADMIN_PASSWORD = "reference_admin_password";
  private static final Map<String, Database> DATABASES = databases();

  private final PostgreSQLContainer postgres =
      new PostgreSQLContainer(System.getProperty("cardo.test.postgres.image"))
          .withDatabaseName("postgres")
          .withUsername(ADMIN_USER)
          .withPassword(ADMIN_PASSWORD);

  void start() {
    postgres.start();
    try (Connection admin =
            DriverManager.getConnection(postgres.getJdbcUrl(), ADMIN_USER, ADMIN_PASSWORD);
        Statement sql = admin.createStatement()) {
      for (Database database : DATABASES.values()) {
        sql.execute("create role " + database.owner() + " nologin");
        sql.execute(
            "create role "
                + database.application()
                + " login password '"
                + database.password()
                + "'");
        sql.execute("create database " + database.name() + " owner " + database.owner());
        sql.execute("revoke connect on database " + database.name() + " from public");
        sql.execute("revoke create on database " + database.name() + " from public");
        sql.execute(
            "grant connect on database " + database.name() + " to " + database.application());
      }
    } catch (SQLException failure) {
      throw new IllegalStateException("Could not create isolated reference databases.", failure);
    }
    for (Database database : DATABASES.values()) {
      try (Connection connection =
              DriverManager.getConnection(jdbcUrl(database.name()), ADMIN_USER, ADMIN_PASSWORD);
          Statement sql = connection.createStatement()) {
        sql.execute("create extension if not exists pgcrypto");
        sql.execute("grant usage, create on schema public to " + database.application());
      } catch (SQLException failure) {
        throw new IllegalStateException(
            "Could not grant the reference application database role.", failure);
      }
    }
  }

  Database database(String service) {
    return DATABASES.get(service);
  }

  String jdbcUrl(String database) {
    return "jdbc:postgresql://"
        + postgres.getHost()
        + ":"
        + postgres.getMappedPort(5432)
        + "/"
        + database;
  }

  boolean isRunning() {
    return postgres.isRunning();
  }

  void seedBillingEntitlement(UUID subjectId) {
    Database database = database("billing");
    try (Connection connection =
            DriverManager.getConnection(
                jdbcUrl(database.name()), database.application(), database.password());
        PreparedStatement sql =
            connection.prepareStatement(
                "insert into billing_entitlements"
                    + " (subject_id, product, status, tenant_limit, seat_limit)"
                    + " values (?, 'reference-product', 'ACTIVE', 3, 8)"
                    + " on conflict (subject_id, product) do update set status = 'ACTIVE'")) {
      sql.setObject(1, subjectId);
      sql.executeUpdate();
    } catch (SQLException failure) {
      throw new IllegalStateException("Could not seed the reference Billing entitlement.", failure);
    }
  }

  long referenceReceiptCount(UUID invitationId) {
    return referenceCount(
        "select count(receipt_id) from reference_invitation where id = ?", invitationId);
  }

  long referenceMembershipCount(String subject) {
    return referenceCount(
        "select count(*) from reference_membership where tenant_id = ? and subject = ?",
        ReferenceContract.TENANT_ID,
        subject);
  }

  private long referenceCount(String statement, Object... values) {
    Database database = database("product");
    try (Connection connection =
            DriverManager.getConnection(
                jdbcUrl(database.name()), database.application(), database.password());
        PreparedStatement sql = connection.prepareStatement(statement)) {
      for (int index = 0; index < values.length; index++) {
        sql.setObject(index + 1, values[index]);
      }
      try (var result = sql.executeQuery()) {
        if (!result.next()) {
          throw new IllegalStateException("Reference count query returned no row.");
        }
        return result.getLong(1);
      }
    } catch (SQLException failure) {
      throw new IllegalStateException("Could not inspect reference product state.", failure);
    }
  }

  @Override
  public void close() {
    postgres.stop();
  }

  private static Map<String, Database> databases() {
    Map<String, Database> databases = new LinkedHashMap<>();
    databases.put(
        "identity",
        new Database(
            "cardo_identity", "cardo_identity_owner", "cardo_identity_app", "identity-db-secret"));
    databases.put(
        "invite",
        new Database("cardo_invite", "cardo_invite_owner", "cardo_invite_app", "invite-db-secret"));
    databases.put(
        "billing",
        new Database(
            "cardo_billing", "cardo_billing_owner", "cardo_billing_app", "billing-db-secret"));
    databases.put(
        "product",
        new Database(
            "reference_product",
            "reference_product_owner",
            "reference_product_app",
            "product-db-secret"));
    return Map.copyOf(databases);
  }

  record Database(String name, String owner, String application, String password) {}
}
