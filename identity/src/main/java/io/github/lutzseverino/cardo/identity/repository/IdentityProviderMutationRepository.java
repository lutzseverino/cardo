package io.github.lutzseverino.cardo.identity.repository;

import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutation;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutationStatus;
import io.github.lutzseverino.cardo.identity.model.IdentityProviderMutationType;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdentityProviderMutationRepository
    extends JpaRepository<IdentityProviderMutation, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select mutation from IdentityProviderMutation mutation where mutation.id = :id")
  Optional<IdentityProviderMutation> findEntityByIdForUpdate(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<IdentityProviderMutation> findFirstEntityByEmailAndTypeOrderByCreatedAtDesc(
      String email, IdentityProviderMutationType type);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<IdentityProviderMutation> findFirstEntityByUserIdAndTypeAndStatusOrderByCreatedAtDesc(
      UUID userId, IdentityProviderMutationType type, IdentityProviderMutationStatus status);

  @Query(
      """
      select mutation.id from IdentityProviderMutation mutation
      where mutation.status = io.github.lutzseverino.cardo.identity.model.IdentityProviderMutationStatus.REQUESTED
      and mutation.nextAttemptAt <= :now
      and (mutation.leaseUntil is null or mutation.leaseUntil <= :now)
      order by mutation.nextAttemptAt, mutation.createdAt
      """)
  List<UUID> findReadyIds(@Param("now") OffsetDateTime now, Pageable page);
}
