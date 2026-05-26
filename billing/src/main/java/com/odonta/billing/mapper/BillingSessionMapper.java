package com.odonta.billing.mapper;

import com.odonta.billing.api.model.CheckoutSessionResponse;
import com.odonta.billing.api.model.PortalSessionResponse;
import com.odonta.billing.model.BillingSessionResult;
import java.net.URI;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface BillingSessionMapper {

  CheckoutSessionResponse toCheckoutResponse(BillingSessionResult session);

  PortalSessionResponse toPortalResponse(BillingSessionResult session);

  default URI toUri(String value) {
    return value == null ? null : URI.create(value);
  }
}
