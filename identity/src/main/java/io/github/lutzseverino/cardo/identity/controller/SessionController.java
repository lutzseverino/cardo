package io.github.lutzseverino.cardo.identity.controller;

import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.api.SessionsApi;
import io.github.lutzseverino.cardo.identity.api.model.AuthenticateRequest;
import io.github.lutzseverino.cardo.identity.api.model.AuthenticatedPrincipalResponse;
import io.github.lutzseverino.cardo.identity.mapper.AuthenticationTransportMapper;
import io.github.lutzseverino.cardo.identity.model.AuthenticationResult;
import io.github.lutzseverino.cardo.identity.model.CurrentAuthentication;
import io.github.lutzseverino.cardo.identity.model.SessionResult;
import io.github.lutzseverino.cardo.identity.reader.CurrentJwtReader;
import io.github.lutzseverino.cardo.identity.service.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${cardo.api.base-path}")
@RequiredArgsConstructor
public class SessionController implements SessionsApi {

  private final AuthenticationTransportMapper mapper;
  private final AuthenticationService authenticationService;
  private final CurrentJwtReader currentJwt;
  private final SessionCookiePolicy cookies;
  private final HttpServletRequest request;

  @Override
  public ResponseEntity<AuthenticatedPrincipalResponse> authenticate(
      @Valid AuthenticateRequest request) {
    SessionResult session = authenticationService.authenticate(mapper.toInput(request));
    return response(HttpStatus.CREATED, session);
  }

  @Override
  public ResponseEntity<AuthenticatedPrincipalResponse> getCurrentPrincipal() {
    CurrentAuthentication current = currentJwt.current();
    AuthenticationResult authentication = authenticationService.getCurrent(current);
    return ResponseEntity.ok(mapper.toResponse(authentication));
  }

  @Override
  public ResponseEntity<Void> getCsrfToken() {
    CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    csrfToken.getToken();
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  @Override
  public ResponseEntity<AuthenticatedPrincipalResponse> refreshCurrentSession() {
    String refreshToken =
        cookies
            .refresh(request)
            .orElseThrow(
                () ->
                    ApiException.of(401, "authentication_required", "Authentication is required."));
    return response(HttpStatus.OK, authenticationService.refresh(refreshToken));
  }

  @Override
  public ResponseEntity<Void> deleteCurrentSession() {
    cookies.refresh(request).ifPresent(authenticationService::revoke);
    return ResponseEntity.noContent().headers(expiredCookieHeaders()).build();
  }

  private ResponseEntity<AuthenticatedPrincipalResponse> response(
      HttpStatus status, SessionResult session) {
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.SET_COOKIE, cookies.access(session.accessCredential()).toString());
    headers.add(HttpHeaders.SET_COOKIE, cookies.refresh(session.refreshCredential()).toString());
    return ResponseEntity.status(status)
        .headers(headers)
        .body(mapper.toResponse(session.authentication()));
  }

  private HttpHeaders expiredCookieHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.SET_COOKIE, cookies.expireAccess().toString());
    headers.add(HttpHeaders.SET_COOKIE, cookies.expireRefresh().toString());
    headers.add(HttpHeaders.SET_COOKIE, cookies.expireCsrf().toString());
    return headers;
  }
}
