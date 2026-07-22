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

  @Test
  void redactsTheEntireKeycloakActionTokenLink() {
    String input =
        "Follow https://identity.test/realms/cardo/login-actions/action-token?key=keycloak-secret&client_id=identity&tab_id=tab-secret now.";

    assertThat(ReferenceDiagnostics.sanitize(input))
        .isEqualTo("Follow [redacted-action-link] now.");
  }

  @Test
  void redactsTheEntireInvitationAcceptanceLink() {
    String input =
        "Open https://app.test/invitations/accept/invitation-secret?locale=en to accept.";

    assertThat(ReferenceDiagnostics.sanitize(input))
        .isEqualTo("Open [redacted-action-link] to accept.");
  }
}
