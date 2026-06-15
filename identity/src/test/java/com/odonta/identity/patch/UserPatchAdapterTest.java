package com.odonta.identity.patch;

import static org.assertj.core.api.Assertions.assertThat;

import com.odonta.identity.api.model.UpdateCurrentUserRequest;
import com.odonta.identity.api.model.UpdateUserRequest;
import com.odonta.identity.model.UpdateCurrentUserInput;
import com.odonta.identity.model.UpdateUserInput;
import com.odonta.identity.model.UserStatus;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.lang.reflect.RecordComponent;
import java.net.URI;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;

class UserPatchAdapterTest {

  private final UserPatchAdapter adapter = new UserPatchAdapter();

  @Test
  void keepsPatchRequestAndApplicationInputFieldsAligned() throws IntrospectionException {
    assertMatchingFields(UpdateUserRequest.class, UpdateUserInput.class);
    assertMatchingFields(UpdateCurrentUserRequest.class, UpdateCurrentUserInput.class);
  }

  @Test
  void preservesAbsentExplicitNullAndValue() {
    UpdateUserRequest request = new UpdateUserRequest();
    request.setAvatarUrl(JsonNullable.of(null));
    request.setStatus(com.odonta.identity.api.model.UpdateUserStatus.DISABLED);

    UpdateUserInput input = adapter.toInput(request);

    assertThat(input.avatarUrl().present()).isTrue();
    assertThat(input.avatarUrl().value()).isNull();
    assertThat(input.status()).isEqualTo(UserStatus.DISABLED);

    request.setAvatarUrl(JsonNullable.undefined());
    assertThat(adapter.toInput(request).avatarUrl().present()).isFalse();

    request.setAvatarUrl(JsonNullable.of(URI.create("https://example.com/avatar.png")));
    assertThat(adapter.toInput(request).avatarUrl().value())
        .isEqualTo("https://example.com/avatar.png");
  }

  private void assertMatchingFields(Class<?> requestType, Class<?> inputType)
      throws IntrospectionException {
    Set<String> requestFields =
        Stream.of(Introspector.getBeanInfo(requestType).getPropertyDescriptors())
            .map(descriptor -> descriptor.getName())
            .filter(name -> !name.equals("class"))
            .collect(Collectors.toSet());
    Set<String> inputFields =
        Stream.of(inputType.getRecordComponents())
            .map(RecordComponent::getName)
            .collect(Collectors.toSet());

    assertThat(requestFields)
        .as("%s -> %s", requestType.getSimpleName(), inputType.getSimpleName())
        .isEqualTo(inputFields);
  }
}
