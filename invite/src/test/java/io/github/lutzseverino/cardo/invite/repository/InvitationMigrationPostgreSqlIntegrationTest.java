package io.github.lutzseverino.cardo.invite.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class InvitationMigrationPostgreSqlIntegrationTest {

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.5-alpine")
          .withDatabaseName("invite")
          .withUsername("invite")
          .withPassword("invite");

  private static final UUID LEGACY_ACCEPTED_ID = UUID.randomUUID();

  @BeforeAll
  static void migrateLegacyData() throws SQLException {
    flyway(MigrationVersion.fromVersion("3")).migrate();
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement =
            connection.prepareStatement(
                """
            insert into invitations (
              id, request_id, product, tenant_id, tenant_resource_type, access_profile,
              invited_email, invited_user_id, invited_authorization_subject, invited_by,
              accept_url_base, expires_at, token, status, accepted_at)
            values (?, ?, 'clinic', ?, 'clinic:clinic', 'clinic:employee',
              'legacy@example.com', ?, 'subject-1', ?, 'https://app.example.com/invitations',
              now() + interval '1 day', 'legacy-token', 'ACCEPTED', now())
            """)) {
      statement.setObject(1, LEGACY_ACCEPTED_ID);
      statement.setObject(2, UUID.randomUUID());
      statement.setObject(3, UUID.randomUUID());
      statement.setObject(4, UUID.randomUUID());
      statement.setObject(5, UUID.randomUUID());
      statement.executeUpdate();
    }
    flyway(MigrationVersion.LATEST).migrate();
  }

  @Test
  void v4PreservesLegacyAcceptedInvitationAsReceiptlessAndAddsRevokedCompletionStatus()
      throws SQLException {
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var receipt =
            connection.prepareStatement("select grant_receipt_id from invitations where id = ?")) {
      receipt.setObject(1, LEGACY_ACCEPTED_ID);
      try (var result = receipt.executeQuery()) {
        assertThat(result.next()).isTrue();
        assertThat(result.getObject(1)).isNull();
      }
      assertThat(
              connection
                  .createStatement()
                  .executeQuery(
                      "select version from flyway_schema_history where success order by installed_rank"))
          .satisfies(
              versions -> {
                java.util.List<String> values = new java.util.ArrayList<>();
                while (versions.next()) {
                  values.add(versions.getString(1));
                }
                assertThat(values).containsExactly("1", "2", "3", "4", "5");
              });
      try (var statement =
          connection.prepareStatement(
              """
              insert into invitation_completion_operations (
                id, invitation_id, invited_user_id, product, status, next_attempt_at, expires_at)
              values (?, ?, ?, 'clinic', 'REVOKED', now(), now() + interval '1 day')
              """)) {
        statement.setObject(1, LEGACY_ACCEPTED_ID);
        statement.setObject(2, LEGACY_ACCEPTED_ID);
        statement.setObject(3, UUID.randomUUID());
        assertThat(statement.executeUpdate()).isOne();
      }
    }
  }

  @Test
  void v4EnforcesReceiptUniquenessWithoutACrossSchemaForeignKey() throws SQLException {
    UUID receiptId = UUID.randomUUID();
    UUID firstId = duplicateInvitation("first-token");
    UUID secondId = duplicateInvitation("second-token");
    updateReceipt(firstId, receiptId);

    assertThatThrownBy(() -> updateReceipt(secondId, receiptId)).isInstanceOf(PSQLException.class);

    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement =
            connection.prepareStatement(
                """
                select count(*)
                from pg_constraint constraint_definition
                join pg_attribute column_definition
                  on column_definition.attrelid = constraint_definition.conrelid
                 and column_definition.attnum = any (constraint_definition.conkey)
                where constraint_definition.conrelid = 'invitations'::regclass
                  and constraint_definition.contype = 'f'
                  and column_definition.attname = 'grant_receipt_id'
                """)) {
      try (var result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isZero();
      }
    }
  }

  private static Flyway flyway(MigrationVersion target) {
    return Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .target(target)
        .load();
  }

  private static UUID duplicateInvitation(String token) throws SQLException {
    UUID id = UUID.randomUUID();
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement =
            connection.prepareStatement(
                """
            insert into invitations (
              id, request_id, product, tenant_id, tenant_resource_type, access_profile,
              invited_email, invited_user_id, invited_authorization_subject, invited_by,
              accept_url_base, expires_at, token, status, accepted_at)
            values (?, ?, 'clinic', ?, 'clinic:clinic', 'clinic:employee',
              'second@example.com', ?, 'subject-2', ?, 'https://app.example.com/invitations',
              now() + interval '1 day', ?, 'ACCEPTED', now())
            """)) {
      statement.setObject(1, id);
      statement.setObject(2, UUID.randomUUID());
      statement.setObject(3, UUID.randomUUID());
      statement.setObject(4, UUID.randomUUID());
      statement.setObject(5, UUID.randomUUID());
      statement.setString(6, token);
      statement.executeUpdate();
    }
    return id;
  }

  private static void updateReceipt(UUID invitationId, UUID receiptId) throws SQLException {
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement =
            connection.prepareStatement(
                "update invitations set grant_receipt_id = ? where id = ?")) {
      statement.setObject(1, receiptId);
      statement.setObject(2, invitationId);
      statement.executeUpdate();
    }
  }
}
