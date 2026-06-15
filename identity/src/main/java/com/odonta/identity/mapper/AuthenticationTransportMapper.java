package com.odonta.identity.mapper;

import com.odonta.authorization.grant.EffectiveGrant;
import com.odonta.authorization.grant.GrantedResource;
import com.odonta.identity.api.model.AuthenticateRequest;
import com.odonta.identity.api.model.AuthenticatedPrincipalResponse;
import com.odonta.identity.api.model.GrantedResourceResponse;
import com.odonta.identity.api.model.ResourceGrantResponse;
import com.odonta.identity.api.model.UserResponse;
import com.odonta.identity.model.AuthenticateInput;
import com.odonta.identity.model.AuthenticatedPrincipal;
import com.odonta.identity.model.AuthenticationResult;
import com.odonta.openapi.mapping.OpenApiNullableConversions;
import com.odonta.openapi.mapping.UriResponseConversions;
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
