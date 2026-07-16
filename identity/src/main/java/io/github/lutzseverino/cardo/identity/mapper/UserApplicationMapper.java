package io.github.lutzseverino.cardo.identity.mapper;

import io.github.lutzseverino.cardo.identity.model.UserResult;
import io.github.lutzseverino.cardo.identity.repository.UserProjection;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = IdentityMapperConfig.class)
public interface UserApplicationMapper {

  @Mapping(target = "authorizationSubject", source = "keycloakSubject")
  UserResult toResult(UserProjection projection);
}
