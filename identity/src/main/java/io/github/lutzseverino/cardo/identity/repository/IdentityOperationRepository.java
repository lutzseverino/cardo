package io.github.lutzseverino.cardo.identity.repository;

import io.github.lutzseverino.cardo.identity.model.IdentityOperation;
import io.github.lutzseverino.cardo.identity.model.IdentityOperationType;
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

public interface IdentityOperationRepository extends JpaRepository<IdentityOperation, UUID> {

  Optional<IdentityOperation> findEntityByUserIdAndType(UUID userId, IdentityOperationType type);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select operation from IdentityOperation operation where operation.id = :id")
  Optional<IdentityOperation> findEntityByIdForUpdate(@Param("id") UUID id);

  @Query(
      """
      select operation.id from IdentityOperation operation
      where operation.status in (
        io.github.lutzseverino.cardo.identity.model.IdentityOperationStatus.REQUESTED,
        io.github.lutzseverino.cardo.identity.model.IdentityOperationStatus.AWAITING_USER)
      and operation.nextAttemptAt <= :now
      order by operation.nextAttemptAt, operation.createdAt
      """)
  List<UUID> findReadyIds(@Param("now") OffsetDateTime now, Pageable page);
}
