package io.github.lutzseverino.cardo.identity.productauth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

final class ActiveTokenValidationFilter extends OncePerRequestFilter {

  private final ActiveTokenValidator validator;
  private final AuthenticationEntryPoint entryPoint;

  ActiveTokenValidationFilter(ActiveTokenValidator validator, AuthenticationEntryPoint entryPoint) {
    this.validator = validator;
    this.entryPoint = entryPoint;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwt && !isActive(jwt)) {
      deny(request, response);
      return;
    }
    filterChain.doFilter(request, response);
  }

  private boolean isActive(JwtAuthenticationToken authentication) {
    try {
      return validator.isActive(authentication.getToken().getTokenValue());
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private void deny(HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
    SecurityContextHolder.clearContext();
    entryPoint.commence(
        request,
        response,
        new OAuth2AuthenticationException(
            new OAuth2Error("invalid_token", "Token is no longer active.", null)));
  }
}
