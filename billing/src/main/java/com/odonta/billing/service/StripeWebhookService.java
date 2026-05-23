package com.odonta.billing.service;

import com.odonta.billing.config.StripeProperties;
import com.odonta.billing.model.StripeEvent;
import com.odonta.billing.repository.StripeEventRepository;
import com.odonta.common.api.ApiException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class StripeWebhookService {

  private final EntitlementService entitlements;
  private final StripeEventRepository events;
  private final StripePriceCatalog prices;
  private final StripeProperties properties;

  StripeWebhookService(
      EntitlementService entitlements,
      StripeEventRepository events,
      StripePriceCatalog prices,
      StripeProperties properties) {
    this.entitlements = entitlements;
    this.events = events;
    this.prices = prices;
    this.properties = properties;
  }

  @Transactional
  public void handle(String payload, String signature) {
    Event event = constructEvent(payload, signature);
    if (events.existsById(event.getId())) {
      return;
    }
    handleEvent(event);
    events.save(StripeEvent.processed(event.getId(), event.getType()));
  }

  private Event constructEvent(String payload, String signature) {
    try {
      return Webhook.constructEvent(payload, signature, properties.webhookSecret());
    } catch (SignatureVerificationException exception) {
      throw ApiException.badRequest(
          "stripe_webhook_signature_invalid", "Stripe webhook signature is invalid.");
    }
  }

  private void handleEvent(Event event) {
    if ("checkout.session.completed".equals(event.getType())) {
      handleCheckoutSessionCompleted(event);
    }
  }

  private void handleCheckoutSessionCompleted(Event event) {
    StripeObject object =
        event
            .getDataObjectDeserializer()
            .getObject()
            .orElseThrow(
                () ->
                    ApiException.badRequest(
                        "stripe_webhook_event_invalid", "Stripe webhook event is invalid."));
    if (!(object instanceof Session session)) {
      throw ApiException.badRequest(
          "stripe_webhook_event_invalid", "Stripe webhook event is invalid.");
    }
    String subjectId = session.getMetadata().get("subject_id");
    String product = session.getMetadata().get("product");
    if (!StringUtils.hasText(subjectId) || !StringUtils.hasText(product)) {
      throw ApiException.badRequest(
          "stripe_webhook_event_invalid", "Stripe webhook event is invalid.");
    }
    var price = prices.findByProduct(product);

    entitlements.activate(
        UUID.fromString(subjectId), price.product(), price.tenantLimit(), price.seatLimit(), null);
  }
}
