package io.github.lutzseverino.cardo.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
  void reusesTheProviderCustomerWhenPersistenceIsRetried() {
    CustomerRepository customers = mock(CustomerRepository.class);
    BillingProvider provider = mock(BillingProvider.class);
    UUID subjectId = UUID.randomUUID();
    when(provider.name()).thenReturn("stripe");
    when(provider.createCustomer(subjectId)).thenReturn("cus_1");
    when(customers.findProjectedBySubjectIdAndProvider(subjectId, "stripe"))
        .thenReturn(Optional.empty());
    when(customers.save(any(Customer.class)))
        .thenThrow(new IllegalStateException("persistence failed"))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CustomerService service = new CustomerService(customers, provider);
    assertThatThrownBy(() -> service.getOrCreate(subjectId))
        .isInstanceOf(IllegalStateException.class);

    CustomerResult result = service.getOrCreate(subjectId);

    assertThat(result.providerCustomerId()).isEqualTo("cus_1");
    verify(provider, times(2)).createCustomer(subjectId);
    verify(provider, never()).deleteCustomer("cus_1");
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
