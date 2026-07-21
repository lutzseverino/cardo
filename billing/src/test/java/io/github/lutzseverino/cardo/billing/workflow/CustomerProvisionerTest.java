package io.github.lutzseverino.cardo.billing.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.billing.model.CustomerProvisioningCompletion;
import io.github.lutzseverino.cardo.billing.model.CustomerProvisioningFailure;
import io.github.lutzseverino.cardo.billing.model.CustomerProvisioningWork;
import io.github.lutzseverino.cardo.billing.model.CustomerResult;
import io.github.lutzseverino.cardo.billing.provider.BillingProvider;
import io.github.lutzseverino.cardo.billing.service.CustomerProvisioningService;
import io.github.lutzseverino.cardo.billing.service.CustomerService;
import io.github.lutzseverino.cardo.common.api.ApiException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class CustomerProvisionerTest {

  private static final UUID SUBJECT_ID = UUID.fromString("97ed77fc-a6b4-445d-a26b-a63f12800be1");
  private static final UUID OPERATION_ID = UUID.fromString("ce129973-ec3c-47f2-acf1-2364295798b7");
  private static final UUID LEASE_TOKEN = UUID.fromString("fd4f05d1-d4ce-4698-b874-a8898959d8f4");

  private final BillingProvider provider = mock(BillingProvider.class);
  private final CustomerService customers = mock(CustomerService.class);
  private final CustomerProvisioningService operations = mock(CustomerProvisioningService.class);
  private final CustomerProvisioner provisioner =
      new CustomerProvisioner(provider, customers, operations);

  @BeforeEach
  void setUp() {
    when(provider.name()).thenReturn("stripe");
  }

  @Test
  void healthyFirstRequestCreatesAndReturnsTheCustomerSynchronously() {
    CustomerResult customer = customer();
    when(customers.find(SUBJECT_ID, "stripe")).thenReturn(Optional.empty(), Optional.of(customer));
    when(operations.request(SUBJECT_ID, "stripe")).thenReturn(OPERATION_ID);
    when(operations.claim(OPERATION_ID)).thenReturn(Optional.of(work(true)));
    when(provider.createCustomer(SUBJECT_ID, OPERATION_ID)).thenReturn("cus_1");
    when(operations.complete(OPERATION_ID, LEASE_TOKEN, "cus_1"))
        .thenReturn(CustomerProvisioningCompletion.COMPLETED);

    assertThat(provisioner.getOrCreate(SUBJECT_ID)).isEqualTo(customer);

    verify(provider).createCustomer(SUBJECT_ID, OPERATION_ID);
    verify(provider).findCustomersBySubjectId(SUBJECT_ID);
    verify(provider, never()).findCustomersByProvisioningId(OPERATION_ID);
  }

  @Test
  void firstDurableAttemptAdoptsALegacyCustomerWithoutCreatingAnother() {
    when(operations.claim(OPERATION_ID)).thenReturn(Optional.of(work(true)));
    when(provider.findCustomersBySubjectId(SUBJECT_ID)).thenReturn(List.of("cus_legacy"));
    when(operations.complete(OPERATION_ID, LEASE_TOKEN, "cus_legacy"))
        .thenReturn(CustomerProvisioningCompletion.COMPLETED);

    provisioner.reconcileInBackground(OPERATION_ID);

    verify(provider).findCustomersBySubjectId(SUBJECT_ID);
    verify(provider, never()).createCustomer(SUBJECT_ID, OPERATION_ID);
  }

  @Test
  void recoveryAfterAnAttemptSearchesByMarkerAndNeverCreatesAgain() {
    when(operations.claim(OPERATION_ID)).thenReturn(Optional.of(work(false)));
    when(provider.findCustomersByProvisioningId(OPERATION_ID)).thenReturn(List.of("cus_1"));
    when(operations.complete(OPERATION_ID, LEASE_TOKEN, "cus_1"))
        .thenReturn(CustomerProvisioningCompletion.COMPLETED);

    provisioner.reconcileInBackground(OPERATION_ID);

    verify(provider).findCustomersByProvisioningId(OPERATION_ID);
    verify(provider, never()).createCustomer(SUBJECT_ID, OPERATION_ID);
  }

  @Test
  void missingRecoveryResultIsRetriedWithoutCreating() {
    when(operations.claim(OPERATION_ID)).thenReturn(Optional.of(work(false)));
    when(provider.findCustomersByProvisioningId(OPERATION_ID)).thenReturn(List.of());

    provisioner.reconcileInBackground(OPERATION_ID);

    verify(operations)
        .recordFailure(
            org.mockito.ArgumentMatchers.eq(OPERATION_ID),
            org.mockito.ArgumentMatchers.eq(LEASE_TOKEN),
            org.mockito.ArgumentMatchers.any());
    verify(provider, never()).createCustomer(SUBJECT_ID, OPERATION_ID);
  }

  @Test
  void retryFallsBackToTheLegacySubjectMarkerWhenTheOperationMarkerIsMissing() {
    when(operations.claim(OPERATION_ID)).thenReturn(Optional.of(work(false)));
    when(provider.findCustomersByProvisioningId(OPERATION_ID)).thenReturn(List.of());
    when(provider.findCustomersBySubjectId(SUBJECT_ID)).thenReturn(List.of("cus_legacy"));
    when(operations.complete(OPERATION_ID, LEASE_TOKEN, "cus_legacy"))
        .thenReturn(CustomerProvisioningCompletion.COMPLETED);

    provisioner.reconcileInBackground(OPERATION_ID);

    verify(provider).findCustomersByProvisioningId(OPERATION_ID);
    verify(provider).findCustomersBySubjectId(SUBJECT_ID);
    verify(provider, never()).createCustomer(SUBJECT_ID, OPERATION_ID);
  }

  @Test
  void duplicateRecoveryResultFailsTerminalForOperatorInspection() {
    when(operations.claim(OPERATION_ID)).thenReturn(Optional.of(work(false)));
    when(provider.findCustomersByProvisioningId(OPERATION_ID))
        .thenReturn(List.of("cus_1", "cus_2"));
    when(operations.recordTerminalFailure(
            OPERATION_ID,
            LEASE_TOKEN,
            "Multiple provider customers share the durable provisioning marker."))
        .thenReturn(true);

    provisioner.reconcileInBackground(OPERATION_ID);

    verify(operations)
        .recordTerminalFailure(
            OPERATION_ID,
            LEASE_TOKEN,
            "Multiple provider customers share the durable provisioning marker.");
    verify(operations, never())
        .recordFailure(
            org.mockito.ArgumentMatchers.eq(OPERATION_ID),
            org.mockito.ArgumentMatchers.eq(LEASE_TOKEN),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void localUniquenessConflictReloadsAndConvergesOnlyWhenTheMappingMatches() {
    when(operations.claim(OPERATION_ID)).thenReturn(Optional.of(work(false)));
    when(provider.findCustomersByProvisioningId(OPERATION_ID)).thenReturn(List.of("cus_1"));
    when(operations.complete(OPERATION_ID, LEASE_TOKEN, "cus_1"))
        .thenThrow(new DataIntegrityViolationException("concurrent mapping"));
    when(operations.convergeAfterLocalConflict(OPERATION_ID, LEASE_TOKEN, "cus_1"))
        .thenReturn(CustomerProvisioningCompletion.COMPLETED);

    provisioner.reconcileInBackground(OPERATION_ID);

    verify(operations).convergeAfterLocalConflict(OPERATION_ID, LEASE_TOKEN, "cus_1");
    verify(operations, never())
        .recordFailure(
            org.mockito.ArgumentMatchers.eq(OPERATION_ID),
            org.mockito.ArgumentMatchers.eq(LEASE_TOKEN),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void unresolvedSynchronousFailureKeepsTheExistingTransportSemantics() {
    when(customers.find(SUBJECT_ID, "stripe")).thenReturn(Optional.empty());
    when(operations.request(SUBJECT_ID, "stripe")).thenReturn(OPERATION_ID);
    when(operations.claim(OPERATION_ID)).thenReturn(Optional.of(work(true)));
    ApiException providerFailure =
        ApiException.of(502, "billing_customer_create_failed", "Customer could not be created.");
    when(provider.createCustomer(SUBJECT_ID, OPERATION_ID)).thenThrow(providerFailure);
    when(operations.recordFailure(OPERATION_ID, LEASE_TOKEN, providerFailure))
        .thenReturn(CustomerProvisioningFailure.RETRY_SCHEDULED);

    assertThatThrownBy(() -> provisioner.getOrCreate(SUBJECT_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            failure -> {
              assertThat(failure.status()).isEqualTo(502);
              assertThat(failure.code()).isEqualTo("billing_customer_create_failed");
            });

    verify(operations).recordFailure(OPERATION_ID, LEASE_TOKEN, providerFailure);
  }

  @Test
  void terminalOrPendingOperationReturnsTheSameFailureWithoutCreatingAgain() {
    when(customers.find(SUBJECT_ID, "stripe")).thenReturn(Optional.empty());
    when(operations.request(SUBJECT_ID, "stripe")).thenReturn(OPERATION_ID);
    when(operations.claim(OPERATION_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> provisioner.getOrCreate(SUBJECT_ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            failure -> {
              assertThat(failure.status()).isEqualTo(502);
              assertThat(failure.code()).isEqualTo("billing_customer_create_failed");
            });

    verify(provider, never()).createCustomer(SUBJECT_ID, OPERATION_ID);
    verify(provider, never()).findCustomersByProvisioningId(OPERATION_ID);
  }

  @Test
  void staleCompletionIsIgnoredWithoutRecordingAnotherFailure() {
    when(operations.claim(OPERATION_ID)).thenReturn(Optional.of(work(false)));
    when(provider.findCustomersByProvisioningId(OPERATION_ID)).thenReturn(List.of("cus_1"));
    when(operations.complete(OPERATION_ID, LEASE_TOKEN, "cus_1"))
        .thenReturn(CustomerProvisioningCompletion.STALE);

    provisioner.reconcileInBackground(OPERATION_ID);

    verify(operations, never())
        .recordFailure(
            org.mockito.ArgumentMatchers.eq(OPERATION_ID),
            org.mockito.ArgumentMatchers.eq(LEASE_TOKEN),
            org.mockito.ArgumentMatchers.any());
  }

  private CustomerProvisioningWork work(boolean firstRemoteAttempt) {
    return new CustomerProvisioningWork(
        OPERATION_ID, SUBJECT_ID, "stripe", LEASE_TOKEN, firstRemoteAttempt);
  }

  private CustomerResult customer() {
    return new CustomerResult(UUID.randomUUID(), SUBJECT_ID, "stripe", "cus_1");
  }
}
