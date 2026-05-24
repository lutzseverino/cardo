package com.odonta.billing.integration.stripe;

import com.odonta.billing.config.StripeProperties;
import com.odonta.billing.model.BillingSessionResult;
import com.odonta.billing.model.CheckoutSessionCommand;
import com.odonta.billing.model.Customer;
import com.odonta.billing.model.PortalSessionCommand;
import com.odonta.billing.provider.BillingProvider;
import com.odonta.billing.service.CustomerService;
import com.odonta.common.api.ApiException;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.billingportal.SessionCreateParams;
import com.stripe.param.checkout.SessionCreateParams.SubscriptionData;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class StripeBillingProvider implements BillingProvider {

  static final String PROVIDER = "stripe";

  private final CustomerService customers;
  private final StripeCheckoutCatalog prices;
  private final StripeClient stripe;

  StripeBillingProvider(
      CustomerService customers, StripeCheckoutCatalog prices, StripeClient stripe) {
    this.customers = customers;
    this.prices = prices;
    this.stripe = stripe;
  }

  @Override
  public BillingSessionResult createCheckoutSession(
      UUID subjectId, CheckoutSessionCommand command) {
    Customer customer = customers.getOrCreate(subjectId, PROVIDER, () -> createCustomer(subjectId));
    StripeProperties.CheckoutPrice price = prices.findByProduct(command.product());

    try {
      Session session =
          stripe
              .v1()
              .checkout()
              .sessions()
              .create(
                  com.stripe.param.checkout.SessionCreateParams.builder()
                      .setMode(com.stripe.param.checkout.SessionCreateParams.Mode.SUBSCRIPTION)
                      .setCustomer(customer.getProviderCustomerId())
                      .setSuccessUrl(command.successUrl())
                      .setCancelUrl(command.cancelUrl())
                      .putMetadata("subject_id", subjectId.toString())
                      .putMetadata("product", price.product())
                      .setSubscriptionData(
                          SubscriptionData.builder()
                              .putMetadata("subject_id", subjectId.toString())
                              .putMetadata("product", price.product())
                              .build())
                      .addLineItem(
                          com.stripe.param.checkout.SessionCreateParams.LineItem.builder()
                              .setPrice(price.id())
                              .setQuantity(1L)
                              .build())
                      .build());
      return new BillingSessionResult(session.getId(), session.getUrl());
    } catch (StripeException exception) {
      throw ApiException.of(
          502, "billing_checkout_session_create_failed", "Checkout session could not be created.");
    }
  }

  @Override
  public BillingSessionResult createPortalSession(UUID subjectId, PortalSessionCommand command) {
    Customer customer = customers.getOrCreate(subjectId, PROVIDER, () -> createCustomer(subjectId));
    try {
      com.stripe.model.billingportal.Session session =
          stripe
              .v1()
              .billingPortal()
              .sessions()
              .create(
                  SessionCreateParams.builder()
                      .setCustomer(customer.getProviderCustomerId())
                      .setReturnUrl(command.returnUrl())
                      .build());
      return new BillingSessionResult(session.getId(), session.getUrl());
    } catch (StripeException exception) {
      throw ApiException.of(
          502, "billing_portal_session_create_failed", "Portal session could not be created.");
    }
  }

  private String createCustomer(UUID subjectId) {
    try {
      com.stripe.model.Customer customer =
          stripe
              .v1()
              .customers()
              .create(
                  CustomerCreateParams.builder()
                      .putMetadata("subject_id", subjectId.toString())
                      .build());
      return customer.getId();
    } catch (StripeException exception) {
      throw ApiException.of(
          502, "billing_customer_create_failed", "Customer could not be created.");
    }
  }
}
