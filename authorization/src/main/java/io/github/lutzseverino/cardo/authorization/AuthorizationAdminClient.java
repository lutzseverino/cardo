package io.github.lutzseverino.cardo.authorization;

import io.github.lutzseverino.cardo.authorization.grant.ClientRoleAssignment;
import io.github.lutzseverino.cardo.authorization.grant.ClientRoleRevocation;
import io.github.lutzseverino.cardo.authorization.grant.GrantedResourceAction;
import io.github.lutzseverino.cardo.authorization.grant.ResourceActionAssignment;
import io.github.lutzseverino.cardo.authorization.grant.ResourceGrantQuery;
import io.github.lutzseverino.cardo.authorization.resource.AuthorizationResource;
import io.github.lutzseverino.cardo.authorization.resource.CreatedAuthorizationResource;
import java.util.List;

public interface AuthorizationAdminClient {

  CreatedAuthorizationResource ensureResource(AuthorizationResource resource);

  void grantResourceActions(ResourceActionAssignment assignment);

  List<GrantedResourceAction> findResourceActionGrants(ResourceGrantQuery query);

  void revokeResourceActionGrant(String ticketId);

  void ensureClientRolesAssigned(ClientRoleAssignment assignment);

  void removeClientRoles(ClientRoleRevocation revocation);
}
