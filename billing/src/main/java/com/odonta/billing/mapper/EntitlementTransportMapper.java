package com.odonta.billing.mapper;

import com.odonta.billing.api.model.EntitlementResponse;
import com.odonta.billing.model.EntitlementResult;
import org.mapstruct.Mapper;

@Mapper(config = BillingMapperConfig.class, uses = BillingTransportConversions.class)
public interface EntitlementTransportMapper {

  EntitlementResponse toResponse(EntitlementResult result);
}
