package io.github.lutzseverino.cardo.identity.authorization;

import io.github.lutzseverino.cardo.authorization.grant.GrantPlan;
import io.github.lutzseverino.cardo.identity.IdentityPermissions;
import io.github.lutzseverino.cardo.identity.model.User;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class IdentityGrantPlanner {

  private static final List<String> DEFAULT_PROFILE_AUTHORITIES =
      List.of(IdentityPermissions.PROFILE_READ, IdentityPermissions.PROFILE_WRITE);

  public GrantPlan creation(User user) {
    return GrantPlan.builder()
        .provision(user)
        .grantAuthorities(
            user.getKeycloakSubject(), IdentityPermissions.CLIENT_ID, DEFAULT_PROFILE_AUTHORITIES)
        .build();
  }
}
