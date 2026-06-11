package com.odonta.billing.client.http;

import com.odonta.billing.client.BillingEntitlementsClient;
import com.odonta.billing.client.http.generated.api.EntitlementsApi;
import java.util.UUID;

final class HttpBillingEntitlementsClient implements BillingEntitlementsClient {

  private final EntitlementsApi entitlements;

  HttpBillingEntitlementsClient(EntitlementsApi entitlements) {
    this.entitlements = entitlements;
  }

  @Override
  public Integer requireTenantLimit(UUID subjectId, String product) {
    return entitlements.requireSubjectEntitlement(subjectId, product).getTenantLimit();
  }
}
