package com.odonta.identity.mapper;

import com.odonta.identity.api.model.UserResponse;
import com.odonta.identity.model.UserProjection;
import com.odonta.identity.model.UserStatus;
import java.net.URI;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

  default UserResponse toResponse(UserProjection user) {
    return new UserResponse(
            user.getId(),
            user.getKeycloakSubject(),
            user.getEmail(),
            user.getName(),
            toResponse(user.getStatus()),
            user.isEmailVerified(),
            user.getCreatedAt(),
            user.getUpdatedAt())
        .avatarUrl(toUri(user.getAvatarUrl()));
  }

  default com.odonta.identity.api.model.UserStatus toResponse(UserStatus status) {
    return status == null
        ? null
        : com.odonta.identity.api.model.UserStatus.fromValue(status.wireValue());
  }

  default URI toUri(String value) {
    return value == null ? null : URI.create(value);
  }
}
