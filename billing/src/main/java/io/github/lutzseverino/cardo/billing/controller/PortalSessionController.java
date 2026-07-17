package io.github.lutzseverino.cardo.billing.controller;

import io.github.lutzseverino.cardo.authorization.spring.AuthenticatedUserReader;
import io.github.lutzseverino.cardo.billing.api.PortalSessionsApi;
import io.github.lutzseverino.cardo.billing.api.model.CreatePortalSessionRequest;
import io.github.lutzseverino.cardo.billing.api.model.PortalSessionResponse;
import io.github.lutzseverino.cardo.billing.mapper.PortalSessionTransportMapper;
import io.github.lutzseverino.cardo.billing.workflow.CreatePortalSessionWorkflow;
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

  private final PortalSessionTransportMapper mapper;
  private final CreatePortalSessionWorkflow createPortalSession;
  private final AuthenticatedUserReader users;

  @Override
  public ResponseEntity<PortalSessionResponse> createPortalSession(
      @Valid CreatePortalSessionRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            mapper.toResponse(
                createPortalSession.create(users.currentUser().id(), mapper.toInput(request))));
  }
}
