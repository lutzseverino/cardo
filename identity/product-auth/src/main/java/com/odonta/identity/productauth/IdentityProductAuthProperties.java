package com.odonta.identity.productauth;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "odonta.identity.product-auth")
public record IdentityProductAuthProperties(List<String> publicPaths, String sessionCookieName) {

  public IdentityProductAuthProperties {
    publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
    sessionCookieName =
        sessionCookieName == null || sessionCookieName.isBlank()
            ? "odonta.session"
            : sessionCookieName;
  }
}
