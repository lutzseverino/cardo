package com.odonta.billing.mapper;

import com.odonta.billing.api.model.EntitlementResponse;
import com.odonta.billing.model.EntitlementResult;
import com.odonta.openapi.mapping.UriResponseConversions;
import org.mapstruct.Mapper;

@Mapper(
    config = BillingMapperConfig.class,
    uses = {BillingTransportConversions.class, UriResponseConversions.class})
public interface EntitlementTransportMapper {

  EntitlementResponse toResponse(EntitlementResult result);
}
