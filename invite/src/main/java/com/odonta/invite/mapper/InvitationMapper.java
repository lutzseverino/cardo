package com.odonta.invite.mapper;

import com.odonta.invite.model.InvitationProjection;
import com.odonta.invite.model.InvitationResponse;
import com.odonta.invite.model.InvitationStatus;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface InvitationMapper {

  InvitationResponse toResponse(InvitationProjection invitation);

  default String toResponse(InvitationStatus status) {
    return status == null ? null : status.wireValue();
  }
}
