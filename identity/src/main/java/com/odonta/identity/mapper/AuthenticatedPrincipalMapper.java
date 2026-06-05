package com.odonta.identity.mapper;

import com.odonta.authorization.grant.EffectiveGrant;
import com.odonta.authorization.grant.GrantedResource;
import com.odonta.identity.api.model.AuthenticatedPrincipalResponse;
import com.odonta.identity.api.model.AuthenticationMethod;
import com.odonta.identity.api.model.GrantedResourceResponse;
import com.odonta.identity.api.model.ResourceGrantResponse;
import com.odonta.identity.api.model.UserResponse;
import com.odonta.identity.api.model.UserStatus;
import com.odonta.identity.model.AuthenticatedPrincipal;
import java.net.URI;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AuthenticatedPrincipalMapper {

  default AuthenticatedPrincipalResponse toResponse(
      AuthenticatedPrincipal principal, List<EffectiveGrant> grants) {
    return new AuthenticatedPrincipalResponse(
        principal.sessionId(),
        user(principal),
        grants.stream().map(this::grant).toList(),
        AuthenticationMethod.fromValue(principal.authenticationMethod().wireValue()),
        principal.authProviderId(),
        principal.expiresAt());
  }

  default UserResponse user(AuthenticatedPrincipal principal) {
    return new UserResponse(
            principal.userId(),
            principal.keycloakSubject(),
            principal.userEmail(),
            principal.userName(),
            UserStatus.fromValue(principal.userStatus().wireValue()),
            principal.userEmailVerified(),
            principal.userCreatedAt(),
            principal.userUpdatedAt())
        .avatarUrl(toUri(principal.userAvatarUrl()));
  }

  default ResourceGrantResponse grant(EffectiveGrant grant) {
    return new ResourceGrantResponse(resource(grant.resource()), grant.actions());
  }

  default GrantedResourceResponse resource(GrantedResource resource) {
    return new GrantedResourceResponse(resource.type(), resource.id());
  }

  default URI toUri(String value) {
    return value == null ? null : URI.create(value);
  }
}
