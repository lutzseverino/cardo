package io.github.lutzseverino.cardo.identity.repository;

import io.github.lutzseverino.cardo.identity.model.BrowserSession;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BrowserSessionRepository extends JpaRepository<BrowserSession, String> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select session from BrowserSession session where session.refreshCredentialHash = :hash")
  Optional<BrowserSession> findEntityByRefreshCredentialHashForUpdate(@Param("hash") String hash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select session from BrowserSession session where session.providerSessionId = :sessionId")
  Optional<BrowserSession> findEntityByProviderSessionIdForUpdate(
      @Param("sessionId") String sessionId);

  @Modifying
  @Query(
      """
      delete from BrowserSession session
      where session.absoluteExpiresAt < :threshold
      or session.revokedAt < :threshold
      """)
  int deleteRetainedBefore(@Param("threshold") OffsetDateTime threshold);
}
