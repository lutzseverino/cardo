package io.github.lutzseverino.cardo.billing.mapper;

import io.github.lutzseverino.cardo.billing.api.model.EntitlementResponse;
import io.github.lutzseverino.cardo.billing.model.EntitlementResult;
import io.github.lutzseverino.cardo.openapi.mapping.UriResponseConversions;
import org.mapstruct.Mapper;

@Mapper(
    config = BillingMapperConfig.class,
    uses = {BillingTransportConversions.class, UriResponseConversions.class})
public interface EntitlementTransportMapper {

  EntitlementResponse toResponse(EntitlementResult result);
}
