package io.github.lutzseverino.cardo.identity.reader;

import io.github.lutzseverino.cardo.authorization.grant.EffectiveGrantAuthorityReader;
import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.model.CurrentAuthentication;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentJwtReader {

  private final EffectiveGrantAuthorityReader grants;

  public CurrentAuthentication current() {
    JwtAuthenticationToken jwt = currentToken();
    return new CurrentAuthentication(
        jwt.getName(),
        jwt.getToken().getClaimAsString("sid"),
        expiresAt(jwt),
        grants.read(jwt.getAuthorities()));
  }

  public String authorizationSubject() {
    return currentToken().getName();
  }

  private JwtAuthenticationToken currentToken() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwt) {
      return jwt;
    }
    throw ApiException.of(401, "authentication_required", "Authentication is required.");
  }

  private OffsetDateTime expiresAt(JwtAuthenticationToken authentication) {
    return authentication.getToken().getExpiresAt() == null
        ? null
        : OffsetDateTime.ofInstant(authentication.getToken().getExpiresAt(), ZoneOffset.UTC);
  }
}
