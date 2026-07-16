package io.github.lutzseverino.cardo.authorization.access;

import java.util.UUID;

public interface AccessProfileGrantProjection {

  UUID getId();

  UUID getProfileId();

  String getResourceType();

  UUID getResourceId();

  String getAction();
}
