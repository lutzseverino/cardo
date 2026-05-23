package com.odonta.billing.repository;

import com.odonta.billing.model.Entitlement;
import com.odonta.billing.model.EntitlementProjection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntitlementRepository extends JpaRepository<Entitlement, UUID> {

  Optional<EntitlementProjection> findProjectedBySubjectIdAndProduct(
      UUID subjectId, String product);
}
