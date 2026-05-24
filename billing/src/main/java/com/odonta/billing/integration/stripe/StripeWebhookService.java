package com.odonta.billing.integration.stripe;

import com.odonta.billing.config.StripeProperties;
import com.odonta.billing.model.Customer;
import com.odonta.billing.model.EntitlementSyncItem;
import com.odonta.billing.model.ProviderEvent;
import com.odonta.billing.repository.ProviderEventRepository;
import com.odonta.billing.service.CustomerService;
import com.odonta.billing.service.EntitlementService;
import com.odonta.common.api.ApiException;
import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.entitlements.ActiveEntitlement;
import com.stripe.model.entitlements.ActiveEntitlementSummary;
import com.stripe.param.entitlements.ActiveEntitlementListParams;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class StripeWebhookService {

  private final CustomerService customers;
  private final EntitlementService entitlements;
  private final ProviderEventRepository events;
  private final StripeProperties properties;
  private final StripeClient stripe;

  StripeWebhookService(
      CustomerService customers,
      EntitlementService entitlements,
      ProviderEventRepository events,
      StripeProperties properties,
      StripeClient stripe) {
    this.customers = customers;
    this.entitlements = entitlements;
    this.events = events;
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
      case "entitlements.active_entitlement_summary.updated" ->
          handleActiveEntitlementSummaryUpdated(event);
      default -> {}
    }
  }

  private void handleActiveEntitlementSummaryUpdated(Event event) {
    ActiveEntitlementSummary summary = object(event, ActiveEntitlementSummary.class);
    if (!StringUtils.hasText(summary.getCustomer())) {
      throw invalidEvent();
    }
    syncActiveEntitlements(summary.getCustomer());
  }

  private void syncActiveEntitlements(String providerCustomerId) {
    Customer customer =
        customers.getByProviderCustomerId(StripeBillingProvider.PROVIDER, providerCustomerId);
    entitlements.replaceActive(customer.getSubjectId(), activeEntitlements(providerCustomerId));
  }

  private List<EntitlementSyncItem> activeEntitlements(String providerCustomerId) {
    try {
      Iterable<ActiveEntitlement> entitlements =
          stripe
              .v1()
              .entitlements()
              .activeEntitlements()
              .list(
                  ActiveEntitlementListParams.builder()
                      .setCustomer(providerCustomerId)
                      .addExpand("data.feature")
                      .build())
              .autoPagingIterable();
      List<EntitlementSyncItem> items = new ArrayList<>();
      for (ActiveEntitlement entitlement : entitlements) {
        if (StringUtils.hasText(entitlement.getLookupKey())) {
          items.add(item(entitlement));
        }
      }
      return items;
    } catch (StripeException exception) {
      throw ApiException.of(
          502, "billing_entitlements_sync_failed", "Entitlements could not be synchronized.");
    }
  }

  private EntitlementSyncItem item(ActiveEntitlement entitlement) {
    var metadata =
        entitlement.getFeatureObject() == null
            ? null
            : entitlement.getFeatureObject().getMetadata();
    return new EntitlementSyncItem(
        entitlement.getLookupKey(),
        integer(metadata == null ? null : metadata.get("tenant_limit")),
        integer(metadata == null ? null : metadata.get("seat_limit")));
  }

  private Integer integer(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      return Integer.valueOf(value);
    } catch (NumberFormatException exception) {
      throw invalidEvent();
    }
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
