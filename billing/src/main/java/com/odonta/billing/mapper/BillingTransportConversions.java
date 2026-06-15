package com.odonta.billing.mapper;

import com.odonta.billing.model.EntitlementStatus;
import org.springframework.stereotype.Component;

@Component
public class BillingTransportConversions {

  public com.odonta.billing.api.model.EntitlementResponse.StatusEnum toTransport(
      EntitlementStatus status) {
    return status == null
        ? null
        : com.odonta.billing.api.model.EntitlementResponse.StatusEnum.fromValue(status.wireValue());
  }
}
