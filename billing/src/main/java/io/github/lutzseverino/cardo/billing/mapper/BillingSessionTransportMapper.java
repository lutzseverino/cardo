package io.github.lutzseverino.cardo.billing.mapper;

import io.github.lutzseverino.cardo.billing.api.model.CheckoutSessionResponse;
import io.github.lutzseverino.cardo.billing.api.model.CreateCheckoutSessionRequest;
import io.github.lutzseverino.cardo.billing.api.model.CreatePortalSessionRequest;
import io.github.lutzseverino.cardo.billing.api.model.PortalSessionResponse;
import io.github.lutzseverino.cardo.billing.model.BillingSessionResult;
import io.github.lutzseverino.cardo.billing.model.CreateCheckoutSessionInput;
import io.github.lutzseverino.cardo.billing.model.CreatePortalSessionInput;
import io.github.lutzseverino.cardo.openapi.mapping.UriResponseConversions;
import org.mapstruct.Mapper;

@Mapper(
    config = BillingMapperConfig.class,
    uses = {BillingTransportConversions.class, UriResponseConversions.class})
public interface BillingSessionTransportMapper {

  CreateCheckoutSessionInput toInput(CreateCheckoutSessionRequest request);

  CreatePortalSessionInput toInput(CreatePortalSessionRequest request);

  CheckoutSessionResponse toCheckoutResponse(BillingSessionResult result);

  PortalSessionResponse toPortalResponse(BillingSessionResult result);
}
