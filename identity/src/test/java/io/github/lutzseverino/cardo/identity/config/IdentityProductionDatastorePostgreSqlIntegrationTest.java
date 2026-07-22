package io.github.lutzseverino.cardo.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.lutzseverino.cardo.authorization.schema.AuthorizationSchemaConfiguration;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class IdentityProductionDatastorePostgreSqlIntegrationTest {

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.5-alpine")
          .withDatabaseName("postgres")
          .withUsername("cardo_admin")
          .withPassword("admin-secret");

  @BeforeAll
  static void provisionRolesAndDatabases() throws SQLException {
    executeAdmin(
        "create role cardo_identity_owner nologin",
        "create role cardo_identity_app login password 'service-secret'",
        "create role cardo_identity_deployer login password 'deployer-secret'",
        "grant cardo_identity_app to cardo_identity_deployer",
        "create role foreign_owner nologin",
        "create database cardo_identity owner cardo_identity_owner",
        "create database cardo_foreign owner foreign_owner",
        "revoke connect on database cardo_identity from public",
        "revoke connect on database cardo_foreign from public",
        "grant connect, create on database cardo_identity to cardo_identity_app");
    executeInIdentity("grant usage, create on schema public to cardo_identity_app");
  }

  @Test
  void productionStartupRunsIdentityFlywayAsANonOwnerAndCannotReachAnotherOwner() {
    context()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var dataSource = context.getBean(javax.sql.DataSource.class);
              try (var connection = dataSource.getConnection();
                  var versions =
                      connection
                          .createStatement()
                          .executeQuery(
                              "select version from flyway_schema_history_identity where success and version <> '0' order by installed_rank")) {
                assertThat(versions.next()).isTrue();
                assertThat(versions.getString(1)).isEqualTo("1");
              } catch (SQLException exception) {
                throw new AssertionError(exception);
              }
            });

    assertThatThrownBy(
            () ->
                DriverManager.getConnection(
                    jdbcUrl("cardo_foreign"), "cardo_identity_app", "service-secret"))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("permission denied for database");
  }

  @Test
  void productionStartupRejectsAPrivilegedSessionThatAssumesTheApplicationRole() {
    context(
            "cardo_identity_deployer",
            "deployer-secret",
            "spring.datasource.hikari.connection-init-sql=set role cardo_identity_app")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .hasRootCauseMessage(
                        "Invalid production property cardo.identity.datastore.application-role: "
                            + "must match both the authenticated and effective database roles."));
  }

  private ApplicationContextRunner context() {
    return context("cardo_identity_app", "service-secret");
  }

  private ApplicationContextRunner context(String username, String password, String... properties) {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                "cardo.identity.runtime.mode=production",
                "spring.datasource.url=" + jdbcUrl("cardo_identity"),
                "spring.datasource.username=" + username,
                "spring.datasource.password=" + password,
                "spring.flyway.table=flyway_schema_history_identity",
                "spring.flyway.baseline-on-migrate=true",
                "spring.flyway.baseline-version=0",
                "spring.flyway.locations=classpath:db/migration,classpath:db/authorization/publications",
                "spring.flyway.placeholders.authorizationSchema=identity_events");
    return runner.withPropertyValues(properties);
  }

  private static void executeAdmin(String... commands) throws SQLException {
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement = connection.createStatement()) {
      for (String command : commands) {
        statement.execute(command);
      }
    }
  }

  private static void executeInIdentity(String... commands) throws SQLException {
    try (var connection =
            DriverManager.getConnection(
                jdbcUrl("cardo_identity"), POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement = connection.createStatement()) {
      for (String command : commands) {
        statement.execute(command);
      }
    }
  }

  private static String jdbcUrl(String database) {
    return "jdbc:postgresql://"
        + POSTGRES.getHost()
        + ":"
        + POSTGRES.getFirstMappedPort()
        + "/"
        + database;
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties({
    IdentityRuntimeProperties.class,
    IdentityDatastoreProperties.class
  })
  @Import({IdentityDatastoreConfiguration.class, AuthorizationSchemaConfiguration.class})
  static class TestConfiguration {}
}
