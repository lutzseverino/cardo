package io.github.lutzseverino.cardo.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.billing.config.CustomerProvisioningProperties;
import io.github.lutzseverino.cardo.billing.model.CustomerProvisioningCompletion;
import io.github.lutzseverino.cardo.billing.model.CustomerProvisioningFailure;
import io.github.lutzseverino.cardo.billing.model.CustomerProvisioningOperation;
import io.github.lutzseverino.cardo.billing.model.CustomerProvisioningStatus;
import io.github.lutzseverino.cardo.billing.repository.CustomerProvisioningOperationRepository;
import io.github.lutzseverino.cardo.billing.repository.CustomerRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerProvisioningServiceTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-21T10:00:00Z");

  @Test
  void terminalHistoryBlocksAutomaticReplacementProvisioning() {
    CustomerProvisioningOperationRepository operations =
        mock(CustomerProvisioningOperationRepository.class);
    UUID subjectId = UUID.randomUUID();
    UUID operationId = UUID.randomUUID();
    CustomerProvisioningOperation failed =
        CustomerProvisioningOperation.request(
            operationId, subjectId, "stripe", OffsetDateTime.now());
    failed.failTerminal("ambiguous provider result", OffsetDateTime.now());
    when(operations.findFirstEntityBySubjectIdAndProviderOrderByCreatedAtDesc(subjectId, "stripe"))
        .thenReturn(Optional.of(failed));
    CustomerProvisioningService service =
        new CustomerProvisioningService(
            operations,
            mock(CustomerRepository.class),
            new CustomerProvisioningProperties(
                Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofMinutes(1), 6, 50));

    assertThat(service.request(subjectId, "stripe")).isEqualTo(operationId);

    verify(operations, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void staleFailureCannotDowngradeACompletedOperation() {
    CustomerProvisioningOperationRepository operations =
        mock(CustomerProvisioningOperationRepository.class);
    CustomerRepository customers = mock(CustomerRepository.class);
    UUID operationId = UUID.randomUUID();
    UUID leaseToken = UUID.randomUUID();
    CustomerProvisioningOperation operation = operation(operationId);
    operation.claim(leaseToken, OffsetDateTime.now(), Duration.ofMinutes(10));
    operation.complete("cus_new", NOW.plusSeconds(5));
    when(operations.findEntityByIdForUpdate(operationId)).thenReturn(Optional.of(operation));
    CustomerProvisioningService service = service(operations, customers);

    assertThat(
            service.recordFailure(
                operationId, leaseToken, new IllegalStateException("late timeout")))
        .isEqualTo(CustomerProvisioningFailure.STALE);

    assertThat(operation.getStatus()).isEqualTo(CustomerProvisioningStatus.COMPLETED);
    assertThat(operation.getProviderCustomerId()).isEqualTo("cus_new");
    verifyNoInteractions(customers);
  }

  @Test
  void expiredLeaseCannotAcknowledgeWithoutANewerClaim() {
    CustomerProvisioningOperationRepository operations =
        mock(CustomerProvisioningOperationRepository.class);
    CustomerRepository customers = mock(CustomerRepository.class);
    UUID operationId = UUID.randomUUID();
    UUID expiredLease = UUID.randomUUID();
    CustomerProvisioningOperation operation = operation(operationId);
    operation.claim(expiredLease, OffsetDateTime.now().minusMinutes(2), Duration.ofMinutes(1));
    when(operations.findEntityByIdForUpdate(operationId)).thenReturn(Optional.of(operation));
    CustomerProvisioningService service = service(operations, customers);

    assertThat(service.recordTerminalFailure(operationId, expiredLease, "late duplicate"))
        .isFalse();

    assertThat(operation.getStatus()).isEqualTo(CustomerProvisioningStatus.REQUESTED);
    assertThat(operation.getAttemptCount()).isZero();
    verifyNoInteractions(customers);
  }

  @Test
  void expiredWorkerCannotOverwriteTheNewLeaseCompletion() {
    CustomerProvisioningOperationRepository operations =
        mock(CustomerProvisioningOperationRepository.class);
    CustomerRepository customers = mock(CustomerRepository.class);
    UUID operationId = UUID.randomUUID();
    UUID staleLease = UUID.randomUUID();
    UUID currentLease = UUID.randomUUID();
    CustomerProvisioningOperation operation = operation(operationId);
    OffsetDateTime current = OffsetDateTime.now();
    operation.claim(staleLease, current.minusMinutes(2), Duration.ofMinutes(1));
    operation.claim(currentLease, current, Duration.ofMinutes(10));
    when(operations.findEntityByIdForUpdate(operationId)).thenReturn(Optional.of(operation));
    when(customers.findEntityBySubjectIdAndProvider(operation.getSubjectId(), "stripe"))
        .thenReturn(Optional.empty());
    when(customers.findEntityByProviderAndProviderCustomerId("stripe", "cus_new"))
        .thenReturn(Optional.empty());
    CustomerProvisioningService service = service(operations, customers);

    assertThat(service.complete(operationId, staleLease, "cus_stale"))
        .isEqualTo(CustomerProvisioningCompletion.STALE);
    verifyNoInteractions(customers);

    assertThat(service.complete(operationId, currentLease, "cus_new"))
        .isEqualTo(CustomerProvisioningCompletion.COMPLETED);
    assertThat(operation.getProviderCustomerId()).isEqualTo("cus_new");
  }

  private CustomerProvisioningOperation operation(UUID operationId) {
    return CustomerProvisioningOperation.request(operationId, UUID.randomUUID(), "stripe", NOW);
  }

  private CustomerProvisioningService service(
      CustomerProvisioningOperationRepository operations, CustomerRepository customers) {
    return new CustomerProvisioningService(
        operations,
        customers,
        new CustomerProvisioningProperties(
            Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofMinutes(1), 6, 50));
  }
}
