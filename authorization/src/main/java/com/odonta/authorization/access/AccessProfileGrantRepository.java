package com.odonta.authorization.access;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessProfileGrantRepository extends JpaRepository<AccessProfileGrant, UUID> {

  List<AccessProfileGrantProjection> findByProfileIdOrderByResourceTypeAscActionAsc(UUID profileId);
}
