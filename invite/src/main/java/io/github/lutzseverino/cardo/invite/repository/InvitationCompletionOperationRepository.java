package io.github.lutzseverino.cardo.invite.repository;

import io.github.lutzseverino.cardo.invite.model.InvitationCompletionOperation;
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

public interface InvitationCompletionOperationRepository
    extends JpaRepository<InvitationCompletionOperation, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select operation from InvitationCompletionOperation operation where operation.id = :id")
  Optional<InvitationCompletionOperation> findEntityByIdForUpdate(@Param("id") UUID id);

  @Query(
      """
      select operation.id from InvitationCompletionOperation operation
      where operation.status in (
        io.github.lutzseverino.cardo.invite.model.InvitationCompletionStatus.REQUESTED,
        io.github.lutzseverino.cardo.invite.model.InvitationCompletionStatus.AWAITING_IDENTITY)
      and operation.nextAttemptAt <= :now
      order by operation.nextAttemptAt, operation.createdAt
      """)
  List<UUID> findReadyIds(@Param("now") OffsetDateTime now, Pageable page);
}
