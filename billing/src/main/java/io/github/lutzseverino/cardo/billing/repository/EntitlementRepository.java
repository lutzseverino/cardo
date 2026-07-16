package io.github.lutzseverino.cardo.billing.repository;

import io.github.lutzseverino.cardo.billing.model.Entitlement;
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
