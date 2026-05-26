package com.odonta.billing.controller;

import com.odonta.authorization.spring.AuthenticatedUserReader;
import com.odonta.billing.api.PortalSessionsApi;
import com.odonta.billing.api.model.PortalSessionRequest;
import com.odonta.billing.api.model.PortalSessionResponse;
import com.odonta.billing.service.PortalSessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${odonta.api.base-path}")
public class PortalSessionController implements PortalSessionsApi {

  private final PortalSessionService portalSessions;
  private final AuthenticatedUserReader users;

  PortalSessionController(PortalSessionService portalSessions, AuthenticatedUserReader users) {
    this.portalSessions = portalSessions;
    this.users = users;
  }

  @Override
  public ResponseEntity<PortalSessionResponse> createPortalSession(
      @Valid PortalSessionRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(portalSessions.create(users.currentUser().id(), request));
  }
}
