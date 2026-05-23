package com.odonta.authorization;

import com.odonta.authorization.grant.AuthorityGrant;
import com.odonta.authorization.grant.GrantedResourceAction;
import com.odonta.authorization.grant.ResourceActionGrant;
import com.odonta.authorization.grant.ResourceGrantQuery;
import com.odonta.authorization.resource.AuthorizationResource;
import com.odonta.authorization.resource.CreatedAuthorizationResource;
import java.util.List;
import java.util.Optional;

public interface AuthorizationAdminClient {

  CreatedAuthorizationResource createResource(AuthorizationResource resource);

  Optional<CreatedAuthorizationResource> findResourceByName(
      String resourceServerClientId, String resourceName);

  void grantResourceActions(ResourceActionGrant grant);

  List<GrantedResourceAction> findResourceActionGrants(ResourceGrantQuery query);

  void revokeResourceActionGrant(String ticketId);

  void ensureClientRolesAssigned(AuthorityGrant grant);
}
