package io.github.lutzseverino.cardo.billing.service;

import io.github.lutzseverino.cardo.billing.config.CustomerProvisioningProperties;
import io.github.lutzseverino.cardo.billing.model.Customer;
import io.github.lutzseverino.cardo.billing.model.CustomerProvisioningCompletion;
import io.github.lutzseverino.cardo.billing.model.CustomerProvisioningFailure;
import io.github.lutzseverino.cardo.billing.model.CustomerProvisioningOperation;
import io.github.lutzseverino.cardo.billing.model.CustomerProvisioningStatus;
import io.github.lutzseverino.cardo.billing.model.CustomerProvisioningWork;
import io.github.lutzseverino.cardo.billing.repository.CustomerProvisioningOperationRepository;
import io.github.lutzseverino.cardo.billing.repository.CustomerRepository;
import io.github.lutzseverino.cardo.common.api.ApiException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerProvisioningService {

  private static final String MAPPING_MISMATCH =
      "A different local customer mapping already owns the provisioning subject or provider customer.";

  private final Clock clock = Clock.systemUTC();
  private final CustomerProvisioningOperationRepository operations;
  private final CustomerRepository customers;
  private final CustomerProvisioningProperties properties;

  @Transactional
  public UUID request(UUID subjectId, String provider) {
    return operations
        .findFirstEntityBySubjectIdAndProviderOrderByCreatedAtDesc(subjectId, provider)
        .map(CustomerProvisioningOperation::getId)
        .orElseGet(
            () ->
                operations
                    .saveAndFlush(
                        CustomerProvisioningOperation.request(
                            UUID.randomUUID(), subjectId, provider, now()))
                    .getId());
  }

  @Transactional(readOnly = true)
  public UUID activeOperationId(UUID subjectId, String provider) {
    return operations
        .findFirstEntityBySubjectIdAndProviderAndStatus(
            subjectId, provider, CustomerProvisioningStatus.REQUESTED)
        .map(CustomerProvisioningOperation::getId)
        .orElseThrow(
            () ->
                ApiException.of(
                    502, "billing_customer_create_failed", "Customer could not be created."));
  }

  @Transactional(readOnly = true)
  public List<UUID> readyIds() {
    return operations.findReadyIds(now(), PageRequest.of(0, properties.batchSize()));
  }

  @Transactional
  public Optional<CustomerProvisioningWork> claim(UUID operationId) {
    CustomerProvisioningOperation operation = requireLocked(operationId);
    OffsetDateTime now = now();
    if (!operation.ready(now)) {
      return Optional.empty();
    }
    UUID leaseToken = UUID.randomUUID();
    boolean firstRemoteAttempt = operation.claim(leaseToken, now, properties.claimLease());
    return Optional.of(
        new CustomerProvisioningWork(
            operation.getId(),
            operation.getSubjectId(),
            operation.getProvider(),
            leaseToken,
            firstRemoteAttempt));
  }

  @Transactional
  public CustomerProvisioningCompletion complete(
      UUID operationId, UUID leaseToken, String providerCustomerId) {
    CustomerProvisioningOperation operation = requireLocked(operationId);
    if (!operation.ownsLease(leaseToken, now())) {
      return CustomerProvisioningCompletion.STALE;
    }
    Optional<Customer> bySubject =
        customers.findEntityBySubjectIdAndProvider(
            operation.getSubjectId(), operation.getProvider());
    if (bySubject.isPresent()) {
      return completeFromExisting(operation, bySubject.orElseThrow(), providerCustomerId);
    }

    Optional<Customer> byProviderCustomer =
        customers.findEntityByProviderAndProviderCustomerId(
            operation.getProvider(), providerCustomerId);
    if (byProviderCustomer.isPresent()) {
      return completeFromExisting(operation, byProviderCustomer.orElseThrow(), providerCustomerId);
    }

    customers.saveAndFlush(
        Customer.create(operation.getSubjectId(), operation.getProvider(), providerCustomerId));
    operation.complete(providerCustomerId, now());
    return CustomerProvisioningCompletion.COMPLETED;
  }

  @Transactional
  public CustomerProvisioningCompletion convergeAfterLocalConflict(
      UUID operationId, UUID leaseToken, String providerCustomerId) {
    CustomerProvisioningOperation operation = requireLocked(operationId);
    if (!operation.ownsLease(leaseToken, now())) {
      return CustomerProvisioningCompletion.STALE;
    }
    Optional<Customer> bySubject =
        customers.findEntityBySubjectIdAndProvider(
            operation.getSubjectId(), operation.getProvider());
    if (bySubject.isPresent()) {
      return completeFromExisting(operation, bySubject.orElseThrow(), providerCustomerId);
    }
    Optional<Customer> byProviderCustomer =
        customers.findEntityByProviderAndProviderCustomerId(
            operation.getProvider(), providerCustomerId);
    if (byProviderCustomer.isPresent()) {
      return completeFromExisting(operation, byProviderCustomer.orElseThrow(), providerCustomerId);
    }
    operation.failTerminal(MAPPING_MISMATCH, now());
    return CustomerProvisioningCompletion.MISMATCH;
  }

  @Transactional
  public CustomerProvisioningFailure recordFailure(
      UUID operationId, UUID leaseToken, RuntimeException failure) {
    CustomerProvisioningOperation operation = requireLocked(operationId);
    if (!operation.ownsLease(leaseToken, now())) {
      return CustomerProvisioningFailure.STALE;
    }
    operation.fail(
        safeMessage(failure), now(), properties.retryBaseDelay(), properties.maxAttempts());
    return CustomerProvisioningStatus.FAILED.equals(operation.getStatus())
        ? CustomerProvisioningFailure.TERMINAL
        : CustomerProvisioningFailure.RETRY_SCHEDULED;
  }

  @Transactional
  public boolean recordTerminalFailure(UUID operationId, UUID leaseToken, String error) {
    CustomerProvisioningOperation operation = requireLocked(operationId);
    if (!operation.ownsLease(leaseToken, now())) {
      return false;
    }
    operation.failTerminal(error, now());
    return true;
  }

  private CustomerProvisioningCompletion completeFromExisting(
      CustomerProvisioningOperation operation, Customer existing, String providerCustomerId) {
    if (existing.getSubjectId().equals(operation.getSubjectId())
        && existing.getProvider().equals(operation.getProvider())
        && existing.getProviderCustomerId().equals(providerCustomerId)) {
      operation.complete(providerCustomerId, now());
      return CustomerProvisioningCompletion.COMPLETED;
    }
    operation.failTerminal(MAPPING_MISMATCH, now());
    return CustomerProvisioningCompletion.MISMATCH;
  }

  private CustomerProvisioningOperation requireLocked(UUID operationId) {
    return operations
        .findEntityByIdForUpdate(operationId)
        .orElseThrow(
            () ->
                ApiException.of(
                    502, "billing_customer_create_failed", "Customer could not be created."));
  }

  private String safeMessage(RuntimeException failure) {
    String message = failure.getMessage();
    return message == null || message.isBlank()
        ? failure.getClass().getSimpleName()
        : message.substring(0, Math.min(message.length(), 2000));
  }

  private OffsetDateTime now() {
    return OffsetDateTime.now(clock);
  }
}
