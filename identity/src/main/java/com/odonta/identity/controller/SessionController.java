package com.odonta.identity.controller;

import com.odonta.common.web.SessionCookies;
import com.odonta.identity.api.SessionsApi;
import com.odonta.identity.api.model.AuthenticateRequest;
import com.odonta.identity.api.model.AuthenticatedPrincipalResponse;
import com.odonta.identity.config.SessionProperties;
import com.odonta.identity.mapper.AuthenticationTransportMapper;
import com.odonta.identity.model.AuthenticationResult;
import com.odonta.identity.service.AuthenticationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${odonta.api.base-path}")
@RequiredArgsConstructor
public class SessionController implements SessionsApi {

  private final SessionProperties properties;
  private final AuthenticationTransportMapper mapper;
  private final AuthenticationService authenticationService;

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
    AuthenticationResult authentication = authenticationService.getCurrentPrincipal();
    return ResponseEntity.ok(mapper.toResponse(authentication));
  }

  @Override
  public ResponseEntity<Void> deleteCurrentSession() {
    authenticationService.revokeCurrent();
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, SessionCookies.expire(properties.cookieName()).toString())
        .build();
  }
}
