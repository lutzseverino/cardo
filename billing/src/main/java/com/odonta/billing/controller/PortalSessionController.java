package com.odonta.billing.controller;

import com.odonta.authorization.spring.AuthenticatedUserReader;
import com.odonta.billing.api.PortalSessionsApi;
import com.odonta.billing.api.model.CreatePortalSessionRequest;
import com.odonta.billing.api.model.PortalSessionResponse;
import com.odonta.billing.mapper.BillingSessionTransportMapper;
import com.odonta.billing.service.PortalSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${odonta.api.base-path}")
@RequiredArgsConstructor
public class PortalSessionController implements PortalSessionsApi {

  private final BillingSessionTransportMapper mapper;
  private final PortalSessionService portalSessions;
  private final AuthenticatedUserReader users;

  @Override
  public ResponseEntity<PortalSessionResponse> createPortalSession(
      @Valid CreatePortalSessionRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            mapper.toPortalResponse(
                portalSessions.create(users.currentUser().id(), mapper.toInput(request))));
  }
}
