package com.odonta.identity.productauth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ActiveTokenValidationFilterTest {

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void continuesWhenAuthenticatedTokenIsActive() throws Exception {
    AtomicBoolean continued = new AtomicBoolean(false);
    ActiveTokenValidationFilter filter =
        new ActiveTokenValidationFilter(token -> true, (request, response, exception) -> {});
    SecurityContextHolder.getContext().setAuthentication(authentication());

    filter.doFilter(request(), new MockHttpServletResponse(), chain(continued));

    assertThat(continued).isTrue();
  }

  @Test
  void deniesInactiveAuthenticatedTokens() throws Exception {
    AtomicBoolean continued = new AtomicBoolean(false);
    MockHttpServletResponse response = new MockHttpServletResponse();
    ActiveTokenValidationFilter filter =
        new ActiveTokenValidationFilter(
            token -> false, (request, deniedResponse, exception) -> deniedResponse.setStatus(401));
    SecurityContextHolder.getContext().setAuthentication(authentication());

    filter.doFilter(request(), response, chain(continued));

    assertThat(continued).isFalse();
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void deniesTokensWhenValidationFails() throws Exception {
    AtomicBoolean continued = new AtomicBoolean(false);
    MockHttpServletResponse response = new MockHttpServletResponse();
    ActiveTokenValidationFilter filter =
        new ActiveTokenValidationFilter(
            token -> {
              throw new IllegalStateException("keycloak unavailable");
            },
            (request, deniedResponse, exception) -> deniedResponse.setStatus(401));
    SecurityContextHolder.getContext().setAuthentication(authentication());

    filter.doFilter(request(), response, chain(continued));

    assertThat(continued).isFalse();
    assertThat(response.getStatus()).isEqualTo(401);
  }

  private MockHttpServletRequest request() {
    return new MockHttpServletRequest("GET", "/api/v1/clinic/me");
  }

  private JwtAuthenticationToken authentication() {
    Jwt jwt =
        Jwt.withTokenValue("access-token")
            .header("alg", "RS256")
            .subject("subject-1")
            .issuedAt(Instant.parse("2026-06-23T12:00:00Z"))
            .expiresAt(Instant.parse("2026-06-23T12:05:00Z"))
            .build();
    return new JwtAuthenticationToken(jwt);
  }

  private FilterChain chain(AtomicBoolean continued) {
    return (request, response) -> continued.set(true);
  }
}
