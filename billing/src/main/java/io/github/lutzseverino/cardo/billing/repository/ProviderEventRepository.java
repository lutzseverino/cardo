package io.github.lutzseverino.cardo.billing.repository;

import io.github.lutzseverino.cardo.billing.model.ProviderEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderEventRepository extends JpaRepository<ProviderEvent, UUID> {

  boolean existsByProviderAndProviderEventId(String provider, String providerEventId);
}
