package io.github.lutzseverino.cardo.identity.mapper;

import io.github.lutzseverino.cardo.authorization.grant.EffectiveGrant;
import io.github.lutzseverino.cardo.authorization.grant.GrantedResource;
import io.github.lutzseverino.cardo.identity.api.model.AuthenticateRequest;
import io.github.lutzseverino.cardo.identity.api.model.AuthenticatedPrincipalResponse;
import io.github.lutzseverino.cardo.identity.api.model.GrantedResourceResponse;
import io.github.lutzseverino.cardo.identity.api.model.ResourceGrantResponse;
import io.github.lutzseverino.cardo.identity.api.model.UserResponse;
import io.github.lutzseverino.cardo.identity.model.AuthenticateInput;
import io.github.lutzseverino.cardo.identity.model.AuthenticatedPrincipal;
import io.github.lutzseverino.cardo.identity.model.AuthenticationResult;
import io.github.lutzseverino.cardo.openapi.mapping.OpenApiNullableConversions;
import io.github.lutzseverino.cardo.openapi.mapping.UriResponseConversions;
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
public interface AuthenticationTransportMapper {

  AuthenticateInput toInput(AuthenticateRequest request);

  @BeanMapping(ignoreUnmappedSourceProperties = "token")
  @Mapping(target = "sessionId", source = "principal.sessionId")
  @Mapping(target = "user", source = "principal")
  @Mapping(target = "authenticationMethod", source = "principal.authenticationMethod")
  @Mapping(target = "authProviderId", source = "principal.authProviderId")
  @Mapping(target = "expiresAt", source = "principal.expiresAt")
  AuthenticatedPrincipalResponse toResponse(AuthenticationResult result);

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

  ResourceGrantResponse toResponse(EffectiveGrant grant);

  GrantedResourceResponse toResponse(GrantedResource resource);
}
