package com.odonta.authorization.sync;

import com.odonta.authorization.resource.AuthorizationResource;
import java.util.List;

public sealed interface AuthorizationSyncOperation
    permits ProvisionAuthorizationResource,
        GrantAuthorizationResourceActions,
        AssignAuthorizationAuthorities {

  String uniqueKey();

  static AuthorizationSyncOperation provision(String uniqueKey, AuthorizationResource resource) {
    return new ProvisionAuthorizationResource(uniqueKey, resource);
  }

  static AuthorizationSyncOperation grant(
      String uniqueKey,
      String resourceServerClientId,
      String resourceName,
      String requesterSubject,
      List<String> actions) {
    return new GrantAuthorizationResourceActions(
        uniqueKey, resourceServerClientId, resourceName, requesterSubject, actions);
  }

  static AuthorizationSyncOperation assignAuthorities(
      String uniqueKey,
      String resourceServerClientId,
      String requesterSubject,
      List<String> authorities) {
    return new AssignAuthorizationAuthorities(
        uniqueKey, resourceServerClientId, requesterSubject, authorities);
  }
}
