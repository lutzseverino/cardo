package io.github.lutzseverino.cardo.invite.mapper;

import io.github.lutzseverino.cardo.invite.model.InvitationResult;
import io.github.lutzseverino.cardo.invite.repository.InvitationProjection;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;

@Mapper(config = InviteMapperConfig.class)
public interface InvitationApplicationMapper {

  @BeanMapping(
      ignoreUnmappedSourceProperties = {
        "product",
        "grants",
        "invitedAuthorizationSubject",
        "acceptUrlBase",
        "token",
        "grantReceiptId"
      })
  InvitationResult toResult(InvitationProjection projection);
}
