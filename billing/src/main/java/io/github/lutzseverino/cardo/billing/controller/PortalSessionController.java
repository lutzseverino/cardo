package io.github.lutzseverino.cardo.billing.controller;

import io.github.lutzseverino.cardo.authorization.spring.AuthenticatedUserReader;
import io.github.lutzseverino.cardo.billing.api.PortalSessionsApi;
import io.github.lutzseverino.cardo.billing.api.model.CreatePortalSessionRequest;
import io.github.lutzseverino.cardo.billing.api.model.PortalSessionResponse;
import io.github.lutzseverino.cardo.billing.mapper.BillingSessionTransportMapper;
import io.github.lutzseverino.cardo.billing.service.PortalSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${cardo.api.base-path}")
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
