package io.github.lutzseverino.cardo.invite.mapper;

import io.github.lutzseverino.cardo.invite.api.model.InvitationGrantConvergenceResponse;
import io.github.lutzseverino.cardo.invite.model.InvitationGrantConvergenceResult;
import org.mapstruct.Mapper;

@Mapper(config = InviteMapperConfig.class)
public interface InvitationGrantConvergenceTransportMapper {

  InvitationGrantConvergenceResponse toResponse(InvitationGrantConvergenceResult result);
}
