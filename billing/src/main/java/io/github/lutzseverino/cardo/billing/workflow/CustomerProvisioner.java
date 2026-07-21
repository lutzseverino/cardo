package io.github.lutzseverino.cardo.billing.workflow;

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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class CustomerProvisioner {

  private static final Logger logger = LoggerFactory.getLogger(CustomerProvisioner.class);
  private static final String UNRESOLVED =
      "No provider customer is visible for the durable provisioning marker.";
  private static final String DUPLICATE =
      "Multiple provider customers share the durable provisioning marker.";
  private static final String MISMATCH =
      "The provider customer does not match the existing local customer mapping.";

  private final BillingProvider provider;
  private final CustomerService customers;
  private final CustomerProvisioningService operations;

  CustomerResult getOrCreate(UUID subjectId) {
    Optional<CustomerResult> existing = customers.find(subjectId, provider.name());
    if (existing.isPresent()) {
      return existing.orElseThrow();
    }

    UUID operationId;
    try {
      operationId = operations.request(subjectId, provider.name());
    } catch (DataIntegrityViolationException conflict) {
      Optional<CustomerResult> concurrent = customers.find(subjectId, provider.name());
      if (concurrent.isPresent()) {
        return concurrent.orElseThrow();
      }
      operationId = operations.activeOperationId(subjectId, provider.name());
    }

    reconcileNow(operationId);
    return customers.find(subjectId, provider.name()).orElseThrow(this::unavailable);
  }

  @Scheduled(fixedDelayString = "${cardo.billing.customer-provisioning.dispatch-delay:PT5S}")
  void reconcile() {
    operations.readyIds().forEach(this::reconcileInBackground);
  }

  void reconcileNow(UUID operationId) {
    operations.claim(operationId).ifPresent(this::processSynchronously);
  }

  void reconcileInBackground(UUID operationId) {
    operations.claim(operationId).ifPresent(this::processInBackground);
  }

  private void processSynchronously(CustomerProvisioningWork work) {
    try {
      process(work);
    } catch (RuntimeException failure) {
      recordRetryableFailure(work, failure);
      throw transportFailure(failure);
    }
  }

  private void processInBackground(CustomerProvisioningWork work) {
    try {
      process(work);
    } catch (RuntimeException failure) {
      recordRetryableFailure(work, failure);
    }
  }

  private void process(CustomerProvisioningWork work) {
    List<String> providerCustomerIds =
        work.firstRemoteAttempt()
            ? List.of(provider.createCustomer(work.subjectId(), work.id()))
            : provider.findCustomersByProvisioningId(work.id());
    if (providerCustomerIds.isEmpty()) {
      throw new UnresolvedProvisioningException(UNRESOLVED);
    }
    if (providerCustomerIds.size() > 1) {
      if (!operations.recordTerminalFailure(work.id(), work.leaseToken(), DUPLICATE)) {
        logStale(work);
        return;
      }
      logTerminal(work, DUPLICATE);
      throw new TerminalProvisioningException(DUPLICATE);
    }

    String providerCustomerId = providerCustomerIds.getFirst();
    CustomerProvisioningCompletion completion;
    try {
      completion = operations.complete(work.id(), work.leaseToken(), providerCustomerId);
    } catch (DataIntegrityViolationException conflict) {
      completion =
          operations.convergeAfterLocalConflict(work.id(), work.leaseToken(), providerCustomerId);
    }
    if (CustomerProvisioningCompletion.STALE.equals(completion)) {
      logStale(work);
      return;
    }
    if (CustomerProvisioningCompletion.MISMATCH.equals(completion)) {
      logTerminal(work, MISMATCH);
      throw new TerminalProvisioningException(MISMATCH);
    }
    logger
        .atInfo()
        .addKeyValue("operationId", work.id())
        .addKeyValue("subjectId", work.subjectId())
        .addKeyValue("provider", work.provider())
        .addKeyValue("providerCustomerId", providerCustomerId)
        .log("Billing customer provisioning completed");
  }

  private void recordRetryableFailure(CustomerProvisioningWork work, RuntimeException failure) {
    if (failure instanceof TerminalProvisioningException) {
      return;
    }
    CustomerProvisioningFailure outcome =
        operations.recordFailure(work.id(), work.leaseToken(), failure);
    if (CustomerProvisioningFailure.STALE.equals(outcome)) {
      logStale(work);
      return;
    }
    if (CustomerProvisioningFailure.TERMINAL.equals(outcome)) {
      logTerminal(work, failure.getMessage());
      return;
    }
    logger
        .atWarn()
        .addKeyValue("operationId", work.id())
        .addKeyValue("subjectId", work.subjectId())
        .addKeyValue("provider", work.provider())
        .setCause(failure)
        .log("Billing customer provisioning failed and will be retried if attempts remain");
  }

  private void logTerminal(CustomerProvisioningWork work, String reason) {
    logger
        .atError()
        .addKeyValue("operationId", work.id())
        .addKeyValue("subjectId", work.subjectId())
        .addKeyValue("provider", work.provider())
        .addKeyValue("reason", reason)
        .log("Billing customer provisioning requires operator inspection");
  }

  private void logStale(CustomerProvisioningWork work) {
    logger
        .atInfo()
        .addKeyValue("operationId", work.id())
        .addKeyValue("leaseToken", work.leaseToken())
        .addKeyValue("subjectId", work.subjectId())
        .addKeyValue("provider", work.provider())
        .log("Ignored stale billing customer provisioning acknowledgement");
  }

  private ApiException transportFailure(RuntimeException failure) {
    if (failure instanceof ApiException api) {
      return api;
    }
    return unavailable();
  }

  private ApiException unavailable() {
    return ApiException.of(502, "billing_customer_create_failed", "Customer could not be created.");
  }

  private static final class UnresolvedProvisioningException extends RuntimeException {
    private UnresolvedProvisioningException(String message) {
      super(message);
    }
  }

  private static final class TerminalProvisioningException extends RuntimeException {
    private TerminalProvisioningException(String message) {
      super(message);
    }
  }
}
