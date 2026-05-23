package com.odonta.authorization.sync;

import com.odonta.authorization.AuthorizationSyncStatus;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

public class AuthorizationSyncService {

  private final ApplicationEventPublisher events;
  private final Map<Class<? extends AuthorizationEvent>, AuthorizationPlanHandler<?>> handlers;
  private final AuthorizationSyncItemRepository items;

  public AuthorizationSyncService(
      ApplicationEventPublisher events,
      AuthorizationSyncItemRepository items,
      List<AuthorizationPlanHandler<?>> handlers) {
    this.events = events;
    this.items = items;
    this.handlers =
        handlers.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    AuthorizationPlanHandler::eventType,
                    Function.identity(),
                    AuthorizationSyncService::duplicateHandler));
  }

  @Transactional
  public void enqueue(AuthorizationEvent event) {
    enqueue(planFor(event));
  }

  private void enqueue(AuthorizationPlan plan) {
    if (plan.isEmpty()) {
      return;
    }
    plan.operations().forEach(this::enqueue);
    events.publishEvent(new AuthorizationSyncRequested());
  }

  private void enqueue(AuthorizationSyncOperation operation) {
    items
        .findByUniqueKey(operation.uniqueKey())
        .ifPresentOrElse(
            item -> {
              if (AuthorizationSyncStatus.FAILED.equals(item.getStatus())) {
                item.markPending();
              }
            },
            () -> items.save(AuthorizationSyncItem.from(operation)));
  }

  private <E extends AuthorizationEvent> AuthorizationPlan planFor(E event) {
    return handlerFor(event).plan(event);
  }

  @SuppressWarnings("unchecked")
  private <E extends AuthorizationEvent> AuthorizationPlanHandler<E> handlerFor(E event) {
    AuthorizationPlanHandler<?> handler = handlers.get(event.getClass());
    if (handler == null) {
      throw new IllegalArgumentException(
          "No authorization plan handler registered for event: " + event.getClass().getName());
    }
    return (AuthorizationPlanHandler<E>) handler;
  }

  private static AuthorizationPlanHandler<?> duplicateHandler(
      AuthorizationPlanHandler<?> first, AuthorizationPlanHandler<?> second) {
    throw new IllegalStateException(
        "Multiple authorization plan handlers registered for event: "
            + first.eventType().getName());
  }
}
