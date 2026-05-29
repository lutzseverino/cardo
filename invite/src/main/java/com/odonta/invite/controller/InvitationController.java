package com.odonta.invite.controller;

import com.odonta.authorization.spring.AuthenticatedUserReader;
import com.odonta.invite.api.InvitationsApi;
import com.odonta.invite.api.model.CompleteInvitationRequest;
import com.odonta.invite.api.model.CreateInvitationRequest;
import com.odonta.invite.api.model.CreateInvitationResponse;
import com.odonta.invite.api.model.InvitationCompletionResponse;
import com.odonta.invite.api.model.InvitationResponse;
import com.odonta.invite.mapper.InvitationMapper;
import com.odonta.invite.model.CompleteInvitationCommand;
import com.odonta.invite.model.CreateInvitationCommand;
import com.odonta.invite.service.InvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${odonta.api.base-path}")
@RequiredArgsConstructor
public class InvitationController implements InvitationsApi {

  private final AuthenticatedUserReader users;
  private final InvitationMapper mapper;
  private final InvitationService invitations;

  @Override
  public ResponseEntity<CreateInvitationResponse> createInvitation(
      @Valid CreateInvitationRequest request) {
    CreateInvitationCommand command =
        new CreateInvitationCommand(
            request.getTenantId(),
            request.getTenantResourceType(),
            request.getEmail(),
            request.getAccessProfileId());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(mapper.toResponse(invitations.create(users.currentUser(), command)));
  }

  @Override
  public ResponseEntity<InvitationResponse> getInvitation(String token) {
    return ResponseEntity.ok(mapper.toResponse(invitations.get(token)));
  }

  @Override
  public ResponseEntity<InvitationCompletionResponse> completeInvitation(
      String token, @Valid CompleteInvitationRequest request) {
    CompleteInvitationCommand command =
        new CompleteInvitationCommand(request.getName(), request.getPassword());
    invitations.complete(token, command);
    return ResponseEntity.status(HttpStatus.CREATED).body(new InvitationCompletionResponse(true));
  }

  @Override
  public ResponseEntity<Void> acceptInvitation(String token) {
    invitations.accept(token, users.currentUser());
    return ResponseEntity.noContent().build();
  }
}
