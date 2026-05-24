package com.odonta.billing.service;

import com.odonta.billing.model.Customer;
import com.odonta.billing.repository.CustomerRepository;
import com.odonta.common.api.ApiException;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class CustomerService {

  private final CustomerRepository customers;

  CustomerService(CustomerRepository customers) {
    this.customers = customers;
  }

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
