package io.github.lutzseverino.cardo.integration.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
  void followsPinnedProviderActionConfirmation() {
    URI page =
        URI.create("http://provider.test/realms/reference/login-actions/action-token?key=initial");

    URI continuation =
        ReferenceKeycloakActions.continuation(
            """
            <div id="kc-info-message">
              <p class="instruction">Confirm execution of actions: <b>Update password</b></p>
              <p><a href="/realms/reference/login-actions/action-token?key=confirmed&amp;client_id=browser">
                Proceed
              </a></p>
            </div>
            """,
            page);

    assertThat(continuation)
        .isEqualTo(
            URI.create(
                "http://provider.test/realms/reference/login-actions/action-token?key=confirmed&client_id=browser"));
  }

  @Test
  void doesNotTreatAnUnrelatedInfoLinkAsActionConfirmation() {
    assertThat(
            ReferenceKeycloakActions.continuation(
                """
                <div id="kc-info-message">
                  <p><a href="https://attacker.test/realms/reference/login-actions/action-token?key=stolen">
                    Proceed
                  </a></p>
                </div>
                """,
                URI.create(
                    "http://provider.test/realms/reference/login-actions/action-token?key=initial")))
        .isNull();
  }

  @Test
  void completesConfirmationFormsAndTerminalInfoLink() throws IOException {
    HttpServer provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    URI origin = URI.create("http://localhost:" + provider.getAddress().getPort());
    URI expectedRedirect = origin.resolve("/invitations/completed");
    List<String> requests = new ArrayList<>();
    List<String> cookies = new ArrayList<>();
    provider.createContext(
        "/",
        exchange -> {
          requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
          cookies.add(exchange.getRequestHeaders().getFirst("Cookie"));
          switch (requests.size()) {
            case 1 -> {
              exchange
                  .getResponseHeaders()
                  .add("Set-Cookie", "AUTH_SESSION_ID=session; Version=1; Path=/realms/reference/");
              exchange
                  .getResponseHeaders()
                  .add(
                      "Set-Cookie",
                      "KC_AUTH_SESSION_HASH=hash; Version=1; Path=/realms/reference/");
              respond(
                  exchange,
                  200,
                  """
                    <div id="kc-info-message">
                      <p><a href="/realms/reference/login-actions/action-token?key=confirmed">Proceed</a></p>
                    </div>
                    """);
            }
            case 2 -> redirect(exchange, "/realms/reference/login-actions/password");
            case 3 ->
                respond(
                    exchange,
                    200,
                    """
                    <form id="kc-passwd-update-form" action="/realms/reference/login-actions/password">
                      <input name="password-new"><input name="password-confirm">
                    </form>
                    """);
            case 4 -> redirect(exchange, "/realms/reference/login-actions/profile");
            case 5 ->
                respond(
                    exchange,
                    200,
                    """
                    <form id="kc-update-profile-form" action="/realms/reference/login-actions/profile">
                      <input name="firstName"><input name="lastName">
                    </form>
                    """);
            case 6 ->
                respond(
                    exchange,
                    200,
                    """
                    <div id="kc-info-message">
                      <p><a href="%s">Back to application</a></p>
                    </div>
                    """
                        .formatted(expectedRedirect));
            default -> respond(exchange, 500, "Unexpected request");
          }
        });
    provider.start();
    try {
      ReferenceKeycloakActions.Result result =
          new ReferenceKeycloakActions()
              .complete(
                  origin.resolve("/realms/reference/login-actions/action-token?key=initial"),
                  "password",
                  "First",
                  "Last",
                  expectedRedirect);

      assertThat(result)
          .isEqualTo(new ReferenceKeycloakActions.Result(true, true, expectedRedirect));
      assertThat(requests)
          .containsExactly(
              "GET /realms/reference/login-actions/action-token?key=initial",
              "GET /realms/reference/login-actions/action-token?key=confirmed",
              "GET /realms/reference/login-actions/password",
              "POST /realms/reference/login-actions/password",
              "GET /realms/reference/login-actions/profile",
              "POST /realms/reference/login-actions/profile");
      assertThat(cookies.subList(1, cookies.size()))
          .containsOnly("AUTH_SESSION_ID=session; KC_AUTH_SESSION_HASH=hash");
    } finally {
      provider.stop(0);
    }
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
              () ->
                  new ReferenceKeycloakActions()
                      .complete(
                          action,
                          "password",
                          "First",
                          "Last",
                          URI.create("http://application.test/completed")))
          .hasMessageContaining(
              "status=400, path=/realms/reference/login-actions/action-token, error=Action expired. Start again.")
          .hasMessageNotContaining("secret-token");
    } finally {
      provider.stop(0);
    }
  }

  private static void redirect(com.sun.net.httpserver.HttpExchange exchange, String location)
      throws IOException {
    exchange.getResponseHeaders().add("Location", location);
    exchange.sendResponseHeaders(302, -1);
    exchange.close();
  }

  private static void respond(
      com.sun.net.httpserver.HttpExchange exchange, int status, String content) throws IOException {
    byte[] body = content.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }
}
