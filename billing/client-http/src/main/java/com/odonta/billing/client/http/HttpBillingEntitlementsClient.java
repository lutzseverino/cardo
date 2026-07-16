package com.odonta.billing.client.http;

import com.odonta.billing.client.BillingEntitlement;
import com.odonta.billing.client.BillingEntitlementStatus;
import com.odonta.billing.client.BillingEntitlementsClient;
import com.odonta.billing.client.http.generated.EntitlementResponse;
import com.odonta.billing.client.http.generated.api.EntitlementsApi;
import java.util.UUID;

final class HttpBillingEntitlementsClient implements BillingEntitlementsClient {

  private final EntitlementsApi entitlements;

  HttpBillingEntitlementsClient(EntitlementsApi entitlements) {
    this.entitlements = entitlements;
  }

  @Override
  public BillingEntitlement require(UUID subjectId, String product) {
    return toEntitlement(entitlements.requireSubjectEntitlement(subjectId, product));
  }

  private BillingEntitlement toEntitlement(EntitlementResponse response) {
    return new BillingEntitlement(
        response.getId(),
        response.getSubjectId(),
        response.getProduct(),
        toStatus(response.getStatus()),
        response.getTenantLimit(),
        response.getSeatLimit(),
        response.getTrialEndsAt(),
        response.getCurrentPeriodEndsAt(),
        response.getCreatedAt(),
        response.getUpdatedAt());
  }

  private BillingEntitlementStatus toStatus(EntitlementResponse.StatusEnum status) {
    return switch (status) {
      case ACTIVE -> BillingEntitlementStatus.ACTIVE;
      case TRIALING -> BillingEntitlementStatus.TRIALING;
      case PAST_DUE -> BillingEntitlementStatus.PAST_DUE;
      case CANCELED -> BillingEntitlementStatus.CANCELED;
    };
  }
}
