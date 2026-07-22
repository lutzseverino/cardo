package io.github.lutzseverino.cardo.invite.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.authorization.grant.GrantReceipt;
import io.github.lutzseverino.cardo.authorization.grant.GrantReceiptStatus;
import io.github.lutzseverino.cardo.authorization.grant.Grants;
import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.invite.authorization.InvitationGrantPlanner;
import io.github.lutzseverino.cardo.invite.mapper.InvitationApplicationMapperImpl;
import io.github.lutzseverino.cardo.invite.model.Invitation;
import io.github.lutzseverino.cardo.invite.model.InvitationCompletionOperation;
import io.github.lutzseverino.cardo.invite.model.InvitationCompletionStatus;
import io.github.lutzseverino.cardo.invite.model.InvitationGrantConvergenceStatus;
import io.github.lutzseverino.cardo.invite.model.InvitationGrantInput;
import io.github.lutzseverino.cardo.invite.model.InvitationStatus;
import io.github.lutzseverino.cardo.invite.provider.InvitationDelivery;
import io.github.lutzseverino.cardo.invite.repository.InvitationCompletionOperationRepository;
import io.github.lutzseverino.cardo.invite.repository.InvitationRepository;
import io.github.lutzseverino.cardo.invite.service.InvitationCompletionService;
import io.github.lutzseverino.cardo.invite.service.InvitationGrantConvergenceService;
import io.github.lutzseverino.cardo.invite.service.InvitationService;
import java.net.URI;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.AopTestUtils;
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
@Import({
  InvitationService.class,
  InvitationCompletionService.class,
  InvitationGrantConvergenceService.class,
  AcceptInvitationWorkflow.class,
  InvitationAcceptanceApplicator.class,
  RevokeInvitationWorkflow.class,
  InvitationLifecyclePostgreSqlIntegrationTest.Config.class
})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InvitationLifecyclePostgreSqlIntegrationTest {

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.5-alpine")
          .withDatabaseName("invite")
          .withUsername("invite")
          .withPassword("invite");

  private final AcceptInvitationWorkflow acceptance;
  private final InvitationCompletionService completionService;
  private final InvitationGrantConvergenceService convergence;
  private final Grants grants;
  private final InvitationCompletionOperationRepository completions;
  private final InvitationRepository invitations;
  private final InvitationService invitationService;
  private final RevokeInvitationWorkflow revocation;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;

  @Autowired
  InvitationLifecyclePostgreSqlIntegrationTest(
      AcceptInvitationWorkflow acceptance,
      InvitationCompletionService completionService,
      InvitationGrantConvergenceService convergence,
      Grants grants,
      InvitationCompletionOperationRepository completions,
      InvitationRepository invitations,
      InvitationService invitationService,
      RevokeInvitationWorkflow revocation,
      JdbcTemplate jdbc,
      PlatformTransactionManager transactionManager) {
    this.acceptance = acceptance;
    this.completionService = completionService;
    this.convergence = convergence;
    this.grants = AopTestUtils.getUltimateTargetObject(grants);
    this.completions = completions;
    this.invitations = invitations;
    this.invitationService = invitationService;
    this.revocation = revocation;
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
    completions.deleteAll();
    invitations.deleteAll();
    reset(grants);
  }

  @Test
  void concurrentAcceptanceStagesAndStoresExactlyOneReceipt() throws Exception {
    UUID invitationId = invitation();
    OffsetDateTime acceptedAt = OffsetDateTime.now();
    AtomicInteger stages = new AtomicInteger();
    when(grants.stage(any()))
        .thenAnswer(
            ignored -> {
              stages.incrementAndGet();
              return receipt();
            });

    List<Attempt<?>> attempts =
        contend(
            () -> acceptance.accept(invitationId, "clinic", acceptedAt),
            () -> acceptance.accept(invitationId, "clinic", acceptedAt));

    assertThat(attempts).allMatch(Attempt::succeeded);
    assertThat(stages).hasValue(1);
    Invitation invitation = invitations.findById(invitationId).orElseThrow();
    assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
    assertThat(invitation.getGrantReceiptId()).isNotNull();
  }

  @Test
  void grantStagingFailureLeavesTheInvitationPendingWithoutAReceiptReference() {
    UUID invitationId = invitation();
    RuntimeException failure = new RuntimeException("publication staging failed");
    when(grants.stage(any())).thenThrow(failure);

    assertThatThrownBy(() -> acceptance.accept(invitationId, "clinic", OffsetDateTime.now()))
        .isSameAs(failure);

    Invitation invitation = invitations.findById(invitationId).orElseThrow();
    assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PENDING);
    assertThat(invitation.getGrantReceiptId()).isNull();
  }

  @Test
  void receiptlessAcceptedRowReturnsUnknownAndAcceptanceReplayDoesNotRestage() {
    UUID invitationId = invitation();
    jdbc.update(
        "update invitations set status = 'ACCEPTED', accepted_at = now() where id = ?",
        invitationId);

    assertThat(convergence.get(invitationId, "clinic").status())
        .isEqualTo(InvitationGrantConvergenceStatus.UNKNOWN);
    assertThat(acceptance.accept(invitationId, "clinic", OffsetDateTime.now()).status())
        .isEqualTo(InvitationStatus.ACCEPTED);
    org.mockito.Mockito.verify(grants, org.mockito.Mockito.never()).stage(any());
  }

  @Test
  void acceptanceCommitBeforeRevocationWinsDeterministically() throws Exception {
    UUID invitationId = invitation();
    when(grants.stage(any())).thenReturn(receipt());

    List<Attempt<?>> attempts =
        contend(
            () -> acceptance.accept(invitationId, "clinic", OffsetDateTime.now()),
            () -> invitationService.revoke(invitationId, "clinic"));

    assertThat(attempts.getFirst().succeeded()).isTrue();
    assertConflict(attempts.getLast(), "invitation_accepted");
    assertThat(invitations.findById(invitationId).orElseThrow().getStatus())
        .isEqualTo(InvitationStatus.ACCEPTED);
  }

  @Test
  void revocationCommitBeforeAcceptanceWinsDeterministically() throws Exception {
    UUID invitationId = invitation();
    when(grants.stage(any())).thenReturn(receipt());

    List<Attempt<?>> attempts =
        contend(
            () -> invitationService.revoke(invitationId, "clinic"),
            () -> acceptance.accept(invitationId, "clinic", OffsetDateTime.now()));

    assertThat(attempts.getFirst().succeeded()).isTrue();
    assertConflict(attempts.getLast(), "invitation_revoked");
    assertThat(invitations.findById(invitationId).orElseThrow().getStatus())
        .isEqualTo(InvitationStatus.REVOKED);
    org.mockito.Mockito.verify(grants, org.mockito.Mockito.never()).stage(any());
  }

  @Test
  void revocationCommitBeforeCompletionClaimPersistsRevokedAndPreventsWork() throws Exception {
    UUID invitationId = invitationWithCompletion();

    List<Attempt<?>> attempts =
        contend(
            () -> revocation.revoke(invitationId, "clinic"),
            () -> completionService.claim(invitationId));

    assertThat(attempts.getFirst().succeeded()).isTrue();
    assertThat(attempts.getLast().result()).isEqualTo(java.util.Optional.empty());
    assertThat(invitations.findById(invitationId).orElseThrow().getStatus())
        .isEqualTo(InvitationStatus.REVOKED);
    assertThat(completions.findById(invitationId).orElseThrow().getStatus())
        .isEqualTo(InvitationCompletionStatus.REVOKED);
  }

  @Test
  void completionClaimCommitBeforeRevocationReturnsWorkThenRemainsTerminallyRevoked()
      throws Exception {
    UUID invitationId = invitationWithCompletion();

    List<Attempt<?>> attempts =
        contend(
            () -> completionService.claim(invitationId),
            () -> revocation.revoke(invitationId, "clinic"));

    assertThat(attempts).allMatch(Attempt::succeeded);
    assertThat((java.util.Optional<?>) attempts.getFirst().result()).isPresent();
    assertThat(invitations.findById(invitationId).orElseThrow().getStatus())
        .isEqualTo(InvitationStatus.REVOKED);
    assertThat(completions.findById(invitationId).orElseThrow().getStatus())
        .isEqualTo(InvitationCompletionStatus.REVOKED);

    completionService.markAwaitingIdentity(invitationId, OffsetDateTime.now().plusHours(1));
    completionService.reschedule(invitationId, OffsetDateTime.now().plusHours(1));
    completionService.complete(invitationId);
    completionService.recordFailure(invitationId, new RuntimeException("late provider failure"));
    completionService.recordTerminalFailure(
        invitationId, new RuntimeException("late terminal provider failure"));
    completionService.recordIdentityFailure(invitationId, "late identity failure");

    assertThat(completions.findById(invitationId).orElseThrow().getStatus())
        .isEqualTo(InvitationCompletionStatus.REVOKED);
  }

  private UUID invitation() {
    return transactions.execute(
        ignored ->
            invitations
                .saveAndFlush(
                    new Invitation(
                        UUID.randomUUID(),
                        "clinic",
                        UUID.randomUUID(),
                        "clinic:clinic",
                        "clinic:employee",
                        List.of(new InvitationGrantInput("clinic:clinic", "read")),
                        "user@example.com",
                        UUID.randomUUID(),
                        "subject-1",
                        UUID.randomUUID(),
                        URI.create("https://app.example.com/invitations"),
                        OffsetDateTime.now().plusDays(1),
                        UUID.randomUUID().toString()))
                .getId());
  }

  private UUID invitationWithCompletion() {
    UUID invitationId = invitation();
    transactions.executeWithoutResult(
        ignored -> {
          Invitation invitation = invitations.findById(invitationId).orElseThrow();
          OffsetDateTime now = OffsetDateTime.now();
          completions.saveAndFlush(
              new InvitationCompletionOperation(
                  invitationId,
                  invitation.getInvitedUserId(),
                  invitation.getProduct(),
                  invitation.getExpiresAt(),
                  now));
        });
    return invitationId;
  }

  private GrantReceipt receipt() {
    return new GrantReceipt(UUID.randomUUID(), GrantReceiptStatus.PENDING, null);
  }

  private List<Attempt<?>> contend(Callable<?> holderTask, Callable<?> contenderTask)
      throws Exception {
    CountDownLatch holderReady = new CountDownLatch(1);
    CountDownLatch contenderConnected = new CountDownLatch(1);
    AtomicInteger contenderPid = new AtomicInteger();
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Attempt<?>> holder =
          executor.submit(
              () -> {
                try {
                  Object result =
                      transactions.execute(
                          ignored -> {
                            Object value = call(holderTask);
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
      Future<Attempt<?>> contender =
          executor.submit(
              () -> {
                await(holderReady);
                try {
                  Object result =
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

  private Object call(Callable<?> task) {
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
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
    }
    throw new IllegalStateException("Timed out waiting for PostgreSQL row-lock contention.");
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting for concurrent test operation.");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for concurrent test.", exception);
    }
  }

  private void assertConflict(Attempt<?> attempt, String code) {
    assertThat(attempt.failure())
        .isInstanceOfSatisfying(
            ApiException.class, failure -> assertThat(failure.code()).isEqualTo(code));
  }

  private record Attempt<T>(T result, Throwable failure) {

    boolean succeeded() {
      return failure == null;
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Config {

    @Bean
    Grants grants() {
      return mock(Grants.class);
    }

    @Bean
    InvitationDelivery invitationDelivery() {
      return mock(InvitationDelivery.class);
    }

    @Bean
    InvitationApplicationMapperImpl invitationApplicationMapper() {
      return new InvitationApplicationMapperImpl();
    }

    @Bean
    InvitationGrantPlanner invitationGrantPlanner() {
      return new InvitationGrantPlanner();
    }
  }
}
