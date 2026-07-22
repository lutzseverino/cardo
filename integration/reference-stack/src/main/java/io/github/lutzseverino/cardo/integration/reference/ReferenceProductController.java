package io.github.lutzseverino.cardo.integration.reference;

import io.github.lutzseverino.cardo.authorization.grant.GrantReceipt;
import io.github.lutzseverino.cardo.authorization.grant.Grants;
import io.github.lutzseverino.cardo.authorization.spring.AuthenticatedUser;
import io.github.lutzseverino.cardo.authorization.spring.AuthenticatedUserReader;
import io.github.lutzseverino.cardo.billing.client.BillingEntitlement;
import io.github.lutzseverino.cardo.billing.client.BillingEntitlementsClient;
import io.github.lutzseverino.cardo.invite.client.InvitationCompletion;
import io.github.lutzseverino.cardo.invite.client.InvitationToken;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ReferenceProductController {

  private final ReferenceWorkflow workflow;
  private final ReferenceProductStore store;
  private final Grants grants;
  private final AuthenticatedUserReader users;
  private final BillingEntitlementsClient billing;
  private final ReferenceGrantGate grantGate;
  private final ReferenceOwnerSetup ownerSetup;
  private final String controlSecret;

  ReferenceProductController(
      ReferenceWorkflow workflow,
      ReferenceProductStore store,
      Grants grants,
      AuthenticatedUserReader users,
      BillingEntitlementsClient billing,
      ReferenceGrantGate grantGate,
      ReferenceOwnerSetup ownerSetup,
      @Value("${reference.control-secret}") String controlSecret) {
    this.workflow = workflow;
    this.store = store;
    this.grants = grants;
    this.users = users;
    this.billing = billing;
    this.grantGate = grantGate;
    this.ownerSetup = ownerSetup;
    this.controlSecret = controlSecret;
  }

  @GetMapping("/")
  Map<String, String> home() {
    return Map.of("service", "reference-product", "status", "ready");
  }

  @GetMapping("/invitations/accept/{token}")
  InvitationToken resolveInvitation(@PathVariable String token) {
    return workflow.resolve(token);
  }

  @PostMapping("/invitations/accept/{token}/credential-setup")
  ResponseEntity<InvitationCompletion> requestCredentialSetup(@PathVariable String token) {
    return ResponseEntity.accepted().body(workflow.requestCredentialSetup(token));
  }

  @GetMapping("/invitations/accept/{token}/credential-setup")
  InvitationCompletion credentialSetup(@PathVariable String token) {
    return workflow.credentialSetup(token);
  }

  @GetMapping("/invitations/completed")
  Map<String, String> credentialSetupCompleted() {
    return Map.of("status", "credential-setup-completed");
  }

  @PostMapping("/api/reference/invitations")
  ResponseEntity<ReferenceProductStore.InvitationState> invite(@RequestBody InviteRequest request) {
    AuthenticatedUser owner = users.currentUser();
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(workflow.create(request.requestId(), request.email(), owner.id()));
  }

  @PostMapping("/api/reference/invitations/{id}/accept")
  ResponseEntity<Void> accept(@PathVariable UUID id) {
    workflow.accept(
        id, users.currentUser().authorizationSubject(), OffsetDateTime.now(ZoneOffset.UTC));
    return ResponseEntity.accepted().build();
  }

  @GetMapping("/api/reference/convergence/{id}")
  ConvergenceResponse convergence(@PathVariable UUID id) {
    UUID receiptId = store.invitation(id).receiptId();
    if (receiptId == null) {
      return new ConvergenceResponse(null, "NOT_STAGED", null);
    }
    GrantReceipt receipt =
        grants
            .find(receiptId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Retained authorization receipt is missing from durable storage."));
    return new ConvergenceResponse(receipt.id(), receipt.status().name(), receipt.failureCode());
  }

  @GetMapping("/api/reference/tenants/{id}")
  Map<String, Object> tenant(@PathVariable UUID id) {
    if (!ReferenceContract.TENANT_ID.equals(id)) {
      throw new IllegalArgumentException("Unknown reference tenant.");
    }
    return Map.of("id", id, "name", "Reference Tenant");
  }

  @GetMapping("/api/reference/billing/{subjectId}")
  BillingEntitlement billing(@PathVariable UUID subjectId) {
    return billing.require(subjectId, "reference-product");
  }

  @PostMapping("/internal/reference/grants/pause")
  void pause(@RequestHeader("X-Reference-Control") String secret) {
    authorizeControl(secret);
    grantGate.pause();
  }

  @PostMapping("/internal/reference/owner")
  GrantReceipt setupOwner(
      @RequestHeader("X-Reference-Control") String secret, @RequestBody Map<String, String> input) {
    authorizeControl(secret);
    return ownerSetup.create(input.get("subject"));
  }

  @GetMapping("/internal/reference/grants/{receiptId}")
  GrantReceipt grant(
      @RequestHeader("X-Reference-Control") String secret, @PathVariable UUID receiptId) {
    authorizeControl(secret);
    return grants
        .find(receiptId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown reference grant receipt."));
  }

  @PostMapping("/internal/reference/grants/release")
  void release(@RequestHeader("X-Reference-Control") String secret) {
    authorizeControl(secret);
    grantGate.release();
  }

  @PostMapping("/internal/reference/fail-after-invite-accept")
  void failAfterAccept(@RequestHeader("X-Reference-Control") String secret) {
    authorizeControl(secret);
    workflow.failNextAfterRemoteAccept();
  }

  private void authorizeControl(String secret) {
    if (!controlSecret.equals(secret)) {
      throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND);
    }
  }

  record InviteRequest(UUID requestId, String email) {}

  record ConvergenceResponse(UUID receiptId, String status, String failureCode) {}
}
