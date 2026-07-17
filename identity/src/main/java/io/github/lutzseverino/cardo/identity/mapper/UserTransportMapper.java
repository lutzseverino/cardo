package io.github.lutzseverino.cardo.identity.mapper;

import io.github.lutzseverino.cardo.identity.api.model.CreateProvisionalUserRequest;
import io.github.lutzseverino.cardo.identity.api.model.CreateUserRequest;
import io.github.lutzseverino.cardo.identity.api.model.UserResponse;
import io.github.lutzseverino.cardo.identity.model.AuthenticatedPrincipal;
import io.github.lutzseverino.cardo.identity.model.CreateProvisionalUserInput;
import io.github.lutzseverino.cardo.identity.model.CreateUserInput;
import io.github.lutzseverino.cardo.identity.model.UserResult;
import io.github.lutzseverino.cardo.openapi.mapping.OpenApiNullableConversions;
import io.github.lutzseverino.cardo.openapi.mapping.UriResponseConversions;
import java.util.List;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
    config = IdentityMapperConfig.class,
    uses = {
      IdentityTransportConversions.class,
      OpenApiNullableConversions.class,
      UriResponseConversions.class
    })
public interface UserTransportMapper {

  CreateUserInput toInput(CreateUserRequest request);

  CreateProvisionalUserInput toInput(CreateProvisionalUserRequest request);

  @Mapping(target = "avatarUrl", qualifiedByName = "toNullableUri")
  UserResponse toResponse(UserResult result);

  @Mapping(target = "id", source = "userId")
  @Mapping(target = "authorizationSubject", source = "keycloakSubject")
  @Mapping(target = "email", source = "userEmail")
  @Mapping(target = "name", source = "userName")
  @Mapping(target = "avatarUrl", source = "userAvatarUrl", qualifiedByName = "toNullableUri")
  @Mapping(target = "status", source = "userStatus")
  @Mapping(target = "emailVerified", source = "userEmailVerified")
  @Mapping(target = "createdAt", source = "userCreatedAt")
  @Mapping(target = "updatedAt", source = "userUpdatedAt")
  @BeanMapping(
      ignoreUnmappedSourceProperties = {
        "sessionId",
        "authenticationMethod",
        "authProviderId",
        "expiresAt"
      })
  UserResponse toResponse(AuthenticatedPrincipal principal);

  List<UserResponse> toResponses(List<UserResult> results);
}
