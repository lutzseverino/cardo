package com.odonta.authorization.grant;

import org.springframework.modulith.events.ApplicationModuleListener;

class RevocationPlanListener {

  private final RevocationProcessor processor;

  RevocationPlanListener(RevocationProcessor processor) {
    this.processor = processor;
  }

  @ApplicationModuleListener(id = "authorization.revocation-plan")
  void apply(RevocationPlan plan) {
    processor.apply(plan);
  }
}
