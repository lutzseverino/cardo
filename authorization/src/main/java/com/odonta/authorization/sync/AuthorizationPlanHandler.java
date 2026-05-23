package com.odonta.authorization.sync;

public interface AuthorizationPlanHandler<E extends AuthorizationEvent> {

  Class<E> eventType();

  AuthorizationPlan plan(E event);
}
