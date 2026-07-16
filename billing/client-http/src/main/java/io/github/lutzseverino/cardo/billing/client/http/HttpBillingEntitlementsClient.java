package io.github.lutzseverino.cardo.billing.client.http;

import io.github.lutzseverino.cardo.billing.client.BillingEntitlement;
import io.github.lutzseverino.cardo.billing.client.BillingEntitlementStatus;
import io.github.lutzseverino.cardo.billing.client.BillingEntitlementsClient;
import io.github.lutzseverino.cardo.billing.client.http.generated.EntitlementResponse;
import io.github.lutzseverino.cardo.billing.client.http.generated.api.EntitlementsApi;
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
