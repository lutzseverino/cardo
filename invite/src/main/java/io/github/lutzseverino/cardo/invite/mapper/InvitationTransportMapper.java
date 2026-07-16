package io.github.lutzseverino.cardo.invite.mapper;

import io.github.lutzseverino.cardo.invite.api.model.CompleteInvitationRequest;
import io.github.lutzseverino.cardo.invite.api.model.CreateInvitationRequest;
import io.github.lutzseverino.cardo.invite.api.model.CreateInvitationResponse;
import io.github.lutzseverino.cardo.invite.api.model.InvitationResponse;
import io.github.lutzseverino.cardo.invite.model.CompleteInvitationInput;
import io.github.lutzseverino.cardo.invite.model.CreateInvitationInput;
import io.github.lutzseverino.cardo.invite.model.CreateInvitationResult;
import io.github.lutzseverino.cardo.invite.model.InvitationResult;
import io.github.lutzseverino.cardo.openapi.mapping.UriResponseConversions;
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
