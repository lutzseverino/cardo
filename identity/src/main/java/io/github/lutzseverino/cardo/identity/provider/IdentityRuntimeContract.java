package io.github.lutzseverino.cardo.identity.provider;

public interface IdentityRuntimeContract {

  void validate();

  void repairLegacyStartupDefinitions();
}
