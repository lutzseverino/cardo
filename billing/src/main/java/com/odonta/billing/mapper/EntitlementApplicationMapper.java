package com.odonta.billing.mapper;

import com.odonta.billing.model.EntitlementResult;
import com.odonta.billing.repository.EntitlementProjection;
import org.mapstruct.Mapper;

@Mapper(config = BillingMapperConfig.class)
public interface EntitlementApplicationMapper {

  EntitlementResult toResult(EntitlementProjection projection);
}
