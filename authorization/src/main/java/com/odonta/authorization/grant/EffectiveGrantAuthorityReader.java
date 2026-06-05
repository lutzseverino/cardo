package com.odonta.authorization.grant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;

public class EffectiveGrantAuthorityReader {

  public List<EffectiveGrant> read(Collection<? extends GrantedAuthority> authorities) {
    Map<GrantedResource, List<String>> actionsByResource = new LinkedHashMap<>();
    authorities.stream()
        .map(GrantedAuthority::getAuthority)
        .map(this::grant)
        .flatMap(Collection::stream)
        .forEach(
            grant ->
                grant
                    .actions()
                    .forEach(action -> addAction(actionsByResource, grant.resource(), action)));
    return actionsByResource.entrySet().stream()
        .map(entry -> new EffectiveGrant(entry.getKey(), entry.getValue()))
        .toList();
  }

  private List<EffectiveGrant> grant(String authority) {
    String[] parts = authority == null ? new String[0] : authority.split(":", 4);
    if (parts.length != 4) {
      return List.of();
    }
    return List.of(
        new EffectiveGrant(
            new GrantedResource(parts[0] + ":" + parts[1], parts[2]), List.of(parts[3])));
  }

  private void addAction(
      Map<GrantedResource, List<String>> actionsByResource,
      GrantedResource resource,
      String action) {
    List<String> actions =
        actionsByResource.computeIfAbsent(resource, ignored -> new ArrayList<>());
    if (!actions.contains(action)) {
      actions.add(action);
    }
  }
}
