package com.odonta.authorization;

import com.odonta.authorization.grant.ClientRoleAssignment;
import com.odonta.authorization.grant.ClientRoleRevocation;
import com.odonta.authorization.grant.GrantedResourceAction;
import com.odonta.authorization.grant.ResourceActionAssignment;
import com.odonta.authorization.grant.ResourceGrantQuery;
import com.odonta.authorization.resource.AuthorizationResource;
import com.odonta.authorization.resource.CreatedAuthorizationResource;
import java.util.List;

public interface AuthorizationAdminClient {

  CreatedAuthorizationResource ensureResource(AuthorizationResource resource);

  void grantResourceActions(ResourceActionAssignment assignment);

  List<GrantedResourceAction> findResourceActionGrants(ResourceGrantQuery query);

  void revokeResourceActionGrant(String ticketId);

  void ensureClientRolesAssigned(ClientRoleAssignment assignment);

  void removeClientRoles(ClientRoleRevocation revocation);
}
