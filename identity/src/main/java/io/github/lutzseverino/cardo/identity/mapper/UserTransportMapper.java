package io.github.lutzseverino.cardo.identity.mapper;

import io.github.lutzseverino.cardo.identity.api.model.CompleteProvisionalUserRequest;
import io.github.lutzseverino.cardo.identity.api.model.CreateProvisionalUserRequest;
import io.github.lutzseverino.cardo.identity.api.model.CreateUserRequest;
import io.github.lutzseverino.cardo.identity.api.model.UserResponse;
import io.github.lutzseverino.cardo.identity.model.CompleteProvisionalUserInput;
import io.github.lutzseverino.cardo.identity.model.CreateProvisionalUserInput;
import io.github.lutzseverino.cardo.identity.model.CreateUserInput;
import io.github.lutzseverino.cardo.identity.model.UserResult;
import io.github.lutzseverino.cardo.openapi.mapping.OpenApiNullableConversions;
import io.github.lutzseverino.cardo.openapi.mapping.UriResponseConversions;
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
