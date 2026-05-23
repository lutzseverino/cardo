package com.odonta.authorization.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class ResourcePermissionEvaluatorTest {

  private final ResourcePermissionEvaluator evaluator = new ResourcePermissionEvaluator();

  @Test
  void allowsSpecificResourceAction() {
    UUID id = UUID.fromString("7f7af229-729c-45ec-8f45-c5ca6d8b1967");
    UsernamePasswordAuthenticationToken authentication =
        authentication("identity:user:7f7af229-729c-45ec-8f45-c5ca6d8b1967:read");

    assertThat(evaluator.hasPermission(authentication, id, "identity:user", "read")).isTrue();
  }

  @Test
  void allowsWildcardResourceAction() {
    UUID id = UUID.fromString("7f7af229-729c-45ec-8f45-c5ca6d8b1967");
    UsernamePasswordAuthenticationToken authentication = authentication("identity:user:*:read");

    assertThat(evaluator.hasPermission(authentication, id, "identity:user", "read")).isTrue();
  }

  @Test
  void deniesDifferentAction() {
    UUID id = UUID.fromString("7f7af229-729c-45ec-8f45-c5ca6d8b1967");
    UsernamePasswordAuthenticationToken authentication =
        authentication("identity:user:7f7af229-729c-45ec-8f45-c5ca6d8b1967:read");

    assertThat(evaluator.hasPermission(authentication, id, "identity:user", "write")).isFalse();
  }

  @Test
  void wildcardTargetRequiresWildcardAuthority() {
    UsernamePasswordAuthenticationToken authentication = authentication("identity:user:*:read");

    assertThat(evaluator.hasPermission(authentication, "*", "identity:user", "read")).isTrue();
  }

  private UsernamePasswordAuthenticationToken authentication(String authority) {
    return new UsernamePasswordAuthenticationToken(
        "subject", "credentials", List.of(new SimpleGrantedAuthority(authority)));
  }
}
