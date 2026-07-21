package io.github.lutzseverino.cardo.authorization.grant;

import java.util.Objects;
import java.util.UUID;

record StagedGrantPlan(UUID receiptId, GrantPlan plan) {

  StagedGrantPlan {
    Objects.requireNonNull(receiptId, "receiptId");
    Objects.requireNonNull(plan, "plan");
    if (plan.isEmpty()) {
      throw new IllegalArgumentException("an empty grant plan must not be published");
    }
  }
}
