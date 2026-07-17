package io.github.lutzseverino.cardo.billing.integration.stripe;

import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.entitlements.ActiveEntitlement;
import com.stripe.model.entitlements.ActiveEntitlementSummary;
import com.stripe.param.entitlements.ActiveEntitlementListParams;
import io.github.lutzseverino.cardo.billing.config.StripeProperties;
import io.github.lutzseverino.cardo.billing.model.EntitlementSyncItem;
import io.github.lutzseverino.cardo.billing.provider.StripeWebhookEvent;
import io.github.lutzseverino.cardo.billing.provider.StripeWebhookProvider;
import io.github.lutzseverino.cardo.common.api.ApiException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class StripeWebhookAdapter implements StripeWebhookProvider {

  public static final String PROVIDER = "stripe";
  public static final String ACTIVE_ENTITLEMENTS_UPDATED =
      "entitlements.active_entitlement_summary.updated";

  private final StripeProperties properties;
  private final StripeClient stripe;

  @Override
  public String name() {
    return PROVIDER;
  }

  @Override
  public StripeWebhookEvent parse(String payload, String signature) {
    Event event = constructEvent(payload, signature);
    if (!ACTIVE_ENTITLEMENTS_UPDATED.equals(event.getType())) {
      return new StripeWebhookEvent(event.getId(), event.getType(), null);
    }
    ActiveEntitlementSummary summary = object(event, ActiveEntitlementSummary.class);
    if (!StringUtils.hasText(summary.getCustomer())) {
      throw invalidEvent();
    }
    return new StripeWebhookEvent(event.getId(), event.getType(), summary.getCustomer());
  }

  @Override
  public boolean updatesActiveEntitlements(StripeWebhookEvent event) {
    return ACTIVE_ENTITLEMENTS_UPDATED.equals(event.type());
  }

  @Override
  public List<EntitlementSyncItem> activeEntitlements(String providerCustomerId) {
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

  private Event constructEvent(String payload, String signature) {
    try {
      return stripe.constructEvent(payload, signature, properties.webhookSecret());
    } catch (SignatureVerificationException exception) {
      throw ApiException.badRequest(
          "billing_webhook_signature_invalid", "Webhook signature is invalid.");
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
