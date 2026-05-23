package com.odonta.authorization.access;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AccessProfileRepository extends JpaRepository<AccessProfile, UUID> {

  @Query(
      """
      select p as accessProfile
      from AccessProfile p
      where p.product = :product and (p.tenantId is null or p.tenantId = :tenantId)
      order by p.template desc, p.name asc
      """)
  List<AccessProfileProjection> findAvailable(String product, UUID tenantId);

  @Query(
      """
      select p as accessProfile
      from AccessProfile p
      where p.id = :id and p.product = :product and (p.tenantId is null or p.tenantId = :tenantId)
      """)
  Optional<AccessProfileProjection> findAvailableById(UUID id, String product, UUID tenantId);
}
