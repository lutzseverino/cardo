package io.github.lutzseverino.cardo.integration.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.authorization.grant.GrantReceipt;
import io.github.lutzseverino.cardo.authorization.grant.GrantReceiptStatus;
import io.github.lutzseverino.cardo.authorization.grant.Grants;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

class ReferenceAcceptancePostgreSqlIntegrationTest {

  @Test
  void repeatedAndConcurrentDeliveryCommitOneTransitionReceiptAndPublication() throws Exception {
    try (PostgreSQLContainer postgres =
        new PostgreSQLContainer(System.getProperty("cardo.test.postgres.image"))
            .withDatabaseName("reference")
            .withUsername("reference")
            .withPassword("reference")) {
      postgres.start();
      PGSimpleDataSource dataSource = new PGSimpleDataSource();
      dataSource.setURL(postgres.getJdbcUrl());
      dataSource.setUser(postgres.getUsername());
      dataSource.setPassword(postgres.getPassword());
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      createSchema(jdbc);
      TransactionTemplate transactions =
          new TransactionTemplate(new DataSourceTransactionManager(dataSource));

      JdbcReferenceProductStore store = new JdbcReferenceProductStore(jdbc);
      UUID invitationId = UUID.fromString("34000000-0000-0000-0000-000000000030");
      UUID receiptId = UUID.fromString("34000000-0000-0000-0000-000000000031");
      UUID invitedBy = UUID.fromString("34000000-0000-0000-0000-000000000032");
      transactions.execute(
          ignored -> store.createInvitation(invitationId, "invited@example.test", invitedBy));
      transactions.execute(
          ignored -> store.createInvitation(invitationId, "invited@example.test", invitedBy));
      assertThat(jdbc.queryForObject("select count(*) from reference_invitation", Long.class))
          .isOne();
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from reference_command where command_type = 'CREATE'",
                  Long.class))
          .isOne();
      assertThatThrownBy(
              () ->
                  transactions.execute(
                      ignored ->
                          store.createInvitation(invitationId, "changed@example.test", invitedBy)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Invitation request identifier changed meaning.");
      store.completeCommand(store.nextCommand().orElseThrow().id());
      OffsetDateTime acceptedAt = OffsetDateTime.of(2026, 7, 22, 12, 0, 0, 0, ZoneOffset.UTC);
      transactions.executeWithoutResult(
          ignored -> store.recordAcceptanceIntent(invitationId, "provider-subject-34", acceptedAt));
      transactions.executeWithoutResult(
          ignored -> store.recordAcceptanceIntent(invitationId, "provider-subject-34", acceptedAt));
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from reference_command where command_type = 'ACCEPT'",
                  Long.class))
          .isOne();
      ReferenceProductStore.ReferenceCommand command = store.nextCommand().orElseThrow();

      Grants grants = mock(Grants.class);
      when(grants.stage(any()))
          .thenAnswer(
              invocation -> {
                jdbc.update(
                    "insert into reference_test_publication(receipt_id) values (?)", receiptId);
                return new GrantReceipt(receiptId, GrantReceiptStatus.PENDING, null);
              });
      ReferenceAcceptanceCommitter committer = new ReferenceAcceptanceCommitter(store, grants);
      CountDownLatch start = new CountDownLatch(1);
      try (var workers = Executors.newFixedThreadPool(2)) {
        var first =
            workers.submit(
                () -> {
                  start.await();
                  transactions.executeWithoutResult(ignored -> committer.complete(command));
                  return null;
                });
        var second =
            workers.submit(
                () -> {
                  start.await();
                  transactions.executeWithoutResult(ignored -> committer.complete(command));
                  return null;
                });
        start.countDown();
        first.get();
        second.get();
      }

      assertThat(store.membershipCount("provider-subject-34")).isOne();
      assertThat(store.receiptCount(invitationId)).isOne();
      assertThat(jdbc.queryForObject("select count(*) from reference_test_publication", Long.class))
          .isOne();
      assertThat(store.nextCommand()).isEmpty();
      verify(grants, times(1)).stage(any());
    }
  }

  private void createSchema(JdbcTemplate jdbc) {
    jdbc.execute(
        "create table reference_invitation (id uuid primary key, request_id uuid not null unique, email varchar(320) not null, invited_by uuid not null, remote_invitation_id uuid unique, acceptance_intent_at timestamp with time zone, accepted_subject varchar(255), accepted_at timestamp with time zone, receipt_id uuid unique, created_at timestamp with time zone not null default current_timestamp, updated_at timestamp with time zone not null default current_timestamp)");
    jdbc.execute(
        "create table reference_membership (tenant_id uuid not null, subject varchar(255) not null, created_at timestamp with time zone not null default current_timestamp, primary key (tenant_id, subject))");
    jdbc.execute(
        "create table reference_command (id uuid primary key, command_type varchar(16) not null, invitation_id uuid not null references reference_invitation(id), accepted_subject varchar(255), accepted_at timestamp with time zone, completed_at timestamp with time zone, created_at timestamp with time zone not null default current_timestamp, unique (command_type, invitation_id))");
    jdbc.execute("create table reference_test_publication (receipt_id uuid primary key)");
  }
}
