package io.github.lutzseverino.cardo.invite.controller;

import io.github.lutzseverino.cardo.authorization.spring.AuthenticatedUserReader;
import io.github.lutzseverino.cardo.invite.api.InvitationsApi;
import io.github.lutzseverino.cardo.invite.api.model.CompleteInvitationRequest;
import io.github.lutzseverino.cardo.invite.api.model.CreateInvitationRequest;
import io.github.lutzseverino.cardo.invite.api.model.CreateInvitationResponse;
import io.github.lutzseverino.cardo.invite.api.model.InvitationCompletionResponse;
import io.github.lutzseverino.cardo.invite.api.model.InvitationResponse;
import io.github.lutzseverino.cardo.invite.mapper.InvitationTransportMapper;
import io.github.lutzseverino.cardo.invite.service.InvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${cardo.api.base-path}")
@RequiredArgsConstructor
public class InvitationController implements InvitationsApi {

  private final AuthenticatedUserReader users;
  private final InvitationTransportMapper mapper;
  private final InvitationService invitations;

  @Override
  public ResponseEntity<CreateInvitationResponse> createInvitation(
      @Valid CreateInvitationRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(mapper.toResponse(invitations.create(users.currentUser(), mapper.toInput(request))));
  }

  @Override
  public ResponseEntity<InvitationResponse> getInvitation(String token) {
    return ResponseEntity.ok(mapper.toResponse(invitations.get(token)));
  }

  @Override
  public ResponseEntity<InvitationCompletionResponse> completeInvitation(
      String token, @Valid CompleteInvitationRequest request) {
    invitations.complete(token, mapper.toInput(request));
    return ResponseEntity.status(HttpStatus.CREATED).body(new InvitationCompletionResponse(true));
  }

  @Override
  public ResponseEntity<Void> acceptInvitation(String token) {
    invitations.accept(token, users.currentUser());
    return ResponseEntity.noContent().build();
  }
}
