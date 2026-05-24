package com.odonta.billing.repository;

import com.odonta.billing.model.Entitlement;
import com.odonta.billing.model.EntitlementProjection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntitlementRepository extends JpaRepository<Entitlement, UUID> {

  Optional<EntitlementProjection> findProjectedBySubjectIdAndProduct(
      UUID subjectId, String product);

  Optional<Entitlement> findBySubjectIdAndProduct(UUID subjectId, String product);

  List<Entitlement> findBySubjectId(UUID subjectId);
}
