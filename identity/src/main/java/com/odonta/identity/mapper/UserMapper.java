package com.odonta.identity.mapper;

import com.odonta.identity.api.model.UserResponse;
import com.odonta.identity.model.UserProjection;
import java.net.URI;
import java.util.List;
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
            user.getStatus(),
            user.isEmailVerified(),
            user.getCreatedAt(),
            user.getUpdatedAt())
        .avatarUrl(toUri(user.getAvatarUrl()));
  }

  default List<UserResponse> toResponses(List<UserProjection> users) {
    return users.stream().map(this::toResponse).toList();
  }

  default URI toUri(String value) {
    return value == null ? null : URI.create(value);
  }
}
