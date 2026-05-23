package com.odonta.identity.mapper;

import com.odonta.identity.model.AuthenticatedPrincipal;
import com.odonta.identity.model.AuthenticatedPrincipalResponse;
import com.odonta.identity.model.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AuthenticatedPrincipalMapper {

  default AuthenticatedPrincipalResponse toResponse(AuthenticatedPrincipal principal) {
    return new AuthenticatedPrincipalResponse(
        principal.sessionId(),
        new UserResponse(
            principal.userId(),
            principal.keycloakSubject(),
            principal.userEmail(),
            principal.userName(),
            principal.userAvatarUrl(),
            principal.userStatus().wireValue(),
            principal.userEmailVerified(),
            principal.userCreatedAt(),
            principal.userUpdatedAt()),
        principal.authenticationMethod().wireValue(),
        principal.authProviderId(),
        principal.expiresAt());
  }
}
