package io.github.lutzseverino.cardo.invite.controller;

import io.github.lutzseverino.cardo.invite.api.InvitationTokensApi;
import io.github.lutzseverino.cardo.invite.api.InvitationsApi;
import io.github.lutzseverino.cardo.invite.api.model.AcceptInvitationRequest;
import io.github.lutzseverino.cardo.invite.api.model.CreateInvitationRequest;
import io.github.lutzseverino.cardo.invite.api.model.CreateInvitationResponse;
import io.github.lutzseverino.cardo.invite.api.model.InvitationCompletionResponse;
import io.github.lutzseverino.cardo.invite.api.model.InvitationResponse;
import io.github.lutzseverino.cardo.invite.api.model.InvitationTokenResponse;
import io.github.lutzseverino.cardo.invite.mapper.InvitationTransportMapper;
import io.github.lutzseverino.cardo.invite.reader.ProductCallerReader;
import io.github.lutzseverino.cardo.invite.service.InvitationCompletionService;
import io.github.lutzseverino.cardo.invite.service.InvitationService;
import io.github.lutzseverino.cardo.invite.workflow.AcceptInvitationWorkflow;
import io.github.lutzseverino.cardo.invite.workflow.CreateInvitationWorkflow;
import io.github.lutzseverino.cardo.invite.workflow.RevokeInvitationWorkflow;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${cardo.api.base-path}")
@RequiredArgsConstructor
public class InvitationController implements InvitationsApi, InvitationTokensApi {

  private final ProductCallerReader callers;
  private final InvitationTransportMapper mapper;
  private final InvitationService invitations;
  private final InvitationCompletionService completions;
  private final AcceptInvitationWorkflow acceptInvitation;
  private final CreateInvitationWorkflow createInvitation;
  private final RevokeInvitationWorkflow revokeInvitation;

  @Override
  public ResponseEntity<CreateInvitationResponse> createInvitation(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              content =
                  @Content(
                      mediaType = "application/json",
                      schema = @Schema(implementation = CreateInvitationRequest.class)))
          @Valid @RequestBody
          CreateInvitationRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            mapper.toResponse(
                createInvitation.create(callers.currentProduct(), mapper.toInput(request))));
  }

  @Override
  public ResponseEntity<InvitationResponse> getInvitation(
      @Parameter(
              name = "invitationId",
              required = true,
              in = ParameterIn.PATH,
              schema = @Schema(type = "string", format = "uuid"))
          @PathVariable("invitationId")
          UUID invitationId) {
    return ResponseEntity.ok(
        mapper.toResponse(invitations.get(invitationId, callers.currentProduct())));
  }

  @Override
  public ResponseEntity<InvitationTokenResponse> getInvitationByToken(String token) {
    return ResponseEntity.ok(mapper.toResponse(invitations.get(token)));
  }

  @Override
  public ResponseEntity<InvitationCompletionResponse> requestInvitationCompletion(String token) {
    return ResponseEntity.accepted()
        .body(mapper.toResponse(completions.request(token, callers.currentProduct())));
  }

  @Override
  public ResponseEntity<InvitationCompletionResponse> getInvitationCompletion(String token) {
    return ResponseEntity.ok(mapper.toResponse(completions.get(token, callers.currentProduct())));
  }

  @Override
  public ResponseEntity<InvitationResponse> acceptInvitation(
      @Parameter(
              name = "invitationId",
              required = true,
              in = ParameterIn.PATH,
              schema = @Schema(type = "string", format = "uuid"))
          @PathVariable("invitationId")
          UUID invitationId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              content =
                  @Content(
                      mediaType = "application/json",
                      schema = @Schema(implementation = AcceptInvitationRequest.class)))
          @Valid @RequestBody
          AcceptInvitationRequest request) {
    return ResponseEntity.ok(
        mapper.toResponse(
            acceptInvitation.accept(
                invitationId, callers.currentProduct(), request.getAcceptedAt())));
  }

  @Override
  public ResponseEntity<InvitationResponse> revokeInvitation(
      @Parameter(
              name = "invitationId",
              required = true,
              in = ParameterIn.PATH,
              schema = @Schema(type = "string", format = "uuid"))
          @PathVariable("invitationId")
          UUID invitationId) {
    return ResponseEntity.ok(
        mapper.toResponse(revokeInvitation.revoke(invitationId, callers.currentProduct())));
  }
}
