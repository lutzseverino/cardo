package com.odonta.identity.controller;

import com.odonta.common.web.SessionCookies;
import com.odonta.identity.config.SessionProperties;
import com.odonta.identity.model.AuthenticatedPrincipalResponse;
import com.odonta.identity.model.AuthenticationResult;
import com.odonta.identity.model.CreateSessionRequest;
import com.odonta.identity.service.AuthenticationService;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${odonta.api.base-path}/identity/sessions")
public class SessionController {

  private final SessionProperties properties;
  private final AuthenticationService authenticationService;

  SessionController(SessionProperties properties, AuthenticationService authenticationService) {
    this.properties = properties;
    this.authenticationService = authenticationService;
  }

  @PostMapping
  ResponseEntity<AuthenticatedPrincipalResponse> create(
      @RequestBody @Valid CreateSessionRequest request) {
    AuthenticationResult authentication = authenticationService.authenticate(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .header(
            HttpHeaders.SET_COOKIE,
            SessionCookies.create(properties.cookieName(), authentication.token(), properties.ttl())
                .toString())
        .body(authentication.principal());
  }

  @GetMapping("/current")
  AuthenticatedPrincipalResponse get(JwtAuthenticationToken authentication) {
    Jwt jwt = authentication.getToken();
    return authenticationService.getCurrentPrincipal(
        jwt.getSubject(), jwt.getClaimAsString("sid"), expiresAt(jwt));
  }

  @DeleteMapping("/current")
  ResponseEntity<Void> delete(JwtAuthenticationToken authentication) {
    authenticationService.revoke(authentication.getToken().getTokenValue());
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, SessionCookies.expire(properties.cookieName()).toString())
        .build();
  }

  private OffsetDateTime expiresAt(Jwt jwt) {
    if (jwt.getExpiresAt() == null) {
      return null;
    }
    return OffsetDateTime.ofInstant(jwt.getExpiresAt(), ZoneOffset.UTC);
  }
}
