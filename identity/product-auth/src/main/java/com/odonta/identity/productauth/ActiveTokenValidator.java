package com.odonta.identity.productauth;

public interface ActiveTokenValidator {
  boolean isActive(String token);
}
