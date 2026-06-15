package com.odonta.billing.mapper;

import com.odonta.billing.model.EntitlementStatus;
import java.net.URI;
import org.springframework.stereotype.Component;

@Component
public class BillingTransportConversions {

  public URI toUri(String value) {
    return value == null ? null : URI.create(value);
  }

  public com.odonta.billing.api.model.EntitlementResponse.StatusEnum toTransport(
      EntitlementStatus status) {
    return status == null
        ? null
        : com.odonta.billing.api.model.EntitlementResponse.StatusEnum.fromValue(status.wireValue());
  }
}
