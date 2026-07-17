package io.github.lutzseverino.cardo.authorization.access;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AccessProfileService {

  private final AccessProfileRepository profiles;
  private final AccessProfileGrantRepository grants;

  public AccessProfileService(
      AccessProfileRepository profiles, AccessProfileGrantRepository grants) {
    this.profiles = profiles;
    this.grants = grants;
  }

  public List<AccessProfileResult> listAvailable(String product, UUID tenantId) {
    return profiles.findAvailable(product, tenantId).stream().map(this::toResult).toList();
  }

  public Optional<AccessProfileResult> getAvailable(UUID profileId, String product, UUID tenantId) {
    return profiles.findAvailableById(profileId, product, tenantId).map(this::toResult);
  }

  public List<AccessGrant> grants(UUID profileId) {
    return grants.findByProfileIdOrderByResourceTypeAscActionAsc(profileId).stream()
        .map(
            grant ->
                new AccessGrant(grant.getResourceType(), grant.getResourceId(), grant.getAction()))
        .toList();
  }

  private AccessProfileResult toResult(AccessProfileProjection profile) {
    return new AccessProfileResult(
        profile.getId(),
        profile.getProduct(),
        profile.getTenantId(),
        profile.getName(),
        profile.getDescription(),
        profile.isTemplate());
  }
}
