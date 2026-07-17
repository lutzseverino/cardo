package io.github.lutzseverino.cardo.identity.controller;

import io.github.lutzseverino.cardo.common.web.SessionCookies;
import io.github.lutzseverino.cardo.identity.api.SessionsApi;
import io.github.lutzseverino.cardo.identity.api.model.AuthenticateRequest;
import io.github.lutzseverino.cardo.identity.api.model.AuthenticatedPrincipalResponse;
import io.github.lutzseverino.cardo.identity.config.SessionProperties;
import io.github.lutzseverino.cardo.identity.mapper.AuthenticationTransportMapper;
import io.github.lutzseverino.cardo.identity.model.AuthenticationResult;
import io.github.lutzseverino.cardo.identity.model.CurrentAuthentication;
import io.github.lutzseverino.cardo.identity.reader.CurrentJwtReader;
import io.github.lutzseverino.cardo.identity.service.AuthenticationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${cardo.api.base-path}")
@RequiredArgsConstructor
public class SessionController implements SessionsApi {

  private final SessionProperties properties;
  private final AuthenticationTransportMapper mapper;
  private final AuthenticationService authenticationService;
  private final CurrentJwtReader currentJwt;

  @Override
  public ResponseEntity<AuthenticatedPrincipalResponse> authenticate(
      @Valid AuthenticateRequest request) {
    AuthenticationResult authentication =
        authenticationService.authenticate(mapper.toInput(request));
    return ResponseEntity.status(HttpStatus.CREATED)
        .header(
            HttpHeaders.SET_COOKIE,
            SessionCookies.create(properties.cookieName(), authentication.token(), properties.ttl())
                .toString())
        .body(mapper.toResponse(authentication));
  }

  @Override
  public ResponseEntity<AuthenticatedPrincipalResponse> getCurrentPrincipal() {
    CurrentAuthentication current = currentJwt.current();
    AuthenticationResult authentication = authenticationService.getCurrent(current);
    return ResponseEntity.ok(mapper.toResponse(authentication));
  }

  @Override
  public ResponseEntity<Void> deleteCurrentSession() {
    authenticationService.revoke(currentJwt.current().accessToken());
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, SessionCookies.expire(properties.cookieName()).toString())
        .build();
  }
}
