package io.github.lutzseverino.cardo.identity.reader;

import io.github.lutzseverino.cardo.common.api.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentJwtReader {

  public JwtAuthenticationToken current() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwt) {
      return jwt;
    }
    throw ApiException.of(401, "authentication_required", "Authentication is required.");
  }
}
