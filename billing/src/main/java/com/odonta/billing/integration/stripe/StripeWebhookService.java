package com.odonta.billing.integration.stripe;

import com.odonta.billing.config.StripeProperties;
import com.odonta.billing.model.Customer;
import com.odonta.billing.model.EntitlementStatus;
import com.odonta.billing.model.ProviderEvent;
import com.odonta.billing.repository.ProviderEventRepository;
import com.odonta.billing.service.CustomerService;
import com.odonta.billing.service.EntitlementService;
import com.odonta.common.api.ApiException;
import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.Price;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class StripeWebhookService {

  private final CustomerService customers;
  private final EntitlementService entitlements;
  private final ProviderEventRepository events;
  private final StripePriceCatalog prices;
  private final StripeProperties properties;
  private final StripeClient stripe;

  StripeWebhookService(
      CustomerService customers,
      EntitlementService entitlements,
      ProviderEventRepository events,
      StripePriceCatalog prices,
      StripeProperties properties,
      StripeClient stripe) {
    this.customers = customers;
    this.entitlements = entitlements;
    this.events = events;
    this.prices = prices;
    this.properties = properties;
    this.stripe = stripe;
  }

  @Transactional
  public void handle(String payload, String signature) {
    Event event = constructEvent(payload, signature);
    if (events.existsByProviderAndProviderEventId(StripeBillingProvider.PROVIDER, event.getId())) {
      return;
    }
    handleEvent(event);
    events.save(
        ProviderEvent.processed(StripeBillingProvider.PROVIDER, event.getId(), event.getType()));
  }

  private Event constructEvent(String payload, String signature) {
    try {
      return stripe.constructEvent(payload, signature, properties.webhookSecret());
    } catch (SignatureVerificationException exception) {
      throw ApiException.badRequest(
          "billing_webhook_signature_invalid", "Webhook signature is invalid.");
    }
  }

  private void handleEvent(Event event) {
    switch (event.getType()) {
      case "checkout.session.completed" -> handleCheckoutSessionCompleted(event);
      case "customer.subscription.created",
          "customer.subscription.updated",
          "customer.subscription.deleted" ->
          handleSubscriptionChanged(event);
      default -> {}
    }
  }

  private void handleCheckoutSessionCompleted(Event event) {
    Session session = object(event, Session.class);
    String subjectId = session.getMetadata().get("subject_id");
    String product = session.getMetadata().get("product");
    if (!StringUtils.hasText(subjectId) || !StringUtils.hasText(product)) {
      throw invalidEvent();
    }
    var price = prices.findByProduct(product);
    entitlements.sync(
        UUID.fromString(subjectId),
        price.product(),
        EntitlementStatus.ACTIVE,
        price.tenantLimit(),
        price.seatLimit(),
        null,
        null);
  }

  private void handleSubscriptionChanged(Event event) {
    Subscription subscription = object(event, Subscription.class);
    if (!StringUtils.hasText(subscription.getCustomer())) {
      throw invalidEvent();
    }
    Customer customer =
        customers.getByProviderCustomerId(
            StripeBillingProvider.PROVIDER, subscription.getCustomer());
    var price = prices.findById(priceId(subscription));
    entitlements.sync(
        customer.getSubjectId(),
        price.product(),
        status(subscription.getStatus()),
        price.tenantLimit(),
        price.seatLimit(),
        instant(subscription.getTrialEnd()),
        null);
  }

  private String priceId(Subscription subscription) {
    if (subscription.getItems() == null || subscription.getItems().getData().isEmpty()) {
      throw invalidEvent();
    }
    Price price = subscription.getItems().getData().getFirst().getPrice();
    if (price == null || !StringUtils.hasText(price.getId())) {
      throw invalidEvent();
    }
    return price.getId();
  }

  private EntitlementStatus status(String status) {
    return switch (status) {
      case "active" -> EntitlementStatus.ACTIVE;
      case "trialing" -> EntitlementStatus.TRIALING;
      case "past_due", "unpaid", "incomplete" -> EntitlementStatus.PAST_DUE;
      default -> EntitlementStatus.CANCELED;
    };
  }

  private OffsetDateTime instant(Long epochSecond) {
    if (epochSecond == null) {
      return null;
    }
    return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), ZoneOffset.UTC);
  }

  private <T extends StripeObject> T object(Event event, Class<T> type) {
    StripeObject object =
        event.getDataObjectDeserializer().getObject().orElseThrow(this::invalidEvent);
    if (!type.isInstance(object)) {
      throw invalidEvent();
    }
    return type.cast(object);
  }

  private ApiException invalidEvent() {
    return ApiException.badRequest("billing_webhook_event_invalid", "Webhook event is invalid.");
  }
}
