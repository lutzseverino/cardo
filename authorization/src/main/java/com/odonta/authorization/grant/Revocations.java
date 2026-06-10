package com.odonta.authorization.grant;

import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class Revocations {

  private final ApplicationEventPublisher events;

  public Revocations(ApplicationEventPublisher events) {
    this.events = events;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void stage(RevocationPlan plan) {
    Objects.requireNonNull(plan, "plan");
    if (!plan.isEmpty()) {
      events.publishEvent(plan);
    }
  }
}
