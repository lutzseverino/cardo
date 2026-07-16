package io.github.lutzseverino.cardo.authorization.grant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class EffectiveGrantAuthorityReaderTest {

  @Test
  void readsResourceGrantsFromAuthorities() {
    EffectiveGrantAuthorityReader reader = new EffectiveGrantAuthorityReader();

    List<EffectiveGrant> grants =
        reader.read(
            List.of(
                new SimpleGrantedAuthority("clinic:clinic:123:read"),
                new SimpleGrantedAuthority("clinic:clinic:123:write"),
                new SimpleGrantedAuthority("identity:user:read")));

    assertThat(grants)
        .containsExactly(
            new EffectiveGrant(
                new GrantedResource("clinic:clinic", "123"), List.of("read", "write")));
  }
}
