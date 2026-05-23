package com.odonta.billing.service;

import com.odonta.billing.config.StripeProperties;
import com.odonta.billing.model.CheckoutSessionRequest;
import com.odonta.billing.model.CheckoutSessionResponse;
import com.odonta.billing.model.Customer;
import com.odonta.common.api.ApiException;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CheckoutSessionService {

  private final CustomerService customers;
  private final StripePriceCatalog prices;
  private final StripeClient stripe;

  CheckoutSessionService(
      CustomerService customers, StripePriceCatalog prices, StripeClient stripe) {
    this.customers = customers;
    this.prices = prices;
    this.stripe = stripe;
  }

  public CheckoutSessionResponse create(UUID subjectId, CheckoutSessionRequest request) {
    Customer customer = customers.getOrCreate(subjectId);
    StripeProperties.Price price = prices.findByProduct(request.product());

    try {
      Session session =
          stripe
              .v1()
              .checkout()
              .sessions()
              .create(
                  SessionCreateParams.builder()
                      .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                      .setCustomer(customer.getStripeCustomerId())
                      .setSuccessUrl(request.successUrl())
                      .setCancelUrl(request.cancelUrl())
                      .putMetadata("subject_id", subjectId.toString())
                      .putMetadata("product", price.product())
                      .addLineItem(
                          SessionCreateParams.LineItem.builder()
                              .setPrice(price.id())
                              .setQuantity(1L)
                              .build())
                      .build());
      return new CheckoutSessionResponse(session.getId(), session.getUrl());
    } catch (StripeException exception) {
      throw ApiException.of(
          502,
          "stripe_checkout_session_create_failed",
          "Stripe checkout session could not be created.");
    }
  }
}
