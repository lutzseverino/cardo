package com.odonta.identity.authorization;

import com.odonta.authorization.grant.GrantPlan;
import com.odonta.identity.IdentityPermissions;
import com.odonta.identity.model.User;
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
