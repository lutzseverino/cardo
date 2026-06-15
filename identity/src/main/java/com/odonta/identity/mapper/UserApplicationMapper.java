package com.odonta.identity.mapper;

import com.odonta.identity.model.UserResult;
import com.odonta.identity.repository.UserProjection;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = IdentityMapperConfig.class)
public interface UserApplicationMapper {

  @Mapping(target = "authorizationSubject", source = "keycloakSubject")
  UserResult toResult(UserProjection projection);
}
