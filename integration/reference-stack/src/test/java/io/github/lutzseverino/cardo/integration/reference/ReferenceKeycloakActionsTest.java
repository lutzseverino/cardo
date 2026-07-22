package io.github.lutzseverino.cardo.integration.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ReferenceKeycloakActionsTest {

  @Test
  void parsesPinnedProviderFormsAndPreservesHiddenExecutionState() {
    ReferenceKeycloakActions.Form form =
        ReferenceKeycloakActions.form(
            """
            <form action="/realms/reference/login-actions/required-action?code=secret&amp;execution=one"
                  id="kc-passwd-update-form" method="post">
              <input type="hidden" name="execution" value="UPDATE_PASSWORD">
              <input type="password" name="password-new">
              <input disabled name="ignored" value="nope">
            </form>
            """,
            URI.create("http://provider.test/current"));

    assertThat(form.id()).isEqualTo("kc-passwd-update-form");
    assertThat(form.action())
        .isEqualTo(
            URI.create(
                "http://provider.test/realms/reference/login-actions/required-action?code=secret&execution=one"));
    assertThat(form.values())
        .containsEntry("execution", "UPDATE_PASSWORD")
        .doesNotContainKey("ignored");
  }

  @Test
  void reportsFormlessProviderResponseWithoutItsTokenQuery() throws IOException {
    HttpServer provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    provider.createContext(
        "/realms/reference/login-actions/action-token",
        exchange -> {
          byte[] body =
              """
              <html><div id="kc-error-message"><p>Action expired. Start again.</p></div></html>
              """
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(400, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    provider.start();
    try {
      URI action =
          URI.create(
              "http://127.0.0.1:"
                  + provider.getAddress().getPort()
                  + "/realms/reference/login-actions/action-token?key=secret-token");

      assertThatThrownBy(
              () -> new ReferenceKeycloakActions().complete(action, "password", "First", "Last"))
          .hasMessageContaining(
              "status=400, path=/realms/reference/login-actions/action-token, error=Action expired. Start again.")
          .hasMessageNotContaining("secret-token");
    } finally {
      provider.stop(0);
    }
  }
}
