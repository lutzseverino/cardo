package com.odonta.identity.client;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface IdentityUsersClient {

  ProvisionalUser createProvisional(String email);

  ProvisionalUser completeProvisional(UUID userId, String name, String password);

  void cancelProvisional(UUID userId);

  List<IdentityUser> searchByAuthorizationSubjects(Collection<String> subjects);

  IdentityUser get(UUID userId);
}
