package io.github.lutzseverino.cardo.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.billing.model.Customer;
import io.github.lutzseverino.cardo.billing.model.CustomerResult;
import io.github.lutzseverino.cardo.billing.provider.BillingProvider;
import io.github.lutzseverino.cardo.billing.repository.CustomerProjection;
import io.github.lutzseverino.cardo.billing.repository.CustomerRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class CustomerServiceTest {

  @Test
  void returnsAnExistingCustomerFromAProjectionWithoutMaterializingOrProvisioning() {
    CustomerRepository customers = mock(CustomerRepository.class);
    BillingProvider provider = mock(BillingProvider.class);
    UUID customerId = UUID.randomUUID();
    UUID subjectId = UUID.randomUUID();
    CustomerProjection projection = projection(customerId, subjectId);
    when(provider.name()).thenReturn("stripe");
    when(customers.findProjectedBySubjectIdAndProvider(subjectId, "stripe"))
        .thenReturn(Optional.of(projection));

    CustomerResult result = new CustomerService(customers, provider).getOrCreate(subjectId);

    assertThat(result).isEqualTo(new CustomerResult(customerId, subjectId, "stripe", "cus_1"));
    verify(provider, never()).createCustomer(subjectId);
  }

  @Test
  void deletesProviderCustomerWhenTheOwningTransactionRollsBackAfterCreation() {
    CustomerRepository customers = mock(CustomerRepository.class);
    BillingProvider provider = mock(BillingProvider.class);
    UUID subjectId = UUID.randomUUID();
    when(provider.name()).thenReturn("stripe");
    when(provider.createCustomer(subjectId)).thenReturn("cus_1");
    when(customers.findProjectedBySubjectIdAndProvider(subjectId, "stripe"))
        .thenReturn(Optional.empty());
    when(customers.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

    TransactionSynchronizationManager.initSynchronization();
    try {
      new CustomerService(customers, provider).getOrCreate(subjectId);
      verify(provider, never()).deleteCustomer("cus_1");

      TransactionSynchronizationManager.getSynchronizations()
          .forEach(
              synchronization ->
                  synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

      verify(provider).deleteCustomer("cus_1");
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  private CustomerProjection projection(UUID customerId, UUID subjectId) {
    return new CustomerProjection() {
      public UUID getId() {
        return customerId;
      }

      public UUID getSubjectId() {
        return subjectId;
      }

      public String getProvider() {
        return "stripe";
      }

      public String getProviderCustomerId() {
        return "cus_1";
      }
    };
  }
}
