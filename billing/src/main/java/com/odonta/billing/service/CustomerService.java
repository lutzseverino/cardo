package com.odonta.billing.service;

import com.odonta.billing.model.Customer;
import com.odonta.billing.repository.CustomerRepository;
import com.odonta.common.api.ApiException;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.param.CustomerCreateParams;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CustomerService {

  private final CustomerRepository customers;
  private final StripeClient stripe;

  CustomerService(CustomerRepository customers, StripeClient stripe) {
    this.customers = customers;
    this.stripe = stripe;
  }

  public Customer getOrCreate(UUID subjectId) {
    return customers.findBySubjectId(subjectId).orElseGet(() -> create(subjectId));
  }

  private Customer create(UUID subjectId) {
    try {
      com.stripe.model.Customer stripeCustomer =
          stripe
              .v1()
              .customers()
              .create(
                  CustomerCreateParams.builder()
                      .putMetadata("subject_id", subjectId.toString())
                      .build());
      return customers.save(Customer.create(subjectId, stripeCustomer.getId()));
    } catch (StripeException exception) {
      throw ApiException.of(
          502, "stripe_customer_create_failed", "Stripe customer could not be created.");
    }
  }
}
