package io.github.lutzseverino.cardo.invite.controller;

import io.github.lutzseverino.cardo.invite.api.InvitationGrantConvergenceApi;
import io.github.lutzseverino.cardo.invite.api.model.InvitationGrantConvergenceResponse;
import io.github.lutzseverino.cardo.invite.mapper.InvitationGrantConvergenceTransportMapper;
import io.github.lutzseverino.cardo.invite.reader.ProductCallerReader;
import io.github.lutzseverino.cardo.invite.service.InvitationGrantConvergenceService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
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
      @Parameter(
              name = "invitationId",
              required = true,
              in = ParameterIn.PATH,
              schema = @Schema(type = "string", format = "uuid"))
          @PathVariable("invitationId")
          UUID invitationId) {
    return ResponseEntity.ok(
        mapper.toResponse(convergence.get(invitationId, callers.currentProduct())));
  }
}
