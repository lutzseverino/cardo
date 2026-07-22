package io.github.lutzseverino.cardo.authorization.grant;

import org.springframework.modulith.events.ApplicationModuleListener;

class RevocationPlanListener {

  private final RevocationProcessor processor;
  private final AuthorizationWorkflowMetrics metrics;

  RevocationPlanListener(RevocationProcessor processor, AuthorizationWorkflowMetrics metrics) {
    this.processor = processor;
    this.metrics = metrics;
  }

  @ApplicationModuleListener(id = "authorization.revocation-plan")
  void apply(RevocationPlan plan) {
    try {
      processor.apply(plan);
      metrics.revocation("success");
    } catch (RuntimeException failure) {
      metrics.revocation("retry");
      throw failure;
    }
  }
}
