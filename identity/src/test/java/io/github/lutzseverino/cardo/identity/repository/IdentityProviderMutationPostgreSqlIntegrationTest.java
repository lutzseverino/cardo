package io.github.lutzseverino.cardo.identity.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.config.IdentityProviderMutationProperties;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutation;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutationStatus;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutationType;
import io.github.lutzseverino.cardo.identity.model.User;
import io.github.lutzseverino.cardo.identity.operations.IdentityWorkflowMetrics;
import io.github.lutzseverino.cardo.identity.service.IdentityProviderMutationService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
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
@Import({
  IdentityProviderMutationService.class,
  IdentityWorkflowMetrics.class,
  IdentityProviderMutationPostgreSqlIntegrationTest.Config.class
})
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
  private final JdbcTemplate jdbc;
  private final MeterRegistry registry;

  @Autowired
  IdentityProviderMutationPostgreSqlIntegrationTest(
      IdentityProviderMutationRepository mutations,
      UserRepository users,
      IdentityProviderMutationService service,
      JdbcTemplate jdbc,
      MeterRegistry registry) {
    this.mutations = mutations;
    this.users = users;
    this.service = service;
    this.jdbc = jdbc;
    this.registry = registry;
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
  void actionableMetricsExcludeProviderMutationsWithAnUnexpiredLease() {
    OffsetDateTime now = OffsetDateTime.now();
    IdentityProviderMutation actionable =
        IdentityProviderMutation.provisionalProvision(
            UUID.randomUUID(), "actionable@example.com", "marker-actionable", now.minusHours(2));
    IdentityProviderMutation leased =
        IdentityProviderMutation.provisionalProvision(
            UUID.randomUUID(), "leased@example.com", "marker-leased", now.minusHours(4));
    leased.claim(now.plusHours(1));
    mutations.saveAllAndFlush(List.of(actionable, leased));
    jdbc.update(
        "update identity_provider_mutations set created_at = CURRENT_TIMESTAMP - INTERVAL '2 hours'"
            + " where id = ?",
        actionable.getId());
    jdbc.update(
        "update identity_provider_mutations set created_at = CURRENT_TIMESTAMP - INTERVAL '4 hours'"
            + " where id = ?",
        leased.getId());

    assertThat(
            registry
                .get("cardo.durable.workflow.work")
                .tags(
                    "workflow",
                    "identity-provider-mutation",
                    "type",
                    "provision-provisional-user",
                    "state",
                    "active")
                .gauge()
                .value())
        .isEqualTo(2D);
    assertThat(
            registry
                .get("cardo.durable.workflow.work")
                .tags(
                    "workflow",
                    "identity-provider-mutation",
                    "type",
                    "provision-provisional-user",
                    "state",
                    "actionable")
                .gauge()
                .value())
        .isEqualTo(1D);
    assertThat(
            registry
                .get("cardo.durable.workflow.oldest.actionable.age")
                .tags(
                    "workflow", "identity-provider-mutation", "type", "provision-provisional-user")
                .gauge()
                .value())
        .isBetween(7_100D, 7_300D);
  }

  @Test
  void onlyOneActiveProvisionalIntentCanOwnAnEmail() {
    OffsetDateTime now = OffsetDateTime.now();
    mutations.saveAndFlush(
        IdentityProviderMutation.provisionalProvision(
            UUID.randomUUID(), "user@example.com", "marker-1", now));

    assertThatThrownBy(
            () ->
                mutations.saveAndFlush(
                    IdentityProviderMutation.provisionalProvision(
                        UUID.randomUUID(), "user@example.com", "marker-2", now)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void activePasswordProvisionBlocksAProvisionalRequestForTheSameEmail() {
    service.requestPasswordProvision("user@example.com", "User");

    assertThatThrownBy(() -> service.requestProvisionalProvision("user@example.com"))
        .isInstanceOfSatisfying(
            ApiException.class,
            failure -> assertThat(failure.code()).isEqualTo("user_provisioning_in_progress"));

    assertThat(activeProvisioningMutations())
        .singleElement()
        .extracting(IdentityProviderMutation::getType)
        .isEqualTo(IdentityProviderMutationType.PROVISION_PASSWORD_USER);
  }

  @Test
  void activeProvisionalProvisionBlocksAPasswordRequestForTheSameEmail() {
    service.requestProvisionalProvision("user@example.com");

    assertThatThrownBy(() -> service.requestPasswordProvision("user@example.com", "User"))
        .isInstanceOfSatisfying(
            ApiException.class,
            failure -> assertThat(failure.code()).isEqualTo("user_provisioning_in_progress"));

    assertThat(activeProvisioningMutations())
        .singleElement()
        .extracting(IdentityProviderMutation::getType)
        .isEqualTo(IdentityProviderMutationType.PROVISION_PROVISIONAL_USER);
  }

  @Test
  void concurrentCrossTypeRequestsCreateExactlyOneActiveOwner() throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<String> password =
          executor.submit(
              () -> {
                start.await();
                try {
                  service.requestPasswordProvision("user@example.com", "User");
                  return "accepted";
                } catch (ApiException failure) {
                  return failure.code();
                }
              });
      Future<String> provisional =
          executor.submit(
              () -> {
                start.await();
                try {
                  service.requestProvisionalProvision("user@example.com");
                  return "accepted";
                } catch (ApiException failure) {
                  return failure.code();
                }
              });
      start.countDown();

      assertThat(List.of(password.get(), provisional.get()))
          .containsExactlyInAnyOrder("accepted", "user_provisioning_in_progress");
    }

    assertThat(activeProvisioningMutations()).singleElement();
  }

  @Test
  void concurrentEquivalentProvisionalRequestsShareOneDurableIntent() throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      java.util.concurrent.Callable<String> request =
          () -> {
            start.await();
            try {
              return service.requestProvisionalProvision("user@example.com").correlationMarker();
            } catch (io.github.lutzseverino.cardo.common.api.ApiException failure) {
              return failure.code();
            }
          };
      Future<String> first = executor.submit(request);
      Future<String> second = executor.submit(request);
      start.countDown();

      assertThat(List.of(first.get(), second.get()))
          .anySatisfy(value -> assertThat(value).isEqualTo("user_provisioning_in_progress"))
          .anySatisfy(value -> assertThat(value).isNotEqualTo("user_provisioning_in_progress"));
    }

    assertThat(mutations.findAll())
        .singleElement()
        .satisfies(
            mutation -> {
              assertThat(mutation.getType())
                  .isEqualTo(IdentityProviderMutationType.PROVISION_PROVISIONAL_USER);
              assertThat(mutation.getCorrelationMarker()).isNotBlank();
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

  @Test
  void v4ChangesOnlyConstraintsAndIndexesWithoutBackfillingWork() throws Exception {
    String schema = "provisional_constraints_" + UUID.randomUUID().toString().replace("-", "");
    migrate(schema, "3");
    int before;
    try (Connection connection = connection(schema);
        Statement statement = connection.createStatement();
        ResultSet rows =
            statement.executeQuery("select count(*) from identity_provider_mutations")) {
      rows.next();
      before = rows.getInt(1);
    }

    migrate(schema, "4");

    try (Connection connection = connection(schema);
        Statement statement = connection.createStatement()) {
      try (ResultSet rows =
          statement.executeQuery("select count(*) from identity_provider_mutations")) {
        rows.next();
        assertThat(rows.getInt(1)).isEqualTo(before);
      }
      statement.executeUpdate(
          "insert into identity_provider_mutations "
              + "(id, mutation_type, status, normalized_email, correlation_marker, next_attempt_at) "
              + "values ('"
              + UUID.randomUUID()
              + "', 'PROVISION_PROVISIONAL_USER', 'REQUESTED', "
              + "'new@example.com', 'marker-v4', now())");
    }
  }

  @Test
  void v6PreservesValidV5RowsAndInstallsCrossTypeOwnerExclusivity() throws Exception {
    String schema = "owner_exclusivity_" + UUID.randomUUID().toString().replace("-", "");
    migrate(schema, "5");
    try (Connection connection = connection(schema);
        Statement statement = connection.createStatement()) {
      insertProvisioningMutation(
          statement, "PROVISION_PASSWORD_USER", "REQUESTED", "password@example.com", "marker-1");
      insertProvisioningMutation(
          statement,
          "PROVISION_PROVISIONAL_USER",
          "REQUESTED",
          "provisional@example.com",
          "marker-2");
      insertProvisioningMutation(
          statement, "PROVISION_PROVISIONAL_USER", "COMPLETED", "password@example.com", "marker-3");
    }

    migrate(schema, "6");

    try (Connection connection = connection(schema);
        Statement statement = connection.createStatement()) {
      try (ResultSet rows =
          statement.executeQuery("select count(*) from identity_provider_mutations")) {
        rows.next();
        assertThat(rows.getInt(1)).isEqualTo(3);
      }
      assertThatThrownBy(
              () ->
                  insertProvisioningMutation(
                      statement,
                      "PROVISION_PROVISIONAL_USER",
                      "REQUESTED",
                      "password@example.com",
                      "marker-4"))
          .isInstanceOf(java.sql.SQLException.class);
    }
  }

  @Test
  void v6FailsSafelyWhenV5ContainsCrossTypeActiveDuplicates() throws Exception {
    String schema = "owner_preflight_" + UUID.randomUUID().toString().replace("-", "");
    migrate(schema, "5");
    try (Connection connection = connection(schema);
        Statement statement = connection.createStatement()) {
      insertProvisioningMutation(
          statement, "PROVISION_PASSWORD_USER", "REQUESTED", "private@example.com", "marker-1");
      insertProvisioningMutation(
          statement, "PROVISION_PROVISIONAL_USER", "REQUESTED", "private@example.com", "marker-2");
    }

    assertThatThrownBy(() -> migrate(schema, "6"))
        .isInstanceOf(FlywayException.class)
        .hasMessageContaining("operator reconciliation")
        .hasMessageNotContaining("private@example.com");

    try (Connection connection = connection(schema);
        Statement statement = connection.createStatement()) {
      try (ResultSet rows =
          statement.executeQuery("select count(*) from identity_provider_mutations")) {
        rows.next();
        assertThat(rows.getInt(1)).isEqualTo(2);
      }
      try (ResultSet index =
          statement.executeQuery(
              "select indexdef from pg_indexes where schemaname = current_schema() "
                  + "and indexname = 'uk_identity_provider_mutations_active_provision_email'")) {
        index.next();
        assertThat(index.getString(1)).contains("normalized_email, mutation_type");
      }
    }
  }

  private List<IdentityProviderMutation> activeProvisioningMutations() {
    return mutations.findAll().stream()
        .filter(
            mutation ->
                mutation.getStatus() == IdentityProviderMutationStatus.REQUESTED
                    && (mutation.getType() == IdentityProviderMutationType.PROVISION_PASSWORD_USER
                        || mutation.getType()
                            == IdentityProviderMutationType.PROVISION_PROVISIONAL_USER))
        .toList();
  }

  private void insertProvisioningMutation(
      Statement statement, String type, String status, String email, String marker)
      throws Exception {
    statement.executeUpdate(
        "insert into identity_provider_mutations "
            + "(id, mutation_type, status, normalized_email, display_name, correlation_marker, "
            + "next_attempt_at) values ('"
            + UUID.randomUUID()
            + "', '"
            + type
            + "', '"
            + status
            + "', '"
            + email
            + "', "
            + ("PROVISION_PASSWORD_USER".equals(type) ? "'User'" : "null")
            + ", '"
            + marker
            + "', now())");
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

  @TestConfiguration(proxyBeanMethods = false)
  static class Config {

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }
}
