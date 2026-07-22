package io.github.lutzseverino.cardo.integration.reference;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class ReferenceMailpitTest {

  @Test
  void extractsPlainAndHtmlEscapedActionLinksWithoutLoggingTheirSecrets() {
    assertThat(
            ReferenceMailpit.firstLink(
                "Open https://reference.test/invitations/accept/secret-token."))
        .contains(URI.create("https://reference.test/invitations/accept/secret-token"));
    assertThat(
            ReferenceMailpit.firstLink(
                "<a href=\"https://identity.test/action?code=secret&amp;execution=one\">open</a>"))
        .contains(URI.create("https://identity.test/action?code=secret&execution=one"));
  }
}
