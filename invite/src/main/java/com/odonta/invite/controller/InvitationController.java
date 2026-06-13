package com.odonta.invite.controller;

import com.odonta.authorization.spring.AuthenticatedUserReader;
import com.odonta.invite.api.InvitationsApi;
import com.odonta.invite.api.model.CompleteInvitationInput;
import com.odonta.invite.api.model.CreateInvitationInput;
import com.odonta.invite.api.model.CreateInvitationResponse;
import com.odonta.invite.api.model.InvitationCompletionResponse;
import com.odonta.invite.api.model.InvitationResponse;
import com.odonta.invite.mapper.InvitationMapper;
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
      @Valid CreateInvitationInput input) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(mapper.toResponse(invitations.create(users.currentUser(), input)));
  }

  @Override
  public ResponseEntity<InvitationResponse> getInvitation(String token) {
    return ResponseEntity.ok(mapper.toResponse(invitations.get(token)));
  }

  @Override
  public ResponseEntity<InvitationCompletionResponse> completeInvitation(
      String token, @Valid CompleteInvitationInput input) {
    invitations.complete(token, input);
    return ResponseEntity.status(HttpStatus.CREATED).body(new InvitationCompletionResponse(true));
  }

  @Override
  public ResponseEntity<Void> acceptInvitation(String token) {
    invitations.accept(token, users.currentUser());
    return ResponseEntity.noContent().build();
  }
}
