package com.odonta.billing.mapper;

import com.odonta.billing.model.EntitlementProjection;
import com.odonta.billing.model.EntitlementResponse;
import com.odonta.billing.model.EntitlementStatus;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface EntitlementMapper {

  EntitlementResponse toResponse(EntitlementProjection entitlement);

  default String toResponse(EntitlementStatus status) {
    return status == null ? null : status.wireValue();
  }
}
