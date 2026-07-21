package io.github.lutzseverino.cardo.identity.workflow;

import io.github.lutzseverino.cardo.identity.provider.IdentityProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InitializeIdentityRuntimeWorkflow {

  private static final Logger logger =
      LoggerFactory.getLogger(InitializeIdentityRuntimeWorkflow.class);

  private final IdentityProvider identityProvider;

  public void initialize(List<String> userIdClaimClientIds) {
    identityProvider.ensureUserIdClaimMapped(userIdClaimClientIds);
    logger.info(
        "identity_provider_repair_ready backfill=provider_bindings_and_enabled_state "
            + "historical_unmarked_orphans=manual_audit_required");
  }
}
