package io.github.lutzseverino.cardo.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.config.BrowserSessionProperties;
import io.github.lutzseverino.cardo.identity.model.BrowserSession;
import io.github.lutzseverino.cardo.identity.repository.BrowserSessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BrowserSessionServiceTest {

  private static final Instant START = Instant.parse("2030-01-01T00:00:00Z");
  private final MutableClock clock = new MutableClock(START);
  private final BrowserSessionRepository repository = mock(BrowserSessionRepository.class);
  private final AtomicReference<BrowserSession> stored = new AtomicReference<>();
  private final BrowserSessionService service =
      new BrowserSessionService(
          repository,
          new BrowserSessionProperties(
              Duration.ofMinutes(30),
              Duration.ofHours(8),
              Duration.ofSeconds(30),
              Duration.ofDays(7),
              Duration.ofHours(1)),
          clock);

  @BeforeEach
  void retainSavedSession() {
    when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any(BrowserSession.class)))
        .thenAnswer(
            invocation -> {
              BrowserSession session = invocation.getArgument(0);
              stored.set(session);
              return session;
            });
    when(repository.findById("session-1"))
        .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
    when(repository.findEntityByProviderSessionIdForUpdate("session-1"))
        .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
    when(repository.findEntityByRefreshCredentialHashForUpdate(anyString()))
        .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
  }

  @Test
  void currentReportsAuthoritativeDeadlinesWithoutRenewingIdleLifetime() {
    var created = start();
    clock.advance(Duration.ofMinutes(10));

    var current = service.current("session-1", "subject-1").orElseThrow();

    assertThat(created.sessionStartedAt()).isEqualTo(time(START));
    assertThat(current.serverTime()).isEqualTo(time(START.plus(Duration.ofMinutes(10))));
    assertThat(current.idleExpiresAt()).isEqualTo(created.idleExpiresAt());
    assertThat(current.absoluteExpiresAt()).isEqualTo(created.absoluteExpiresAt());
  }

  @Test
  void explicitRefreshRenewsOnlyTheIdleDeadlineAndRotatesTheCredential() {
    var created = start();
    clock.advance(Duration.ofMinutes(20));

    var refresh = service.claimRefresh("refresh-1");
    var completed =
        service.completeRefresh(
            refresh, "session-1", "subject-1", "refresh-2", time(START.plus(Duration.ofHours(8))));

    assertThat(completed.idleExpiresAt()).isEqualTo(time(START.plus(Duration.ofMinutes(50))));
    assertThat(completed.absoluteExpiresAt()).isEqualTo(created.absoluteExpiresAt());
    assertThat(completed.sessionStartedAt()).isEqualTo(created.sessionStartedAt());
  }

  @Test
  void rejectsConcurrentRefreshWhileTheLeaseIsOwned() {
    start();
    service.claimRefresh("refresh-1");

    assertThatThrownBy(() -> service.claimRefresh("refresh-1"))
        .isInstanceOfSatisfying(
            ApiException.class,
            failure -> assertThat(failure.code()).isEqualTo("session_refresh_in_progress"));
  }

  @Test
  void expiredRefreshLeaseFailsClosedInsteadOfBeingReclaimed() {
    start();
    service.claimRefresh("refresh-1");
    clock.advance(Duration.ofSeconds(31));

    assertThatThrownBy(() -> service.claimRefresh("refresh-1"))
        .isInstanceOfSatisfying(
            ApiException.class, failure -> assertThat(failure.code()).isEqualTo("session_revoked"));
    assertThat(stored.get().active()).isFalse();
  }

  @Test
  void staleFencingTokenCannotRevokeTheOwnedSession() {
    start();
    var owner = service.claimRefresh("refresh-1");
    var stale =
        new io.github.lutzseverino.cardo.identity.model.BrowserSessionRefresh(
            owner.providerSessionId(), owner.providerSubject(), java.util.UUID.randomUUID());

    assertThat(service.revokeRefresh(stale)).isFalse();
    assertThat(stored.get().active()).isTrue();
  }

  @Test
  void reportsStableIdleAndAbsoluteExpiryReasons() {
    start();
    clock.advance(Duration.ofMinutes(30));

    assertThatThrownBy(() -> service.current("session-1", "subject-1"))
        .isInstanceOfSatisfying(
            ApiException.class,
            failure -> assertThat(failure.code()).isEqualTo("session_idle_expired"));

    BrowserSession absoluteSession =
        BrowserSession.start(
            "session-1",
            "subject-1",
            "hash",
            time(START),
            time(START.plus(Duration.ofHours(8))),
            time(START.plus(Duration.ofHours(8))));
    stored.set(absoluteSession);
    clock.set(START.plus(Duration.ofHours(8)));

    assertThatThrownBy(() -> service.current("session-1", "subject-1"))
        .isInstanceOfSatisfying(
            ApiException.class,
            failure -> assertThat(failure.code()).isEqualTo("session_absolute_expired"));
  }

  @Test
  void rejectsUnknownAndRevokedRefreshCredentialsWithStableReasons() {
    when(repository.findEntityByRefreshCredentialHashForUpdate(anyString()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.claimRefresh("unknown"))
        .isInstanceOfSatisfying(
            ApiException.class,
            failure -> assertThat(failure.code()).isEqualTo("refresh_credential_invalid"));

    start();
    when(repository.findEntityByRefreshCredentialHashForUpdate(anyString()))
        .thenAnswer(invocation -> Optional.of(stored.get()));
    service.revoke("refresh-1");

    assertThatThrownBy(() -> service.claimRefresh("refresh-1"))
        .isInstanceOfSatisfying(
            ApiException.class, failure -> assertThat(failure.code()).isEqualTo("session_revoked"));
  }

  private io.github.lutzseverino.cardo.identity.model.BrowserSessionMetadata start() {
    return service.start(
        "session-1", "subject-1", "refresh-1", time(START.plus(Duration.ofHours(8))));
  }

  private OffsetDateTime time(Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  private static final class MutableClock extends Clock {

    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    void set(Instant instant) {
      this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
