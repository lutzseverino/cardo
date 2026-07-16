package io.github.lutzseverino.cardo.billing.mapper;

import io.github.lutzseverino.cardo.billing.model.EntitlementStatus;
import org.springframework.stereotype.Component;

@Component
public class BillingTransportConversions {

  public io.github.lutzseverino.cardo.billing.api.model.EntitlementResponse.StatusEnum toTransport(
      EntitlementStatus status) {
    return status == null
        ? null
        : io.github.lutzseverino.cardo.billing.api.model.EntitlementResponse.StatusEnum.fromValue(
            status.wireValue());
  }
}
