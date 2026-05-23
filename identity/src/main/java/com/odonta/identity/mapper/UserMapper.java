package com.odonta.identity.mapper;

import com.odonta.identity.model.UserProjection;
import com.odonta.identity.model.UserResponse;
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
        user.getAvatarUrl(),
        user.getStatus().wireValue(),
        user.isEmailVerified(),
        user.getCreatedAt(),
        user.getUpdatedAt());
  }
}
