package io.github.lutzseverino.cardo.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.billing.model.CustomerResult;
import io.github.lutzseverino.cardo.billing.repository.CustomerProjection;
import io.github.lutzseverino.cardo.billing.repository.CustomerRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerServiceTest {

  @Test
  void returnsAnExistingCustomerFromAProjection() {
    CustomerRepository customers = mock(CustomerRepository.class);
    UUID customerId = UUID.randomUUID();
    UUID subjectId = UUID.randomUUID();
    CustomerProjection projection = projection(customerId, subjectId);
    when(customers.findProjectedBySubjectIdAndProvider(subjectId, "stripe"))
        .thenReturn(Optional.of(projection));

    Optional<CustomerResult> result = new CustomerService(customers).find(subjectId, "stripe");

    assertThat(result).contains(new CustomerResult(customerId, subjectId, "stripe", "cus_1"));
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
