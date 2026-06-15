package com.odonta.invite.mapper;

import com.odonta.invite.api.model.CompleteInvitationRequest;
import com.odonta.invite.api.model.CreateInvitationRequest;
import com.odonta.invite.api.model.CreateInvitationResponse;
import com.odonta.invite.api.model.InvitationResponse;
import com.odonta.invite.model.CompleteInvitationInput;
import com.odonta.invite.model.CreateInvitationInput;
import com.odonta.invite.model.CreateInvitationResult;
import com.odonta.invite.model.InvitationResult;
import com.odonta.openapi.mapping.UriResponseConversions;
import org.mapstruct.Mapper;

@Mapper(
    config = InviteMapperConfig.class,
    uses = {InviteTransportConversions.class, UriResponseConversions.class})
public interface InvitationTransportMapper {

  CreateInvitationInput toInput(CreateInvitationRequest request);

  CompleteInvitationInput toInput(CompleteInvitationRequest request);

  CreateInvitationResponse toResponse(CreateInvitationResult result);

  InvitationResponse toResponse(InvitationResult result);
}
