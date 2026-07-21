package io.github.lutzseverino.cardo.billing.integration.stripe;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerSearchParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionCreateParams.LineItem;
import com.stripe.param.checkout.SessionCreateParams.Mode;
import com.stripe.param.checkout.SessionCreateParams.SubscriptionData;
import io.github.lutzseverino.cardo.billing.config.StripeProperties;
import io.github.lutzseverino.cardo.billing.model.BillingSessionResult;
import io.github.lutzseverino.cardo.billing.provider.BillingProvider;
import io.github.lutzseverino.cardo.common.api.ApiException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StripeBillingProvider implements BillingProvider {

  static final String PROVIDER = "stripe";
  static final String PROVISIONING_METADATA_KEY = "cardo_provisioning_id";
  private static final String CUSTOMER_CREATION_KEY_PREFIX = "cardo-billing-customer-v2:";

  private final StripeCheckoutCatalog prices;
  private final StripeClient stripe;

  @Override
  public String name() {
    return PROVIDER;
  }

  @Override
  public String createCustomer(UUID subjectId, UUID provisioningId) {
    try {
      com.stripe.model.Customer customer =
          stripe
              .v1()
              .customers()
              .create(
                  CustomerCreateParams.builder()
                      .putMetadata("subject_id", subjectId.toString())
                      .putMetadata(PROVISIONING_METADATA_KEY, provisioningId.toString())
                      .build(),
                  RequestOptions.builder()
                      .setIdempotencyKey(customerCreationIdempotencyKey(provisioningId))
                      .build());
      return customer.getId();
    } catch (StripeException exception) {
      throw ApiException.of(
          502, "billing_customer_create_failed", "Customer could not be created.");
    }
  }

  @Override
  public List<String> findCustomersByProvisioningId(UUID provisioningId) {
    try {
      return stripe
          .v1()
          .customers()
          .search(
              CustomerSearchParams.builder()
                  .setQuery(
                      "metadata['" + PROVISIONING_METADATA_KEY + "']:'" + provisioningId + "'")
                  .setLimit(2L)
                  .build())
          .getData()
          .stream()
          .map(com.stripe.model.Customer::getId)
          .toList();
    } catch (StripeException exception) {
      throw ApiException.of(
          502, "billing_customer_create_failed", "Customer could not be created.");
    }
  }

  private String customerCreationIdempotencyKey(UUID provisioningId) {
    return CUSTOMER_CREATION_KEY_PREFIX + provisioningId;
  }

  @Override
  public void deleteCustomer(String providerCustomerId) {
    try {
      stripe.v1().customers().delete(providerCustomerId);
    } catch (StripeException exception) {
      throw ApiException.of(
          502, "billing_customer_delete_failed", "Customer could not be deleted.");
    }
  }

  @Override
  public BillingSessionResult createCheckoutSession(
      UUID subjectId, String providerCustomerId, String product, URI successUrl, URI cancelUrl) {
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
                      .setCustomer(providerCustomerId)
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
  public BillingSessionResult createPortalSession(String providerCustomerId, URI returnUrl) {
    try {
      com.stripe.model.billingportal.Session session =
          stripe
              .v1()
              .billingPortal()
              .sessions()
              .create(
                  com.stripe.param.billingportal.SessionCreateParams.builder()
                      .setCustomer(providerCustomerId)
                      .setReturnUrl(returnUrl.toString())
                      .build());
      return new BillingSessionResult(session.getId(), session.getUrl());
    } catch (StripeException exception) {
      throw ApiException.of(
          502, "billing_portal_session_create_failed", "Portal session could not be created.");
    }
  }
}
