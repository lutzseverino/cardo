package io.github.lutzseverino.cardo.identity.workflow;

import io.github.lutzseverino.cardo.identity.provider.IdentityProvider;
import io.github.lutzseverino.cardo.identity.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InitializeIdentityRuntimeWorkflow {

  private final IdentityProvider identityProvider;
  private final UserRepository users;

  public void initialize(List<String> userIdClaimClientIds) {
    identityProvider.ensureUserIdClaimMapped(userIdClaimClientIds);
    users
        .findAllProjectedBy()
        .forEach(user -> identityProvider.bindUserId(user.getKeycloakSubject(), user.getId()));
  }
}
