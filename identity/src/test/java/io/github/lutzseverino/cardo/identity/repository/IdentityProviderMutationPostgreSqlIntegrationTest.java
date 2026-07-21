package io.github.lutzseverino.cardo.identity.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.lutzseverino.cardo.identity.config.IdentityProviderMutationProperties;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutation;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutationStatus;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutationType;
import io.github.lutzseverino.cardo.identity.model.User;
import io.github.lutzseverino.cardo.identity.service.IdentityProviderMutationService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest(
    properties = {
      "spring.flyway.locations=classpath:db/migration",
      "spring.jpa.hibernate.ddl-auto=validate",
      "cardo.identity.provider-mutations.dispatch-delay=5s",
      "cardo.identity.provider-mutations.retry-base-delay=1s",
      "cardo.identity.provider-mutations.claim-lease=1m",
      "cardo.identity.provider-mutations.max-attempts=3",
      "cardo.identity.provider-mutations.batch-size=50"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import(IdentityProviderMutationService.class)
@EnableConfigurationProperties(IdentityProviderMutationProperties.class)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IdentityProviderMutationPostgreSqlIntegrationTest {

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.5-alpine")
          .withDatabaseName("identity_mutations")
          .withUsername("identity")
          .withPassword("identity");

  private final IdentityProviderMutationRepository mutations;
  private final UserRepository users;
  private final IdentityProviderMutationService service;

  @Autowired
  IdentityProviderMutationPostgreSqlIntegrationTest(
      IdentityProviderMutationRepository mutations,
      UserRepository users,
      IdentityProviderMutationService service) {
    this.mutations = mutations;
    this.users = users;
    this.service = service;
  }

  @DynamicPropertySource
  static void configurePostgres(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    properties.add("spring.datasource.username", POSTGRES::getUsername);
    properties.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @BeforeEach
  void clearDatabase() {
    mutations.deleteAll();
    users.deleteAll();
  }

  @Test
  void partialIndexAllowsOnlyOneActiveDesiredStatePerUser() {
    User user = users.saveAndFlush(new User("subject-1", "user@example.com", "User"));
    OffsetDateTime now = OffsetDateTime.now();
    mutations.saveAndFlush(
        IdentityProviderMutation.enabledState(
            UUID.randomUUID(), user.getId(), "subject-1", false, now));

    assertThatThrownBy(
            () ->
                mutations.saveAndFlush(
                    IdentityProviderMutation.enabledState(
                        UUID.randomUUID(), user.getId(), "subject-1", true, now)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void serviceCoalescesDesiredStateWithoutCreatingCompetingWork() {
    User user = users.saveAndFlush(new User("subject-1", "user@example.com", "User"));

    service.requestEnabledState(user.getId(), "subject-1", false);
    service.requestEnabledState(user.getId(), "subject-1", true);

    List<IdentityProviderMutation> active =
        mutations.findAll().stream()
            .filter(
                mutation ->
                    mutation.getType() == IdentityProviderMutationType.SET_IDENTITY_ENABLED
                        && mutation.getStatus() == IdentityProviderMutationStatus.REQUESTED)
            .toList();
    assertThat(active)
        .singleElement()
        .satisfies(
            mutation -> {
              assertThat(mutation.getDesiredEnabled()).isTrue();
              assertThat(mutation.getDesiredVersion()).isEqualTo(2);
            });
  }

  @Test
  void v3BackfillsBindingsAndDesiredStateForPreexistingUsers() throws Exception {
    String schema = "mutation_backfill_" + UUID.randomUUID().toString().replace("-", "");
    migrate(schema, "2");
    UUID activeId = UUID.randomUUID();
    UUID disabledId = UUID.randomUUID();
    try (Connection connection = connection(schema);
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "insert into users (id, keycloak_subject, email, status) values ('"
              + activeId
              + "', 'subject-active', 'active@example.com', 'ACTIVE')");
      statement.executeUpdate(
          "insert into users (id, keycloak_subject, email, status) values ('"
              + disabledId
              + "', 'subject-disabled', 'disabled@example.com', 'DISABLED')");
    }

    migrate(schema, "3");

    try (Connection connection = connection(schema);
        Statement statement = connection.createStatement();
        ResultSet rows =
            statement.executeQuery(
                "select mutation_type, provider_subject, desired_enabled "
                    + "from identity_provider_mutations order by mutation_type, provider_subject")) {
      java.util.ArrayList<String> repaired = new java.util.ArrayList<>();
      while (rows.next()) {
        repaired.add(
            rows.getString("mutation_type")
                + ":"
                + rows.getString("provider_subject")
                + ":"
                + rows.getObject("desired_enabled"));
      }
      assertThat(repaired)
          .containsExactly(
              "BIND_USER_ID:subject-active:null",
              "BIND_USER_ID:subject-disabled:null",
              "SET_IDENTITY_ENABLED:subject-active:true",
              "SET_IDENTITY_ENABLED:subject-disabled:false");
    }
  }

  private void migrate(String schema, String target) {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas(schema)
        .defaultSchema(schema)
        .locations("classpath:db/migration")
        .target(target)
        .load()
        .migrate();
  }

  private Connection connection(String schema) throws Exception {
    Connection connection =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    connection.setSchema(schema);
    return connection;
  }
}
