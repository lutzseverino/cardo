package com.odonta.billing.integration.stripe;

import com.odonta.billing.config.StripeProperties;
import com.odonta.billing.model.BillingSessionResult;
import com.odonta.billing.model.Customer;
import com.odonta.billing.provider.BillingProvider;
import com.odonta.billing.service.CustomerService;
import com.odonta.common.api.ApiException;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionCreateParams.LineItem;
import com.stripe.param.checkout.SessionCreateParams.Mode;
import com.stripe.param.checkout.SessionCreateParams.SubscriptionData;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StripeBillingProvider implements BillingProvider {

  static final String PROVIDER = "stripe";

  private final CustomerService customers;
  private final StripeCheckoutCatalog prices;
  private final StripeClient stripe;

  @Override
  public BillingSessionResult createCheckoutSession(
      UUID subjectId, String product, URI successUrl, URI cancelUrl) {
    Customer customer = customers.getOrCreate(subjectId, PROVIDER, () -> createCustomer(subjectId));
    StripeProperties.CheckoutPrice price = prices.findByProduct(product);

    try {
      Session session =
          stripe
              .v1()
              .checkout()
              .sessions()
              .create(
                  SessionCreateParams.builder()
                      .setMode(Mode.SUBSCRIPTION)
                      .setCustomer(customer.getProviderCustomerId())
                      .setSuccessUrl(successUrl.toString())
                      .setCancelUrl(cancelUrl.toString())
                      .putMetadata("subject_id", subjectId.toString())
                      .putMetadata("product", price.product())
                      .setSubscriptionData(
                          SubscriptionData.builder()
                              .putMetadata("subject_id", subjectId.toString())
                              .putMetadata("product", price.product())
                              .build())
                      .addLineItem(LineItem.builder().setPrice(price.id()).setQuantity(1L).build())
                      .build());
      return new BillingSessionResult(session.getId(), session.getUrl());
    } catch (StripeException exception) {
      throw ApiException.of(
          502, "billing_checkout_session_create_failed", "Checkout session could not be created.");
    }
  }

  @Override
  public BillingSessionResult createPortalSession(UUID subjectId, URI returnUrl) {
    Customer customer = customers.getOrCreate(subjectId, PROVIDER, () -> createCustomer(subjectId));
    try {
      com.stripe.model.billingportal.Session session =
          stripe
              .v1()
              .billingPortal()
              .sessions()
              .create(
                  com.stripe.param.billingportal.SessionCreateParams.builder()
                      .setCustomer(customer.getProviderCustomerId())
                      .setReturnUrl(returnUrl.toString())
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
