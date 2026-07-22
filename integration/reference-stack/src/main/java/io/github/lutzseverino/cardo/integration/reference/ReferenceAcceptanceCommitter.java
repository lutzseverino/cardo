package io.github.lutzseverino.cardo.integration.reference;

import io.github.lutzseverino.cardo.authorization.grant.GrantPlan;
import io.github.lutzseverino.cardo.authorization.grant.GrantReceipt;
import io.github.lutzseverino.cardo.authorization.grant.Grants;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ReferenceAcceptanceCommitter {

  private final ReferenceProductStore store;
  private final Grants grants;

  ReferenceAcceptanceCommitter(ReferenceProductStore store, Grants grants) {
    this.store = store;
    this.grants = grants;
  }

  @Transactional
  void complete(ReferenceProductStore.ReferenceCommand command) {
    ReferenceProductStore.InvitationState state = store.lockInvitation(command.invitationId());
    if (state.receiptId() != null) {
      store.completeCommand(command.id());
      return;
    }
    GrantReceipt receipt =
        grants.stage(
            GrantPlan.builder()
                .grantActions(
                    command.acceptedSubject(),
                    ReferenceContract.tenantResource(),
                    List.of(ReferenceContract.TENANT_ACTION))
                .build());
    store.completeAcceptance(command.invitationId(), command.acceptedSubject(), receipt.id());
    store.completeCommand(command.id());
  }
}
