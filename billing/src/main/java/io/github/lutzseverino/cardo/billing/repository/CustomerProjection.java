package io.github.lutzseverino.cardo.billing.repository;

import java.util.UUID;

public interface CustomerProjection {

  UUID getId();

  UUID getSubjectId();

  String getProvider();

  String getProviderCustomerId();
}
