package io.github.lutzseverino.cardo.billing.service;

import io.github.lutzseverino.cardo.billing.model.Customer;
import io.github.lutzseverino.cardo.billing.repository.CustomerRepository;
import io.github.lutzseverino.cardo.common.api.ApiException;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {

  private final CustomerRepository customers;

  @Transactional
  public Customer getOrCreate(
      UUID subjectId, String provider, Supplier<String> providerCustomerId) {
    return customers
        .findBySubjectIdAndProvider(subjectId, provider)
        .orElseGet(
            () -> customers.save(Customer.create(subjectId, provider, providerCustomerId.get())));
  }

  public Customer getByProviderCustomerId(String provider, String providerCustomerId) {
    return customers
        .findByProviderAndProviderCustomerId(provider, providerCustomerId)
        .orElseThrow(
            () ->
                ApiException.badRequest(
                    "billing_customer_not_found", "Billing customer not found."));
  }
}
