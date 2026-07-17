package io.github.lutzseverino.cardo.identity.client;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface IdentityUsersClient {

  ProvisionalUser createProvisional(String email);

  IdentityOperation requestCredentialSetup(UUID userId, UUID operationId, OffsetDateTime notAfter);

  IdentityOperation getCredentialSetup(UUID userId, UUID operationId);

  IdentityOperation cancelProvisional(UUID userId);

  IdentityOperation getProvisionalDeletion(UUID userId);

  List<IdentityUser> searchByAuthorizationSubjects(Collection<String> subjects);

  IdentityUser get(UUID userId);
}
