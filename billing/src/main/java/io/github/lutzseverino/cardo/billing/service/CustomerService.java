package io.github.lutzseverino.cardo.billing.service;

import io.github.lutzseverino.cardo.billing.model.CustomerResult;
import io.github.lutzseverino.cardo.billing.repository.CustomerProjection;
import io.github.lutzseverino.cardo.billing.repository.CustomerRepository;
import io.github.lutzseverino.cardo.common.api.ApiException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerService {

  private final CustomerRepository customers;

  public Optional<CustomerResult> find(UUID subjectId, String provider) {
    return customers.findProjectedBySubjectIdAndProvider(subjectId, provider).map(this::toResult);
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

  private CustomerResult toResult(CustomerProjection customer) {
    return new CustomerResult(
        customer.getId(),
        customer.getSubjectId(),
        customer.getProvider(),
        customer.getProviderCustomerId());
  }
}
