package com.odonta.invite.mapper;

import com.odonta.invite.api.model.CreateInvitationResponse;
import com.odonta.invite.api.model.InvitationResponse;
import com.odonta.invite.api.model.InvitationStatus;
import com.odonta.invite.model.CreateInvitationResult;
import com.odonta.invite.model.InvitationProjection;
import java.net.URI;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface InvitationMapper {

  default CreateInvitationResponse toResponse(CreateInvitationResult result) {
    return new CreateInvitationResponse(
        toResponse(result.invitation()), URI.create(result.acceptUrl()));
  }

  default InvitationResponse toResponse(InvitationProjection invitation) {
    return new InvitationResponse(
            invitation.getId(),
            invitation.getTenantId(),
            invitation.getTenantResourceType(),
            invitation.getAccessProfileId(),
            invitation.getInvitedEmail(),
            invitation.getInvitedUserId(),
            toResponse(invitation.getStatus()),
            invitation.getCreatedAt(),
            invitation.getUpdatedAt())
        .acceptedAt(invitation.getAcceptedAt());
  }

  default InvitationStatus toResponse(com.odonta.invite.model.InvitationStatus status) {
    return status == null ? null : InvitationStatus.fromValue(status.wireValue());
  }
}
