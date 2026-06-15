package com.odonta.invite.mapper;

import com.odonta.invite.model.InvitationResult;
import com.odonta.invite.repository.InvitationProjection;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;

@Mapper(config = InviteMapperConfig.class)
public interface InvitationApplicationMapper {

  @BeanMapping(
      ignoreUnmappedSourceProperties = {"invitedAuthorizationSubject", "invitedBy", "token"})
  InvitationResult toResult(InvitationProjection projection);
}
