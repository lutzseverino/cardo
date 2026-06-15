package com.odonta.identity.mapper;

import com.odonta.identity.api.model.CompleteProvisionalUserRequest;
import com.odonta.identity.api.model.CreateProvisionalUserRequest;
import com.odonta.identity.api.model.CreateUserRequest;
import com.odonta.identity.api.model.UserResponse;
import com.odonta.identity.model.CompleteProvisionalUserInput;
import com.odonta.identity.model.CreateProvisionalUserInput;
import com.odonta.identity.model.CreateUserInput;
import com.odonta.identity.model.UserResult;
import com.odonta.openapi.mapping.OpenApiNullableConversions;
import com.odonta.openapi.mapping.UriResponseConversions;
import java.util.List;
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

  CompleteProvisionalUserInput toInput(CompleteProvisionalUserRequest request);

  @Mapping(target = "avatarUrl", qualifiedByName = "toNullableUri")
  UserResponse toResponse(UserResult result);

  List<UserResponse> toResponses(List<UserResult> results);
}
