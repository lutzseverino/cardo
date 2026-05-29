package com.odonta.identity.controller;

import com.odonta.common.web.SessionCookies;
import com.odonta.identity.api.SessionsApi;
import com.odonta.identity.api.model.AuthenticatedPrincipalResponse;
import com.odonta.identity.api.model.CreateSessionRequest;
import com.odonta.identity.config.SessionProperties;
import com.odonta.identity.mapper.AuthenticatedPrincipalMapper;
import com.odonta.identity.model.AuthenticationResult;
import com.odonta.identity.model.CreateSessionCommand;
import com.odonta.identity.reader.CurrentJwtReader;
import com.odonta.identity.service.AuthenticationService;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${odonta.api.base-path}")
@RequiredArgsConstructor
public class SessionController implements SessionsApi {

  private final SessionProperties properties;
  private final AuthenticatedPrincipalMapper mapper;
  private final CurrentJwtReader currentJwt;
  private final AuthenticationService authenticationService;

  @Override
  public ResponseEntity<AuthenticatedPrincipalResponse> createSession(
      @Valid CreateSessionRequest request) {
    CreateSessionCommand command =
        new CreateSessionCommand(request.getEmail(), request.getPassword());
    AuthenticationResult authentication = authenticationService.authenticate(command);
    return ResponseEntity.status(HttpStatus.CREATED)
        .header(
            HttpHeaders.SET_COOKIE,
            SessionCookies.create(properties.cookieName(), authentication.token(), properties.ttl())
                .toString())
        .body(mapper.toResponse(authentication.principal()));
  }

  @Override
  public ResponseEntity<AuthenticatedPrincipalResponse> getCurrentPrincipal() {
    Jwt jwt = currentJwt.current().getToken();
    return ResponseEntity.ok(
        mapper.toResponse(
            authenticationService.getCurrentPrincipal(
                jwt.getSubject(), jwt.getClaimAsString("sid"), expiresAt(jwt))));
  }

  @Override
  public ResponseEntity<Void> deleteCurrentSession() {
    authenticationService.revoke(currentJwt.current().getToken().getTokenValue());
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
