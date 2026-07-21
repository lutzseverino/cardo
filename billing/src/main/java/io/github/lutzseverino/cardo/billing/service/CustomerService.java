package io.github.lutzseverino.cardo.billing.service;

import io.github.lutzseverino.cardo.billing.model.Customer;
import io.github.lutzseverino.cardo.billing.model.CustomerResult;
import io.github.lutzseverino.cardo.billing.provider.BillingProvider;
import io.github.lutzseverino.cardo.billing.repository.CustomerProjection;
import io.github.lutzseverino.cardo.billing.repository.CustomerRepository;
import io.github.lutzseverino.cardo.common.api.ApiException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {

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
    return toResult(
        customers.save(Customer.create(subjectId, provider.name(), providerCustomerId)));
  }

  private CustomerResult toResult(CustomerProjection customer) {
    return new CustomerResult(
        customer.getId(),
        customer.getSubjectId(),
        customer.getProvider(),
        customer.getProviderCustomerId());
  }
}
