package com.odonta.authorization.grant;

import com.odonta.authorization.AuthorizationAdminClient;

class RevocationProcessor {

  private final AuthorizationAdminClient authorization;

  RevocationProcessor(AuthorizationAdminClient authorization) {
    this.authorization = authorization;
  }

  void apply(RevocationPlan plan) {
    plan.resourceRevocations().forEach(this::revoke);
    plan.authorityRevocations().forEach(this::revoke);
  }

  private void revoke(RevocationPlan.ResourceRevocation revocation) {
    authorization
        .findResourceActionGrants(
            ResourceGrantQuery.forResourceName(
                revocation.resourceServerClientId(),
                revocation.resourceName(),
                revocation.subject()))
        .stream()
        .filter(GrantedResourceAction::granted)
        .filter(grant -> revocation.actions().contains(grant.action()))
        .map(GrantedResourceAction::id)
        .forEach(authorization::revokeResourceActionGrant);
  }

  private void revoke(RevocationPlan.AuthorityRevocation revocation) {
    authorization.removeClientRoles(
        new ClientRoleRevocation(
            revocation.resourceServerClientId(), revocation.subject(), revocation.authorities()));
  }
}
