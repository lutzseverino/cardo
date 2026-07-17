package io.github.lutzseverino.cardo.billing.provider;

import io.github.lutzseverino.cardo.billing.model.EntitlementSyncItem;
import java.util.List;

public interface StripeWebhookProvider {

  String name();

  StripeWebhookEvent parse(String payload, String signature);

  boolean updatesActiveEntitlements(StripeWebhookEvent event);

  List<EntitlementSyncItem> activeEntitlements(String providerCustomerId);
}
