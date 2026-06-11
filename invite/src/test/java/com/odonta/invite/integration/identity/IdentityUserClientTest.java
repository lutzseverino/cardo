package com.odonta.invite.integration.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.odonta.identity.client.CreateProvisionalUserRequest;
import com.odonta.identity.client.UserResponse;
import com.odonta.identity.client.api.UsersApi;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IdentityUserClientTest {

  @Test
  void mapsProvisionalUserAcrossTheGeneratedClientBoundary() {
    UsersApi users = mock(UsersApi.class);
    IdentityUserClient client = new IdentityUserClient(users);
    UUID userId = UUID.randomUUID();
    when(users.createProvisionalUser(any(CreateProvisionalUserRequest.class)))
        .thenReturn(new UserResponse().id(userId).authorizationSubject("subject-1"));

    assertThat(client.createProvisional("employee@example.com"))
        .isEqualTo(new IdentityUserClient.ProvisionalUser(userId, "subject-1"));

    ArgumentCaptor<CreateProvisionalUserRequest> request =
        ArgumentCaptor.forClass(CreateProvisionalUserRequest.class);
    verify(users).createProvisionalUser(request.capture());
    assertThat(request.getValue().getEmail()).isEqualTo("employee@example.com");
  }
}
