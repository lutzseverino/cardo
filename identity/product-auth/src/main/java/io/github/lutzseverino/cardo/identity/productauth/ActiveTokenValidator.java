package io.github.lutzseverino.cardo.identity.productauth;

public interface ActiveTokenValidator {
  boolean isActive(String token);
}
