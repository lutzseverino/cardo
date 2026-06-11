package com.odonta.invite.integration.identity;

import com.odonta.identity.client.CompleteProvisionalUserRequest;
import com.odonta.identity.client.CreateProvisionalUserRequest;
import com.odonta.identity.client.UserResponse;
import com.odonta.identity.client.api.UsersApi;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IdentityUserClient {

  private final UsersApi users;

  public ProvisionalUser createProvisional(String email) {
    return toProvisionalUser(
        users.createProvisionalUser(new CreateProvisionalUserRequest().email(email)));
  }

  public ProvisionalUser completeProvisional(UUID userId, String name, String password) {
    return toProvisionalUser(
        users.completeProvisionalUser(
            userId, new CompleteProvisionalUserRequest().name(name).password(password)));
  }

  public void cancelProvisional(UUID userId) {
    users.cancelProvisionalUser(userId);
  }

  private ProvisionalUser toProvisionalUser(UserResponse user) {
    return new ProvisionalUser(user.getId(), user.getAuthorizationSubject());
  }

  public record ProvisionalUser(UUID id, String authorizationSubject) {}
}
