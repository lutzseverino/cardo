package io.github.lutzseverino.cardo.integration.reference;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReferenceDiagnosticsTest {

  @Test
  void redactsTokensSecretsCookiesAndActionLinks() {
    String input =
        "Authorization=eyJabc.def.ghi password=hunter2 Set-Cookie=session-value "
            + "https://provider.test/action?code=secret";

    String sanitized = ReferenceDiagnostics.sanitize(input);

    assertThat(sanitized)
        .doesNotContain("eyJabc.def.ghi", "hunter2", "session-value", "code=secret")
        .contains("[redacted]", "[redacted-action-link]");
  }
}
