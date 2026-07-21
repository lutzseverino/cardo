package io.github.lutzseverino.cardo.identity.model;

public enum IdentityProviderMutationTerminalReason {
  CREDENTIAL_RESUBMISSION_REQUIRED,
  PROVIDER_REJECTED,
  RETRY_EXHAUSTED,
  LOCAL_STATE_CONFLICT
}
