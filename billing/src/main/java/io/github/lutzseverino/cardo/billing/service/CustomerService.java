package io.github.lutzseverino.cardo.billing.service;

import io.github.lutzseverino.cardo.billing.model.Customer;
import io.github.lutzseverino.cardo.billing.model.CustomerResult;
import io.github.lutzseverino.cardo.billing.provider.BillingProvider;
import io.github.lutzseverino.cardo.billing.repository.CustomerProjection;
import io.github.lutzseverino.cardo.billing.repository.CustomerRepository;
import io.github.lutzseverino.cardo.common.api.ApiException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class CustomerService {

  private static final Logger logger = LoggerFactory.getLogger(CustomerService.class);

  private final CustomerRepository customers;
  private final BillingProvider provider;

  @Transactional
  public CustomerResult getOrCreate(UUID subjectId) {
    return customers
        .findProjectedBySubjectIdAndProvider(subjectId, provider.name())
        .map(this::toResult)
        .orElseGet(() -> create(subjectId));
  }

  public CustomerResult getByProviderCustomerId(String provider, String providerCustomerId) {
    return customers
        .findProjectedByProviderAndProviderCustomerId(provider, providerCustomerId)
        .map(this::toResult)
        .orElseThrow(
            () ->
                ApiException.badRequest(
                    "billing_customer_not_found", "Billing customer not found."));
  }

  private CustomerResult toResult(Customer customer) {
    return new CustomerResult(
        customer.getId(),
        customer.getSubjectId(),
        customer.getProvider(),
        customer.getProviderCustomerId());
  }

  private CustomerResult create(UUID subjectId) {
    String providerCustomerId = provider.createCustomer(subjectId);
    AtomicBoolean compensated = deleteCustomerOnRollback(providerCustomerId);
    try {
      return toResult(
          customers.save(Customer.create(subjectId, provider.name(), providerCustomerId)));
    } catch (RuntimeException exception) {
      deleteCustomer(providerCustomerId, compensated, exception);
      throw exception;
    }
  }

  private CustomerResult toResult(CustomerProjection customer) {
    return new CustomerResult(
        customer.getId(),
        customer.getSubjectId(),
        customer.getProvider(),
        customer.getProviderCustomerId());
  }

  private AtomicBoolean deleteCustomerOnRollback(String providerCustomerId) {
    AtomicBoolean compensated = new AtomicBoolean();
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      return compensated;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED && compensated.compareAndSet(false, true)) {
              try {
                provider.deleteCustomer(providerCustomerId);
              } catch (RuntimeException exception) {
                logger.error(
                    "Failed to delete provider customer {} after rollback",
                    providerCustomerId,
                    exception);
              }
            }
          }
        });
    return compensated;
  }

  private void deleteCustomer(
      String providerCustomerId, AtomicBoolean compensated, RuntimeException original) {
    if (!compensated.compareAndSet(false, true)) {
      return;
    }
    try {
      provider.deleteCustomer(providerCustomerId);
    } catch (RuntimeException compensationFailure) {
      original.addSuppressed(compensationFailure);
    }
  }
}
