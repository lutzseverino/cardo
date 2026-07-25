package io.github.lutzseverino.cardo.identity.service;

import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.config.BrowserSessionProperties;
import io.github.lutzseverino.cardo.identity.model.BrowserSession;
import io.github.lutzseverino.cardo.identity.model.BrowserSessionMetadata;
import io.github.lutzseverino.cardo.identity.model.BrowserSessionRefresh;
import io.github.lutzseverino.cardo.identity.repository.BrowserSessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BrowserSessionService {

  private final BrowserSessionRepository sessions;
  private final BrowserSessionProperties properties;
  private final Clock clock;

  @Transactional
  public BrowserSessionMetadata start(
      String providerSessionId,
      String providerSubject,
      String refreshCredential,
      OffsetDateTime providerRefreshExpiresAt) {
    requireText(providerSessionId, "provider session id");
    requireText(providerSubject, "provider subject");
    OffsetDateTime now = now();
    requireFuture(providerRefreshExpiresAt, now);
    OffsetDateTime absoluteExpiresAt = now.plus(properties.absoluteTimeout());
    OffsetDateTime idleExpiresAt =
        earliest(now.plus(properties.idleTimeout()), absoluteExpiresAt, providerRefreshExpiresAt);
    BrowserSession session =
        BrowserSession.start(
            providerSessionId,
            providerSubject,
            fingerprint(refreshCredential),
            now,
            idleExpiresAt,
            absoluteExpiresAt);
    try {
      sessions.saveAndFlush(session);
    } catch (DataIntegrityViolationException conflict) {
      throw ApiException.conflict(
          "browser_session_conflict", "The provider session is already established.");
    }
    return metadata(session, now);
  }

  @Transactional(readOnly = true)
  public Optional<BrowserSessionMetadata> current(
      String providerSessionId, String providerSubject) {
    if (providerSessionId == null || providerSessionId.isBlank()) {
      return Optional.empty();
    }
    return sessions
        .findById(providerSessionId)
        .map(
            session -> {
              requireSubject(session, providerSubject);
              OffsetDateTime now = now();
              assertUsable(session, now);
              return metadata(session, now);
            });
  }

  @Transactional(noRollbackFor = ApiException.class)
  public BrowserSessionRefresh claimRefresh(String refreshCredential) {
    String hash = fingerprint(refreshCredential);
    BrowserSession session =
        sessions
            .findEntityByRefreshCredentialHashForUpdate(hash)
            .orElseThrow(
                () ->
                    ApiException.of(
                        401, "refresh_credential_invalid", "The refresh credential is not valid."));
    OffsetDateTime now = now();
    assertUsable(session, now);
    if (session.refreshInProgress()) {
      if (session.refreshLeaseExpired(now)) {
        session.revoke(now);
        throw ApiException.of(
            401,
            "session_revoked",
            "The browser session was revoked after an uncertain refresh outcome.");
      }
      throw ApiException.conflict(
          "session_refresh_in_progress", "The browser session is already being refreshed.");
    }
    var leaseToken = session.claimRefresh(now.plus(properties.refreshLease()));
    return new BrowserSessionRefresh(
        session.getProviderSessionId(), session.getProviderSubject(), leaseToken);
  }

  @Transactional
  public BrowserSessionMetadata completeRefresh(
      BrowserSessionRefresh refresh,
      String providerSessionId,
      String providerSubject,
      String refreshCredential,
      OffsetDateTime providerRefreshExpiresAt) {
    BrowserSession session = requireLocked(refresh.providerSessionId());
    if (!session.ownsRefreshLease(refresh.leaseToken())) {
      throw ApiException.conflict(
          "session_refresh_superseded", "The browser session refresh was superseded.");
    }
    if (session.refreshLeaseExpired(now())) {
      throw ApiException.of(
          401,
          "session_revoked",
          "The browser session was revoked after its refresh deadline elapsed.");
    }
    if (!session.getProviderSessionId().equals(providerSessionId)
        || !session.getProviderSubject().equals(providerSubject)) {
      session.revoke(now());
      throw ApiException.of(
          502,
          "identity_provider_session_mismatch",
          "Identity provider rotated a different browser session.");
    }
    OffsetDateTime now = now();
    requireFuture(providerRefreshExpiresAt, now);
    OffsetDateTime idleExpiresAt =
        earliest(
            now.plus(properties.idleTimeout()),
            session.getAbsoluteExpiresAt(),
            providerRefreshExpiresAt);
    if (!idleExpiresAt.isAfter(now)) {
      session.revoke(now);
      throw expired(session, now);
    }
    session.completeRefresh(refresh.leaseToken(), fingerprint(refreshCredential), idleExpiresAt);
    return metadata(session, now);
  }

  @Transactional
  public boolean revokeRefresh(BrowserSessionRefresh refresh) {
    return sessions
        .findEntityByProviderSessionIdForUpdate(refresh.providerSessionId())
        .map(session -> session.revokeRefresh(refresh.leaseToken(), now()))
        .orElse(false);
  }

  @Transactional
  public void revoke(String refreshCredential) {
    sessions
        .findEntityByRefreshCredentialHashForUpdate(fingerprint(refreshCredential))
        .ifPresent(session -> session.revoke(now()));
  }

  @Transactional
  public int purgeRetained() {
    return sessions.deleteRetainedBefore(now().minus(properties.retention()));
  }

  private BrowserSession requireLocked(String providerSessionId) {
    return sessions
        .findEntityByProviderSessionIdForUpdate(providerSessionId)
        .orElseThrow(
            () -> ApiException.of(401, "session_revoked", "The browser session was revoked."));
  }

  private void requireSubject(BrowserSession session, String providerSubject) {
    if (!session.getProviderSubject().equals(providerSubject)) {
      throw ApiException.of(
          401, "session_revoked", "The browser session does not match the current principal.");
    }
  }

  private void assertUsable(BrowserSession session, OffsetDateTime now) {
    if (!session.active()) {
      throw ApiException.of(401, "session_revoked", "The browser session was revoked.");
    }
    if (!session.getAbsoluteExpiresAt().isAfter(now) || !session.getIdleExpiresAt().isAfter(now)) {
      throw expired(session, now);
    }
  }

  private ApiException expired(BrowserSession session, OffsetDateTime now) {
    if (!session.getAbsoluteExpiresAt().isAfter(now)) {
      return ApiException.of(
          401, "session_absolute_expired", "The browser session reached its absolute deadline.");
    }
    return ApiException.of(
        401, "session_idle_expired", "The browser session expired after inactivity.");
  }

  private BrowserSessionMetadata metadata(BrowserSession session, OffsetDateTime now) {
    return new BrowserSessionMetadata(
        now,
        session.getStartedAt(),
        session.getIdleExpiresAt(),
        session.getAbsoluteExpiresAt(),
        session.active()
            && session.getIdleExpiresAt().isAfter(now)
            && session.getAbsoluteExpiresAt().isAfter(now));
  }

  private OffsetDateTime earliest(
      OffsetDateTime first, OffsetDateTime second, OffsetDateTime third) {
    OffsetDateTime earliest = first.isBefore(second) ? first : second;
    return earliest.isBefore(third) ? earliest : third;
  }

  private String fingerprint(String credential) {
    requireText(credential, "refresh credential");
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(credential.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  private void requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
  }

  private void requireFuture(OffsetDateTime value, OffsetDateTime now) {
    if (value == null || !value.isAfter(now)) {
      throw ApiException.of(
          502,
          "identity_provider_refresh_credential_expired",
          "Identity provider returned an unavailable refresh credential.");
    }
  }

  private OffsetDateTime now() {
    return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }
}
