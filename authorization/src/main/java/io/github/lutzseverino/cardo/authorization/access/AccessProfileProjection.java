package io.github.lutzseverino.cardo.authorization.access;

import java.util.UUID;

public interface AccessProfileProjection {

  UUID getId();

  String getProduct();

  UUID getTenantId();

  String getName();

  String getDescription();

  boolean isTemplate();
}
