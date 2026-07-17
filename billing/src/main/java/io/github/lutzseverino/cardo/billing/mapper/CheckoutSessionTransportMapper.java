package io.github.lutzseverino.cardo.billing.mapper;

import io.github.lutzseverino.cardo.billing.api.model.CheckoutSessionResponse;
import io.github.lutzseverino.cardo.billing.api.model.CreateCheckoutSessionRequest;
import io.github.lutzseverino.cardo.billing.model.BillingSessionResult;
import io.github.lutzseverino.cardo.billing.model.CreateCheckoutSessionInput;
import io.github.lutzseverino.cardo.openapi.mapping.UriResponseConversions;
import org.mapstruct.Mapper;

@Mapper(
    config = BillingMapperConfig.class,
    uses = {BillingTransportConversions.class, UriResponseConversions.class})
public interface CheckoutSessionTransportMapper {

  CreateCheckoutSessionInput toInput(CreateCheckoutSessionRequest request);

  CheckoutSessionResponse toResponse(BillingSessionResult result);
}
