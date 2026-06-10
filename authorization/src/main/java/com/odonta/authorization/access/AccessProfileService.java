package com.odonta.authorization.access;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AccessProfileService {

  private final AccessProfileRepository profiles;
  private final AccessProfileGrantRepository grants;

  public AccessProfileService(
      AccessProfileRepository profiles, AccessProfileGrantRepository grants) {
    this.profiles = profiles;
    this.grants = grants;
  }

  public List<AccessProfileProjection> availableProfiles(String product, UUID tenantId) {
    return profiles.findAvailable(product, tenantId);
  }

  public Optional<AccessProfileProjection> availableProfile(
      UUID profileId, String product, UUID tenantId) {
    return profiles.findAvailableById(profileId, product, tenantId);
  }

  public List<AccessGrant> grants(UUID profileId) {
    return grants.findByProfileIdOrderByResourceTypeAscActionAsc(profileId).stream()
        .map(
            grant ->
                new AccessGrant(grant.getResourceType(), grant.getResourceId(), grant.getAction()))
        .toList();
  }
}
