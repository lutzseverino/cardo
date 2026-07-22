package io.github.lutzseverino.cardo.billing.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.lutzseverino.cardo.billing.model.Customer;
import io.github.lutzseverino.cardo.billing.model.CustomerProvisioningCompletion;
import io.github.lutzseverino.cardo.billing.model.CustomerProvisioningOperation;
import io.github.lutzseverino.cardo.billing.model.CustomerProvisioningStatus;
import io.github.lutzseverino.cardo.billing.operations.BillingWorkflowMetrics;
import io.github.lutzseverino.cardo.billing.service.CustomerProvisioningService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest(
    properties = {
      "spring.flyway.locations=classpath:db/migration",
      "spring.jpa.hibernate.ddl-auto=validate",
      "cardo.billing.customer-provisioning.dispatch-delay=5s",
      "cardo.billing.customer-provisioning.retry-base-delay=10s",
      "cardo.billing.customer-provisioning.claim-lease=1m",
      "cardo.billing.customer-provisioning.max-attempts=6",
      "cardo.billing.customer-provisioning.batch-size=50"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import(CustomerProvisioningService.class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CustomerProvisioningPostgreSqlIntegrationTest {

  @MockitoBean private BillingWorkflowMetrics metrics;

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-21T10:00:00Z");

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.5-alpine")
          .withDatabaseName("billing")
          .withUsername("billing")
          .withPassword("billing");

  @Autowired private CustomerProvisioningOperationRepository operations;
  @Autowired private CustomerRepository customers;
  @Autowired private CustomerProvisioningService service;
  @Autowired private JdbcTemplate jdbc;

  @DynamicPropertySource
  static void configurePostgres(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    properties.add("spring.datasource.username", POSTGRES::getUsername);
    properties.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @BeforeEach
  void clearDatabase() {
    operations.deleteAll();
    customers.deleteAll();
  }

  @Test
  void migrationAddsTheDurableOperationAndActiveAndReadyIndexes() {
    assertThat(
            jdbc.queryForList(
                "select version from flyway_schema_history_billing where success order by installed_rank",
                String.class))
        .containsExactly("1", "2", "3");
    assertThat(
            jdbc.queryForList(
                "select indexname from pg_indexes where tablename = 'billing_customer_provisioning_operations'",
                String.class))
        .contains(
            "uq_billing_customer_provisioning_active",
            "ix_billing_customer_provisioning_ready",
            "idx_billing_customer_provisioning_terminal");
  }

  @Test
  void partialUniquenessRejectsTwoActiveOperationsButRetainsTerminalHistory() {
    UUID subjectId = UUID.randomUUID();
    CustomerProvisioningOperation first = operation(UUID.randomUUID(), subjectId);
    operations.saveAndFlush(first);

    assertThatThrownBy(() -> operations.saveAndFlush(operation(UUID.randomUUID(), subjectId)))
        .isInstanceOf(DataIntegrityViolationException.class);

    operations.deleteAll();
    CustomerProvisioningOperation failed = operation(UUID.randomUUID(), subjectId);
    failed.failTerminal("operator inspection", NOW);
    CustomerProvisioningOperation completed = operation(UUID.randomUUID(), subjectId);
    completed.complete("cus_history", NOW);
    operations.saveAndFlush(failed);
    operations.saveAndFlush(completed);
    operations.saveAndFlush(operation(UUID.randomUUID(), subjectId));

    assertThat(operations.findAll())
        .extracting(CustomerProvisioningOperation::getStatus)
        .containsExactlyInAnyOrder(
            CustomerProvisioningStatus.FAILED,
            CustomerProvisioningStatus.COMPLETED,
            CustomerProvisioningStatus.REQUESTED);
  }

  @Test
  void concurrentClaimsLeaseTheOperationToOnlyOneWorker() throws Exception {
    CustomerProvisioningOperation operation =
        operations.saveAndFlush(operation(UUID.randomUUID(), UUID.randomUUID()));
    CountDownLatch start = new CountDownLatch(1);

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Optional<?>> first = executor.submit(() -> claimAfter(start, operation.getId()));
      Future<Optional<?>> second = executor.submit(() -> claimAfter(start, operation.getId()));
      start.countDown();

      assertThat(List.of(first.get(), second.get())).filteredOn(Optional::isPresent).hasSize(1);
    }
    assertThat(operations.findById(operation.getId()).orElseThrow().getRemoteAttemptedAt())
        .isNotNull();
  }

  @Test
  void completionConvergesToMatchingMappingAndFailsTerminalOnMismatch() {
    UUID subjectId = UUID.randomUUID();
    CustomerProvisioningOperation matching =
        operations.saveAndFlush(operation(UUID.randomUUID(), subjectId));
    customers.saveAndFlush(Customer.create(subjectId, "stripe", "cus_1"));
    UUID matchingLease = service.claim(matching.getId()).orElseThrow().leaseToken();

    assertThat(service.complete(matching.getId(), matchingLease, "cus_1"))
        .isEqualTo(CustomerProvisioningCompletion.COMPLETED);

    UUID otherSubject = UUID.randomUUID();
    CustomerProvisioningOperation mismatch =
        operations.saveAndFlush(operation(UUID.randomUUID(), otherSubject));
    customers.saveAndFlush(Customer.create(otherSubject, "stripe", "cus_existing"));
    UUID mismatchLease = service.claim(mismatch.getId()).orElseThrow().leaseToken();

    assertThat(service.complete(mismatch.getId(), mismatchLease, "cus_remote"))
        .isEqualTo(CustomerProvisioningCompletion.MISMATCH);
    assertThat(operations.findById(mismatch.getId()).orElseThrow().getStatus())
        .isEqualTo(CustomerProvisioningStatus.FAILED);
  }

  private Optional<?> claimAfter(CountDownLatch start, UUID operationId) throws Exception {
    start.await();
    return service.claim(operationId);
  }

  private CustomerProvisioningOperation operation(UUID operationId, UUID subjectId) {
    return CustomerProvisioningOperation.request(operationId, subjectId, "stripe", NOW);
  }
}
