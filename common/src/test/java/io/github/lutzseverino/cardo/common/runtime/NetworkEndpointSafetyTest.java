package io.github.lutzseverino.cardo.common.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NetworkEndpointSafetyTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "localhost",
        "localhost.",
        "api.localhost.",
        "127.0.0.1",
        "127.255.255.254",
        "127.1",
        "127.0.1",
        "127.16777215",
        "2130706433",
        "0.0.0.0",
        "0.0",
        "0.0.0",
        "0",
        "[::1]",
        "::1",
        "[::]",
        "::7f00:1",
        "::ffff:7f00:1",
        "::ffff:127.0.0.1",
        "::ffff:0.0.0.0"
      })
  void identifiesCanonicalLocalAndUnspecifiedHosts(String host) {
    assertThat(NetworkEndpointSafety.isLocalOrUnspecified(host)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "id.example.com",
        "1",
        "126.16777215",
        "128.1",
        "10.0.0.5",
        "192.168.1.5",
        "2001:db8::1",
        "::ffff:192.168.1.5"
      })
  void acceptsRemoteHosts(String host) {
    assertThat(NetworkEndpointSafety.isLocalOrUnspecified(host)).isFalse();
  }
}
