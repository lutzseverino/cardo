package io.github.lutzseverino.cardo.identity.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.config.IdentityOperationProperties;
import io.github.lutzseverino.cardo.identity.model.IdentityOperation;
import io.github.lutzseverino.cardo.identity.model.IdentityOperationResult;
import io.github.lutzseverino.cardo.identity.model.IdentityOperationStatus;
import io.github.lutzseverino.cardo.identity.model.User;
import io.github.lutzseverino.cardo.identity.operations.IdentityWorkflowMetrics;
import io.github.lutzseverino.cardo.identity.service.IdentityOperationService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest(
    properties = {
      "spring.flyway.locations=classpath:db/migration",
      "spring.jpa.hibernate.ddl-auto=validate"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({IdentityOperationService.class, IdentityOperationPostgreSqlIntegrationTest.Config.class})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IdentityOperationPostgreSqlIntegrationTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-18T10:00:00Z");
  private static final OffsetDateTime NOT_AFTER = NOW.plusDays(30);

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.5-alpine")
          .withDatabaseName("identity")
          .withUsername("identity")
          .withPassword("identity");

  private final IdentityOperationRepository operations;
  private final UserRepository users;
  private final IdentityOperationService service;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;

  @MockitoBean private IdentityWorkflowMetrics metrics;

  @Autowired
  IdentityOperationPostgreSqlIntegrationTest(
      IdentityOperationRepository operations,
      UserRepository users,
      IdentityOperationService service,
      JdbcTemplate jdbc,
      PlatformTransactionManager transactionManager) {
    this.operations = operations;
    this.users = users;
    this.service = service;
    this.jdbc = jdbc;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  @DynamicPropertySource
  static void configurePostgres(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    properties.add("spring.datasource.username", POSTGRES::getUsername);
    properties.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @BeforeEach
  void clearDatabase() {
    operations.deleteAll();
    users.deleteAll();
  }

  @Test
  void migrationsRetainMultipleTerminalCredentialSetupOperations() {
    UUID userId = UUID.randomUUID();
    IdentityOperation failed = operation(UUID.randomUUID(), userId);
    failed.expire("Invitation expired.", NOW);
    IdentityOperation completed = operation(UUID.randomUUID(), userId);
    completed.complete(NOW);

    operations.saveAndFlush(failed);
    operations.saveAndFlush(completed);

    assertThat(
            jdbc.queryForList(
                "select version from flyway_schema_history_identity where success order by installed_rank",
                String.class))
        .containsExactly("1", "2", "3", "4", "5", "6");
    assertThat(operations.findAll())
        .extracting(IdentityOperation::getStatus)
        .containsExactlyInAnyOrder(
            IdentityOperationStatus.FAILED, IdentityOperationStatus.COMPLETED);
  }

  @ParameterizedTest
  @EnumSource(
      value = IdentityOperationStatus.class,
      names = {"REQUESTED", "AWAITING_USER"})
  void partialIndexRejectsASecondActiveCredentialSetup(IdentityOperationStatus secondStatus) {
    UUID userId = UUID.randomUUID();
    operations.saveAndFlush(operation(UUID.randomUUID(), userId));
    IdentityOperation second = operation(UUID.randomUUID(), userId);
    if (IdentityOperationStatus.AWAITING_USER.equals(secondStatus)) {
      second.awaitUser(NOW.plusMinutes(5), NOW.plusHours(1));
    }

    assertThatThrownBy(() -> operations.saveAndFlush(second))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
    assertThat(operations.count()).isOne();
  }

  @Test
  void concurrentEquivalentCreationConvergesToOneActiveOperation() throws Exception {
    UUID userId = invitedUser();
    UUID operationId = UUID.randomUUID();

    List<Attempt<IdentityOperationResult>> attempts =
        withUserRowLockContention(
            () -> service.requestCredentialSetup(operationId, userId, NOT_AFTER),
            () -> service.requestCredentialSetup(operationId, userId, NOT_AFTER));

    assertThat(attempts).allMatch(Attempt::succeeded);
    assertThat(attempts).extracting(attempt -> attempt.result().id()).containsOnly(operationId);
    assertThat(operations.count()).isOne();
    assertThat(operations.findById(operationId).orElseThrow().getStatus())
        .isEqualTo(IdentityOperationStatus.REQUESTED);
  }

  @Test
  void concurrentDifferentCreationReturnsAClassifiedConflict() throws Exception {
    UUID userId = invitedUser();
    UUID firstOperationId = UUID.randomUUID();
    UUID secondOperationId = UUID.randomUUID();

    List<Attempt<IdentityOperationResult>> attempts =
        withUserRowLockContention(
            () -> service.requestCredentialSetup(firstOperationId, userId, NOT_AFTER),
            () -> service.requestCredentialSetup(secondOperationId, userId, NOT_AFTER));

    assertThat(attempts).filteredOn(Attempt::succeeded).hasSize(1);
    assertThat(attempts)
        .filteredOn(attempt -> !attempt.succeeded())
        .singleElement()
        .extracting(Attempt::failure)
        .isInstanceOfSatisfying(
            ApiException.class,
            failure -> {
              assertThat(failure.status()).isEqualTo(409);
              assertThat(failure.code()).isEqualTo("identity_operation_conflict");
            });
    assertThat(operations.count()).isOne();
  }

  @Test
  void restartReclaimsExpiredLeaseAndFencesTheEarlierAcknowledgement() {
    UUID userId = invitedUser();
    UUID operationId = UUID.randomUUID();
    service.requestCredentialSetup(operationId, userId, NOT_AFTER);

    var first = service.claim(operationId).orElseThrow();
    jdbc.update(
        "update identity_operations set next_attempt_at = CURRENT_TIMESTAMP - INTERVAL '1 second'"
            + " where id = ?",
        operationId);
    var recovered = service.claim(operationId).orElseThrow();

    assertThat(recovered.leaseToken()).isNotEqualTo(first.leaseToken());
    assertThat(
            service.recordFailure(
                operationId, first.leaseToken(), new IllegalStateException("late timeout")))
        .isFalse();
    assertThat(
            service.recordFailure(
                operationId, recovered.leaseToken(), new IllegalStateException("retry")))
        .isTrue();
    assertThat(operations.findById(operationId).orElseThrow().getAttemptCount()).isOne();
  }

  @Test
  void retryWaitsForConcurrentCreationAndKeepsOlderOperationFailed() throws Exception {
    UUID userId = invitedUser();
    UUID failedId = UUID.randomUUID();
    IdentityOperation failed = operation(failedId, userId);
    failed.expire("Previous invitation expired.", NOW);
    operations.saveAndFlush(failed);

    UUID newerId = UUID.randomUUID();
    List<Attempt<IdentityOperationResult>> attempts =
        withUserRowLockContention(
            () -> service.requestCredentialSetup(newerId, userId, NOT_AFTER.plusDays(1)),
            () -> service.requestCredentialSetup(failedId, userId, NOT_AFTER));

    assertThat(attempts.getFirst().succeeded()).isTrue();
    assertThat(attempts.getLast().failure())
        .isInstanceOfSatisfying(
            ApiException.class,
            failure -> {
              assertThat(failure.status()).isEqualTo(409);
              assertThat(failure.code()).isEqualTo("identity_operation_conflict");
            });

    assertThat(operations.findById(failedId).orElseThrow().getStatus())
        .isEqualTo(IdentityOperationStatus.FAILED);
    assertThat(operations.findById(newerId).orElseThrow().getStatus())
        .isEqualTo(IdentityOperationStatus.REQUESTED);
  }

  private IdentityOperation operation(UUID operationId, UUID userId) {
    return IdentityOperation.credentialSetup(
        operationId, userId, "subject-" + userId, NOT_AFTER, NOW);
  }

  private UUID invitedUser() {
    return users
        .saveAndFlush(
            User.invited("subject-" + UUID.randomUUID(), UUID.randomUUID() + "@example.com"))
        .getId();
  }

  private <T> List<Attempt<T>> withUserRowLockContention(
      Callable<T> holderTask, Callable<T> contenderTask) throws Exception {
    CountDownLatch holderReady = new CountDownLatch(1);
    CountDownLatch contenderConnected = new CountDownLatch(1);
    AtomicInteger contenderPid = new AtomicInteger();
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Attempt<T>> holder =
          executor.submit(
              () -> {
                try {
                  T result =
                      transactions.execute(
                          ignored -> {
                            T value = call(holderTask);
                            holderReady.countDown();
                            await(contenderConnected);
                            awaitBlocked(contenderPid.get());
                            return value;
                          });
                  return new Attempt<>(result, null);
                } catch (Throwable failure) {
                  holderReady.countDown();
                  return new Attempt<>(null, failure);
                }
              });
      Future<Attempt<T>> contender =
          executor.submit(
              () -> {
                await(holderReady);
                try {
                  T result =
                      transactions.execute(
                          ignored -> {
                            contenderPid.set(
                                jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                            contenderConnected.countDown();
                            return call(contenderTask);
                          });
                  return new Attempt<>(result, null);
                } catch (Throwable failure) {
                  contenderConnected.countDown();
                  return new Attempt<>(null, failure);
                }
              });
      return List.of(holder.get(10, TimeUnit.SECONDS), contender.get(10, TimeUnit.SECONDS));
    }
  }

  private <T> T call(Callable<T> task) {
    try {
      return task.call();
    } catch (RuntimeException failure) {
      throw failure;
    } catch (Exception failure) {
      throw new IllegalStateException("Concurrent test operation failed.", failure);
    }
  }

  private void awaitBlocked(int backendPid) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      Boolean blocked =
          jdbc.queryForObject(
              "select cardinality(pg_blocking_pids(?)) > 0", Boolean.class, backendPid);
      if (Boolean.TRUE.equals(blocked)) {
        return;
      }
      if (Thread.currentThread().isInterrupted()) {
        throw new IllegalStateException(
            "Interrupted while waiting for PostgreSQL lock contention.");
      }
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
    }
    throw new IllegalStateException("Timed out waiting for PostgreSQL lock contention.");
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting for concurrent test operation.");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while waiting for concurrent test operation.", exception);
    }
  }

  private record Attempt<T>(T result, Throwable failure) {

    boolean succeeded() {
      return failure == null;
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Config {

    @Bean
    IdentityOperationProperties identityOperationProperties() {
      return new IdentityOperationProperties(
          Duration.ofSeconds(5),
          Duration.ofSeconds(15),
          Duration.ofHours(24),
          Duration.ofSeconds(5),
          Duration.ofMinutes(1),
          12,
          50);
    }
  }
}
