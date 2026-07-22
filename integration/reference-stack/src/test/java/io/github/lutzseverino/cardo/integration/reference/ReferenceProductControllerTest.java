package io.github.lutzseverino.cardo.integration.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.authorization.grant.GrantReceipt;
import io.github.lutzseverino.cardo.authorization.grant.GrantReceiptStatus;
import io.github.lutzseverino.cardo.authorization.grant.Grants;
import io.github.lutzseverino.cardo.authorization.spring.AuthenticatedUser;
import io.github.lutzseverino.cardo.authorization.spring.AuthenticatedUserReader;
import io.github.lutzseverino.cardo.billing.client.BillingEntitlementsClient;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ReferenceProductControllerTest {

  private static final UUID INVITATION = UUID.fromString("34000000-0000-0000-0000-000000000020");
  private static final UUID RECEIPT = UUID.fromString("34000000-0000-0000-0000-000000000021");

  @Test
  void mapsPendingAppliedAndFailedConvergenceWithoutInventingInviteState() {
    ReferenceProductStore store = mock(ReferenceProductStore.class);
    Grants grants = mock(Grants.class);
    when(store.invitation(INVITATION)).thenReturn(invitation(RECEIPT));
    ReferenceProductController controller = controller(store, grants);

    for (GrantReceiptStatus status : GrantReceiptStatus.values()) {
      String failure = status == GrantReceiptStatus.FAILED ? "provider_application_failed" : null;
      when(grants.find(RECEIPT))
          .thenReturn(Optional.of(new GrantReceipt(RECEIPT, status, failure)));
      ReferenceProductController.ConvergenceResponse response = controller.convergence(INVITATION);
      assertThat(response.receiptId()).isEqualTo(RECEIPT);
      assertThat(response.status()).isEqualTo(status.name());
      assertThat(response.failureCode()).isEqualTo(failure);
    }
  }

  @Test
  void treatsAnUnknownRetainedReceiptAsAnIntegrityFailure() {
    ReferenceProductStore store = mock(ReferenceProductStore.class);
    Grants grants = mock(Grants.class);
    when(store.invitation(INVITATION)).thenReturn(invitation(RECEIPT));
    when(grants.find(RECEIPT)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> controller(store, grants).convergence(INVITATION))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Retained authorization receipt is missing from durable storage.");
  }

  @Test
  void fixtureControlsRequireTheEphemeralHarnessSecret() {
    ReferenceProductController controller = controller(mock(), mock());

    assertThatThrownBy(() -> controller.pause("wrong"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  private ReferenceProductController controller(ReferenceProductStore store, Grants grants) {
    ReferenceWorkflow workflow = mock(ReferenceWorkflow.class);
    AuthenticatedUserReader users = mock(AuthenticatedUserReader.class);
    AuthenticatedUser user =
        new AuthenticatedUser(UUID.randomUUID(), "reference-subject", "Reference User");
    when(users.currentUser()).thenReturn(user);
    when(workflow.requireOwnedInvitation(INVITATION, user))
        .thenAnswer(ignored -> store.invitation(INVITATION));
    return new ReferenceProductController(
        workflow,
        grants,
        users,
        mock(BillingEntitlementsClient.class),
        new ReferenceGrantGate(),
        mock(ReferenceOwnerSetup.class),
        "ephemeral-control");
  }

  private ReferenceProductStore.InvitationState invitation(UUID receiptId) {
    return new ReferenceProductStore.InvitationState(
        INVITATION,
        INVITATION,
        "invited@example.test",
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "subject",
        receiptId);
  }
}
