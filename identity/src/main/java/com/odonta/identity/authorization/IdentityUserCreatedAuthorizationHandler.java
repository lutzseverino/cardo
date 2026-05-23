package com.odonta.identity.authorization;

import static com.odonta.authorization.plan.AuthorizationPlanBuilder.authorizationPlan;

import com.odonta.authorization.sync.AuthorizationPlan;
import com.odonta.authorization.sync.AuthorizationPlanHandler;
import com.odonta.identity.IdentityPermissions;
import com.odonta.identity.model.User;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class IdentityUserCreatedAuthorizationHandler
    implements AuthorizationPlanHandler<IdentityUserCreated> {

  private static final List<String> DEFAULT_PROFILE_AUTHORITIES =
      List.of(IdentityPermissions.PROFILE_READ, IdentityPermissions.PROFILE_WRITE);

  @Override
  public Class<IdentityUserCreated> eventType() {
    return IdentityUserCreated.class;
  }

  @Override
  public AuthorizationPlan plan(IdentityUserCreated event) {
    User user = event.user();
    return authorizationPlan()
        .provision(user)
        .assignAuthorities(
            user.getKeycloakSubject(), IdentityPermissions.CLIENT_ID, DEFAULT_PROFILE_AUTHORITIES)
        .build();
  }
}
