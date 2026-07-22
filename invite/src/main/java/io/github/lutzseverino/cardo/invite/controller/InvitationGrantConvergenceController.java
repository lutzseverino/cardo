package io.github.lutzseverino.cardo.invite.controller;

import io.github.lutzseverino.cardo.invite.api.InvitationGrantConvergenceApi;
import io.github.lutzseverino.cardo.invite.api.model.InvitationGrantConvergenceResponse;
import io.github.lutzseverino.cardo.invite.mapper.InvitationGrantConvergenceTransportMapper;
import io.github.lutzseverino.cardo.invite.reader.ProductCallerReader;
import io.github.lutzseverino.cardo.invite.service.InvitationGrantConvergenceService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${cardo.api.base-path}")
@RequiredArgsConstructor
public class InvitationGrantConvergenceController implements InvitationGrantConvergenceApi {

  private final ProductCallerReader callers;
  private final InvitationGrantConvergenceTransportMapper mapper;
  private final InvitationGrantConvergenceService convergence;

  @Override
  public ResponseEntity<InvitationGrantConvergenceResponse> getInvitationGrantConvergence(
      UUID invitationId) {
    return ResponseEntity.ok(
        mapper.toResponse(convergence.get(invitationId, callers.currentProduct())));
  }
}
