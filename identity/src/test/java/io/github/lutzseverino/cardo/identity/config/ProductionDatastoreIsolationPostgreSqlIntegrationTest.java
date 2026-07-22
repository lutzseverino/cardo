package io.github.lutzseverino.cardo.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class ProductionDatastoreIsolationPostgreSqlIntegrationTest {

  private static final List<String> SERVICES = List.of("identity", "invite", "billing");

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.5-alpine")
          .withDatabaseName("postgres")
          .withUsername("cardo_admin")
          .withPassword("admin-secret");

  @BeforeAll
  static void createIsolatedDatabasesAndRoles() throws SQLException {
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement = connection.createStatement()) {
      for (String service : SERVICES) {
        statement.execute("create role cardo_" + service + " login password 'service-secret'");
        statement.execute("create database cardo_" + service + " owner cardo_" + service);
        statement.execute("revoke connect on database cardo_" + service + " from public");
        statement.execute("grant connect on database cardo_" + service + " to cardo_" + service);
      }
    }
  }

  @Test
  void eachRoleCanMigrateItsOwnDatabaseAndCannotConnectToAnotherOwnersDatabase() throws Exception {
    for (String service : SERVICES) {
      try (var connection =
              DriverManager.getConnection(jdbcUrl(service), "cardo_" + service, "service-secret");
          var statement = connection.createStatement()) {
        statement.execute("create table migration_probe (id integer primary key)");
        statement.execute("insert into migration_probe values (1)");
        try (var result = statement.executeQuery("select count(*) from migration_probe")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getInt(1)).isOne();
        }
      }

      String other = SERVICES.get((SERVICES.indexOf(service) + 1) % SERVICES.size());
      assertThatThrownBy(
              () ->
                  DriverManager.getConnection(jdbcUrl(other), "cardo_" + service, "service-secret"))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("permission denied for database");
    }
  }

  private static String jdbcUrl(String database) {
    return "jdbc:postgresql://"
        + POSTGRES.getHost()
        + ":"
        + POSTGRES.getFirstMappedPort()
        + "/"
        + "cardo_"
        + database;
  }
}
