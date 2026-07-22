package io.github.lutzseverino.cardo.invite.repository;

import io.github.lutzseverino.cardo.invite.model.Invitation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

  @Query(
      value =
          "SELECT pg_advisory_xact_lock(hashtextextended(CONCAT(:product, ':', CAST(:requestId AS text)), 0))",
      nativeQuery = true)
  void lockCreation(@Param("product") String product, @Param("requestId") UUID requestId);

  Optional<InvitationProjection> findProjectedById(UUID id);

  Optional<InvitationProjection> findProjectedByToken(String token);

  Optional<Invitation> findEntityById(UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select invitation from Invitation invitation where invitation.id = :id")
  Optional<Invitation> findEntityByIdForUpdate(@Param("id") UUID id);

  Optional<Invitation> findEntityByToken(String token);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select invitation from Invitation invitation where invitation.token = :token")
  Optional<Invitation> findEntityByTokenForUpdate(@Param("token") String token);

  Optional<InvitationProjection> findProjectedByProductAndRequestId(String product, UUID requestId);
}
