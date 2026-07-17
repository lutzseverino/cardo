package io.github.lutzseverino.cardo.billing.mapper;

import io.github.lutzseverino.cardo.billing.api.model.CreatePortalSessionRequest;
import io.github.lutzseverino.cardo.billing.api.model.PortalSessionResponse;
import io.github.lutzseverino.cardo.billing.model.BillingSessionResult;
import io.github.lutzseverino.cardo.billing.model.CreatePortalSessionInput;
import io.github.lutzseverino.cardo.openapi.mapping.UriResponseConversions;
import org.mapstruct.Mapper;

@Mapper(
    config = BillingMapperConfig.class,
    uses = {BillingTransportConversions.class, UriResponseConversions.class})
public interface PortalSessionTransportMapper {

  CreatePortalSessionInput toInput(CreatePortalSessionRequest request);

  PortalSessionResponse toResponse(BillingSessionResult result);
}
