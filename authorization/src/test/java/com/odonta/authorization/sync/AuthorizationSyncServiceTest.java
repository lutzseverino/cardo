package com.odonta.authorization.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class AuthorizationSyncServiceTest {

  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final AuthorizationSyncItemRepository items = mock(AuthorizationSyncItemRepository.class);

  @Test
  void enqueuesPlanFromRegisteredEventHandler() {
    AuthorizationSyncService service =
        new AuthorizationSyncService(events, items, List.of(new TestAuthorizationHandler()));
    when(items.findByUniqueKey("authorities:identity:subject-1")).thenReturn(Optional.empty());

    service.enqueue(new TestAuthorizationEvent("subject-1"));

    ArgumentCaptor<AuthorizationSyncItem> item =
        ArgumentCaptor.forClass(AuthorizationSyncItem.class);
    verify(items).save(item.capture());
    verify(events).publishEvent(any(AuthorizationSyncRequested.class));
    assertThat(item.getValue().getUniqueKey()).isEqualTo("authorities:identity:subject-1");
    assertThat(item.getValue().actionList()).containsExactly("profile:read");
  }

  @Test
  void rejectsEventsWithoutRegisteredHandler() {
    AuthorizationSyncService service = new AuthorizationSyncService(events, items, List.of());

    assertThatThrownBy(() -> service.enqueue(new TestAuthorizationEvent("subject-1")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(TestAuthorizationEvent.class.getName());

    verifyNoInteractions(items, events);
  }

  @Test
  void doesNotPublishSyncRequestForEmptyPlan() {
    AuthorizationSyncService service =
        new AuthorizationSyncService(events, items, List.of(new EmptyAuthorizationHandler()));

    service.enqueue(new EmptyAuthorizationEvent());

    verifyNoInteractions(items);
    verifyNoMoreInteractions(events);
  }

  private record TestAuthorizationEvent(String requesterSubject) implements AuthorizationEvent {}

  private static final class TestAuthorizationHandler
      implements AuthorizationPlanHandler<TestAuthorizationEvent> {

    @Override
    public Class<TestAuthorizationEvent> eventType() {
      return TestAuthorizationEvent.class;
    }

    @Override
    public AuthorizationPlan plan(TestAuthorizationEvent event) {
      return AuthorizationPlan.of(
          List.of(
              AuthorizationSyncOperation.assignAuthorities(
                  "authorities:identity:" + event.requesterSubject(),
                  "identity",
                  event.requesterSubject(),
                  List.of("profile:read"))));
    }
  }

  private record EmptyAuthorizationEvent() implements AuthorizationEvent {}

  private static final class EmptyAuthorizationHandler
      implements AuthorizationPlanHandler<EmptyAuthorizationEvent> {

    @Override
    public Class<EmptyAuthorizationEvent> eventType() {
      return EmptyAuthorizationEvent.class;
    }

    @Override
    public AuthorizationPlan plan(EmptyAuthorizationEvent event) {
      return AuthorizationPlan.of(List.of());
    }
  }
}
