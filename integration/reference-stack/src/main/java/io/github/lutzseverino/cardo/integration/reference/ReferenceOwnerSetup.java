package io.github.lutzseverino.cardo.integration.reference;

import io.github.lutzseverino.cardo.authorization.grant.GrantPlan;
import io.github.lutzseverino.cardo.authorization.grant.GrantReceipt;
import io.github.lutzseverino.cardo.authorization.grant.Grants;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ReferenceOwnerSetup {

  private final ReferenceProductStore store;
  private final Grants grants;

  ReferenceOwnerSetup(ReferenceProductStore store, Grants grants) {
    this.store = store;
    this.grants = grants;
  }

  @Transactional
  GrantReceipt create(String subject) {
    store.createOwnerMembership(subject);
    return grants.stage(
        GrantPlan.builder().grantFullAccess(subject, ReferenceContract.tenantResource()).build());
  }
}
