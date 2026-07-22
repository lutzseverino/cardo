package io.github.lutzseverino.cardo.integration.reference;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ReferenceHttpsOriginTest {

  @Test
  void routesOnlyIdentitySessionsAndTheProductThroughOneTrustedHttpsOrigin() throws Exception {
    HttpServer product = server("product");
    HttpServer identity = server("identity");
    try (ReferenceHttpsOrigin origin =
        ReferenceHttpsOrigin.start(
            product.getAddress().getPort(), identity.getAddress().getPort())) {
      assertThat(get(origin, "/api/reference/tenants/one")).isEqualTo("product");
      assertThat(get(origin, "/api/v1/identity/sessions/current")).isEqualTo("identity");
      assertThat(get(origin, "/api/v1/identity/users")).isEqualTo("product");
      assertThat(status(origin, "/internal/reference/grants/pause")).isEqualTo(404);
    } finally {
      product.stop(0);
      identity.stop(0);
    }
  }

  private HttpServer server(String response) throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          byte[] body = response.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    return server;
  }

  private String get(ReferenceHttpsOrigin origin, String path) throws Exception {
    return origin
        .browser()
        .send(
            HttpRequest.newBuilder(URI.create(origin.origin() + path)).GET().build(),
            HttpResponse.BodyHandlers.ofString())
        .body();
  }

  private int status(ReferenceHttpsOrigin origin, String path) throws Exception {
    return origin
        .browser()
        .send(
            HttpRequest.newBuilder(URI.create(origin.origin() + path)).GET().build(),
            HttpResponse.BodyHandlers.discarding())
        .statusCode();
  }
}
