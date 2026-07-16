package io.github.lutzseverino.cardo.billing.mapper;

import io.github.lutzseverino.cardo.billing.model.EntitlementResult;
import io.github.lutzseverino.cardo.billing.repository.EntitlementProjection;
import org.mapstruct.Mapper;

@Mapper(config = BillingMapperConfig.class)
public interface EntitlementApplicationMapper {

  EntitlementResult toResult(EntitlementProjection projection);
}
