package com.odonta.identity.mapper;

import com.odonta.identity.api.model.AuthenticatedPrincipalResponse;
import com.odonta.identity.model.AuthenticatedPrincipal;
import java.net.URI;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AuthenticatedPrincipalMapper {

  default AuthenticatedPrincipalResponse toResponse(AuthenticatedPrincipal principal) {
    return new AuthenticatedPrincipalResponse(
        principal.sessionId(),
        user(principal),
        com.odonta.identity.api.model.AuthenticationMethod.fromValue(
            principal.authenticationMethod().wireValue()),
        principal.authProviderId(),
        principal.expiresAt());
  }

  default com.odonta.identity.api.model.UserResponse user(AuthenticatedPrincipal principal) {
    return new com.odonta.identity.api.model.UserResponse(
            principal.userId(),
            principal.keycloakSubject(),
            principal.userEmail(),
            principal.userName(),
            com.odonta.identity.api.model.UserStatus.fromValue(principal.userStatus().wireValue()),
            principal.userEmailVerified(),
            principal.userCreatedAt(),
            principal.userUpdatedAt())
        .avatarUrl(toUri(principal.userAvatarUrl()));
  }

  default URI toUri(String value) {
    return value == null ? null : URI.create(value);
  }
}
