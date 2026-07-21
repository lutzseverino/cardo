package io.github.lutzseverino.cardo.identity.productauth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;

final class SessionCookieAuthenticationFilter extends BearerTokenAuthenticationFilter {

  SessionCookieAuthenticationFilter(
      AuthenticationManager authenticationManager,
      BearerTokenResolver bearerTokenResolver,
      AuthenticationEntryPoint authenticationEntryPoint) {
    super(authenticationManager, authenticationConverter(bearerTokenResolver));
    setAuthenticationEntryPoint(authenticationEntryPoint);
  }

  private static BearerTokenAuthenticationConverter authenticationConverter(
      BearerTokenResolver bearerTokenResolver) {
    BearerTokenAuthenticationConverter converter = new BearerTokenAuthenticationConverter();
    converter.setBearerTokenResolver(bearerTokenResolver);
    return converter;
  }
}
