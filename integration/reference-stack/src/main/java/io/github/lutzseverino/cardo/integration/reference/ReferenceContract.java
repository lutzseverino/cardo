package io.github.lutzseverino.cardo.integration.reference;

import io.github.lutzseverino.cardo.authorization.resource.AuthorizationResource;
import java.util.List;
import java.util.UUID;

final class ReferenceContract {

  static final String PRODUCT_CLIENT = "reference-product";
  static final String PRODUCT_OUTBOUND_CLIENT = "reference-product-outbound";
  static final UUID TENANT_ID = UUID.fromString("34000000-0000-0000-0000-000000000001");
  static final String TENANT_RESOURCE_TYPE = PRODUCT_OUTBOUND_CLIENT + ":tenant";
  static final String TENANT_RESOURCE = TENANT_RESOURCE_TYPE + ":" + TENANT_ID;
  static final String TENANT_ACTION = "read";
  static final String TENANT_AUTHORITY = TENANT_RESOURCE + ":" + TENANT_ACTION;

  private ReferenceContract() {}

  static AuthorizationResource tenantResource() {
    return new AuthorizationResource(
        PRODUCT_CLIENT, TENANT_RESOURCE, TENANT_RESOURCE_TYPE, null, List.of(TENANT_ACTION));
  }
}
