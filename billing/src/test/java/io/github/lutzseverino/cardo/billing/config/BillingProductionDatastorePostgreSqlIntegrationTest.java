package io.github.lutzseverino.cardo.billing.config;

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
class BillingProductionDatastorePostgreSqlIntegrationTest {

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.5-alpine")
          .withDatabaseName("postgres")
          .withUsername("cardo_admin")
          .withPassword("admin-secret");

  @BeforeAll
  static void provisionRolesAndDatabases() throws SQLException {
    executeAdmin(
        "create role cardo_billing_owner nologin",
        "create role cardo_billing_app login password 'service-secret'",
        "create role foreign_owner nologin",
        "create database cardo_billing owner cardo_billing_owner",
        "create database cardo_foreign owner foreign_owner",
        "revoke connect on database cardo_billing from public",
        "revoke connect on database cardo_foreign from public",
        "grant connect, create on database cardo_billing to cardo_billing_app");
    executeInService("grant usage, create on schema public to cardo_billing_app");
  }

  @Test
  void productionStartupRunsBillingFlywayAsANonOwnerAndCannotReachAnotherOwner() {
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
                              "select version from flyway_schema_history_billing where success and version <> '0' order by installed_rank")) {
                assertThat(versions.next()).isTrue();
                assertThat(versions.getString(1)).isEqualTo("1");
              } catch (SQLException exception) {
                throw new AssertionError(exception);
              }
            });

    assertThatThrownBy(
            () ->
                DriverManager.getConnection(
                    jdbcUrl("cardo_foreign"), "cardo_billing_app", "service-secret"))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("permission denied for database");
  }

  private ApplicationContextRunner context() {
    return new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class))
        .withUserConfiguration(TestConfiguration.class)
        .withPropertyValues(
            "cardo.billing.runtime.mode=production",
            "spring.datasource.url=" + jdbcUrl("cardo_billing"),
            "spring.datasource.username=cardo_billing_app",
            "spring.datasource.password=service-secret",
            "spring.flyway.table=flyway_schema_history_billing",
            "spring.flyway.baseline-on-migrate=true",
            "spring.flyway.baseline-version=0",
            "spring.flyway.locations=classpath:db/migration");
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

  private static void executeInService(String... commands) throws SQLException {
    try (var connection =
            DriverManager.getConnection(
                jdbcUrl("cardo_billing"), POSTGRES.getUsername(), POSTGRES.getPassword());
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
  @EnableConfigurationProperties({BillingRuntimeProperties.class, BillingDatastoreProperties.class})
  @Import({BillingDatastoreConfiguration.class, AuthorizationSchemaConfiguration.class})
  static class TestConfiguration {}
}
