package com.odonta.billing.mapper;

import com.odonta.billing.api.model.CheckoutSessionResponse;
import com.odonta.billing.api.model.CreateCheckoutSessionRequest;
import com.odonta.billing.api.model.CreatePortalSessionRequest;
import com.odonta.billing.api.model.PortalSessionResponse;
import com.odonta.billing.model.BillingSessionResult;
import com.odonta.billing.model.CreateCheckoutSessionInput;
import com.odonta.billing.model.CreatePortalSessionInput;
import org.mapstruct.Mapper;

@Mapper(config = BillingMapperConfig.class, uses = BillingTransportConversions.class)
public interface BillingSessionTransportMapper {

  CreateCheckoutSessionInput toInput(CreateCheckoutSessionRequest request);

  CreatePortalSessionInput toInput(CreatePortalSessionRequest request);

  CheckoutSessionResponse toCheckoutResponse(BillingSessionResult result);

  PortalSessionResponse toPortalResponse(BillingSessionResult result);
}
