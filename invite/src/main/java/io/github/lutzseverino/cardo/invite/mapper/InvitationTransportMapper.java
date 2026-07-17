package io.github.lutzseverino.cardo.invite.mapper;

import io.github.lutzseverino.cardo.invite.api.model.CreateInvitationRequest;
import io.github.lutzseverino.cardo.invite.api.model.CreateInvitationResponse;
import io.github.lutzseverino.cardo.invite.api.model.InvitationCompletionResponse;
import io.github.lutzseverino.cardo.invite.api.model.InvitationResponse;
import io.github.lutzseverino.cardo.invite.api.model.InvitationTokenResponse;
import io.github.lutzseverino.cardo.invite.model.CreateInvitationInput;
import io.github.lutzseverino.cardo.invite.model.CreateInvitationResult;
import io.github.lutzseverino.cardo.invite.model.InvitationCompletionResult;
import io.github.lutzseverino.cardo.invite.model.InvitationResult;
import io.github.lutzseverino.cardo.invite.model.InvitationTokenResult;
import io.github.lutzseverino.cardo.openapi.mapping.UriResponseConversions;
import org.mapstruct.Mapper;

@Mapper(
    config = InviteMapperConfig.class,
    uses = {InviteTransportConversions.class, UriResponseConversions.class})
public interface InvitationTransportMapper {

  CreateInvitationInput toInput(CreateInvitationRequest request);

  CreateInvitationResponse toResponse(CreateInvitationResult result);

  InvitationResponse toResponse(InvitationResult result);

  InvitationTokenResponse toResponse(InvitationTokenResult result);

  InvitationCompletionResponse toResponse(InvitationCompletionResult result);
}
