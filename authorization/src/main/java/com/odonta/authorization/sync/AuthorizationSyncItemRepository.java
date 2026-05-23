package com.odonta.authorization.sync;

import com.odonta.authorization.AuthorizationSyncStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthorizationSyncItemRepository
    extends JpaRepository<AuthorizationSyncItem, UUID> {

  Optional<AuthorizationSyncItem> findByUniqueKey(String uniqueKey);

  List<AuthorizationSyncItem> findTop50ByStatusOrderByCreatedAtAsc(AuthorizationSyncStatus status);
}
