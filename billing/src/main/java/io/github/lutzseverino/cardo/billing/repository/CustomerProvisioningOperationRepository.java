package io.github.lutzseverino.cardo.billing.repository;

import io.github.lutzseverino.cardo.billing.model.CustomerProvisioningOperation;
import io.github.lutzseverino.cardo.billing.model.CustomerProvisioningStatus;
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

public interface CustomerProvisioningOperationRepository
    extends JpaRepository<CustomerProvisioningOperation, UUID> {

  Optional<CustomerProvisioningOperation> findFirstEntityBySubjectIdAndProviderAndStatus(
      UUID subjectId, String provider, CustomerProvisioningStatus status);

  Optional<CustomerProvisioningOperation> findFirstEntityBySubjectIdAndProviderOrderByCreatedAtDesc(
      UUID subjectId, String provider);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select operation from CustomerProvisioningOperation operation where operation.id = :id")
  Optional<CustomerProvisioningOperation> findEntityByIdForUpdate(@Param("id") UUID id);

  @Query(
      """
      select operation.id from CustomerProvisioningOperation operation
      where operation.status =
        io.github.lutzseverino.cardo.billing.model.CustomerProvisioningStatus.REQUESTED
      and operation.nextAttemptAt <= :now
      order by operation.nextAttemptAt, operation.createdAt
      """)
  List<UUID> findReadyIds(@Param("now") OffsetDateTime now, Pageable page);
}
