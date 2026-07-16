package io.github.lutzseverino.cardo.authorization.grant;

import org.springframework.modulith.events.ApplicationModuleListener;

class GrantPlanListener {

  private final GrantProcessor processor;

  GrantPlanListener(GrantProcessor processor) {
    this.processor = processor;
  }

  @ApplicationModuleListener(id = "authorization.grant-plan")
  void apply(GrantPlan plan) {
    processor.apply(plan);
  }
}
