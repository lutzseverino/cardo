package io.github.lutzseverino.cardo.authorization.grant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class GrantReceiptStoreTest {

  private JdbcTemplate jdbc;
  private GrantReceiptStore receipts;
  private TransactionTemplate transactions;

  @BeforeEach
  void setUp() {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:grant-receipts-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("CREATE SCHEMA authorization_events");
    jdbc.execute(
        """
        CREATE TABLE authorization_events.grant_receipt (
          id UUID PRIMARY KEY,
          status VARCHAR(16) NOT NULL,
          failure_code VARCHAR(128),
          attempt_count INT NOT NULL,
          created_at TIMESTAMP WITH TIME ZONE NOT NULL,
          updated_at TIMESTAMP WITH TIME ZONE NOT NULL
        )
        """);
    receipts = new GrantReceiptStore(jdbc, "authorization_events");
    transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
  }

  @Test
  void advancesPendingReceiptToStableFailureAfterBoundedAttempts() {
    UUID receiptId = UUID.randomUUID();
    receipts.create(receiptId, GrantReceiptStatus.PENDING);

    assertThat(receipts.recordFailure(receiptId, 3)).isFalse();
    assertThat(receipts.recordFailure(receiptId, 3)).isFalse();
    assertThat(receipts.recordFailure(receiptId, 3)).isTrue();
    assertThat(receipts.find(receiptId))
        .contains(
            new GrantReceipt(
                receiptId,
                GrantReceiptStatus.FAILED,
                GrantReceiptStore.PROVIDER_APPLICATION_FAILED));
    assertThat(receipts.recordFailure(receiptId, 3)).isTrue();
  }

  @Test
  void appliedTransitionIsIdempotent() {
    UUID receiptId = UUID.randomUUID();
    receipts.create(receiptId, GrantReceiptStatus.PENDING);

    receipts.markApplied(receiptId);
    receipts.markApplied(receiptId);

    assertThat(receipts.find(receiptId))
        .contains(new GrantReceipt(receiptId, GrantReceiptStatus.APPLIED, null));
  }

  @Test
  void receiptInsertParticipatesInTheCallingTransaction() {
    UUID receiptId = UUID.randomUUID();

    transactions.executeWithoutResult(
        transaction -> {
          receipts.create(receiptId, GrantReceiptStatus.PENDING);
          transaction.setRollbackOnly();
        });

    assertThat(receipts.find(receiptId)).isEmpty();
  }

  @Test
  void realGrantStagingStoresTheReceiptPublishedWithThePlan() {
    List<Object> events = new ArrayList<>();
    Grants grants = new Grants(events::add, receipts);
    GrantPlan plan = nonEmptyPlan();

    GrantReceipt receipt = transactions.execute(ignored -> grants.stage(plan));

    assertThat(receipts.find(receipt.id())).contains(receipt);
    assertThat(events)
        .singleElement()
        .isInstanceOfSatisfying(
            StagedGrantPlan.class,
            event -> {
              assertThat(event.receiptId()).isEqualTo(receipt.id());
              assertThat(event.plan()).isEqualTo(plan);
            });
  }

  @Test
  void realGrantStagingRollsBackTheReceiptWhenPublicationFails() {
    List<Object> events = new ArrayList<>();
    RuntimeException failure = new RuntimeException("publication failed");
    Grants grants =
        new Grants(
            event -> {
              events.add(event);
              throw failure;
            },
            receipts);

    assertThatThrownBy(
            () -> transactions.executeWithoutResult(ignored -> grants.stage(nonEmptyPlan())))
        .isSameAs(failure);

    assertThat(events)
        .singleElement()
        .isInstanceOfSatisfying(
            StagedGrantPlan.class, event -> assertThat(receipts.find(event.receiptId())).isEmpty());
  }

  private GrantPlan nonEmptyPlan() {
    return new GrantPlan(
        List.of(),
        List.of(),
        List.of(new GrantPlan.AuthorityGrant("identity", "subject-1", List.of("profile:read"))));
  }
}
