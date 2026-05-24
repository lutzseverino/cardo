package com.odonta.billing.controller;

import com.odonta.authorization.spring.AuthenticatedUserReader;
import com.odonta.billing.model.PortalSessionRequest;
import com.odonta.billing.model.PortalSessionResponse;
import com.odonta.billing.service.PortalSessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${odonta.api.base-path}/billing/portal/sessions")
public class PortalSessionController {

  private final PortalSessionService portalSessions;
  private final AuthenticatedUserReader users;

  PortalSessionController(PortalSessionService portalSessions, AuthenticatedUserReader users) {
    this.portalSessions = portalSessions;
    this.users = users;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  PortalSessionResponse create(
      JwtAuthenticationToken authentication, @RequestBody @Valid PortalSessionRequest request) {
    return portalSessions.create(users.currentUser(authentication).id(), request);
  }
}
