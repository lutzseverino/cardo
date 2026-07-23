package io.github.lutzseverino.cardo.integration.reference;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

final class ReferenceHttpsOrigin implements AutoCloseable {

  private final GenericContainer<?> caddy;
  private final int productPort;
  private final int identityPort;
  private final ServerSocket httpsPortReservation;
  private final int httpsPort;

  private ReferenceHttpsOrigin(
      GenericContainer<?> caddy,
      int productPort,
      int identityPort,
      ServerSocket httpsPortReservation) {
    this.caddy = caddy;
    this.productPort = productPort;
    this.identityPort = identityPort;
    this.httpsPortReservation = httpsPortReservation;
    this.httpsPort = httpsPortReservation.getLocalPort();
  }

  static ReferenceHttpsOrigin configure(int productPort, int identityPort) {
    String configuration =
        "{\n  admin off\n}\n"
            + "https://localhost {\n"
            + "  tls internal\n"
            + "  handle /internal/reference/* {\n"
            + "    respond 404\n"
            + "  }\n"
            + "  handle /api/v1/identity/sessions* {\n"
            + "    reverse_proxy host.testcontainers.internal:"
            + identityPort
            + "\n  }\n"
            + "  handle {\n"
            + "    reverse_proxy host.testcontainers.internal:"
            + productPort
            + "\n  }\n} \n";
    GenericContainer<?> caddy =
        new GenericContainer<>(DockerImageName.parse(System.getProperty("cardo.test.caddy.image")))
            .withCopyToContainer(
                Transferable.of(configuration.getBytes(StandardCharsets.UTF_8)),
                "/etc/caddy/Caddyfile")
            .withExposedPorts(443)
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30)));
    ServerSocket httpsPortReservation = reserveHttpsPort();
    caddy.setPortBindings(List.of(httpsPortReservation.getLocalPort() + ":443"));
    return new ReferenceHttpsOrigin(caddy, productPort, identityPort, httpsPortReservation);
  }

  void start() {
    requireListening("product", productPort);
    requireListening("Identity", identityPort);
    Testcontainers.exposeHostPorts(productPort, identityPort);
    releaseHttpsPort();
    caddy.start();
  }

  URI origin() {
    return URI.create("https://localhost:" + httpsPort);
  }

  HttpClient browser() {
    return browserSession().client();
  }

  Browser browserSession() {
    try {
      byte[] certificate =
          caddy.copyFileFromContainer(
              "/data/caddy/pki/authorities/local/root.crt", input -> input.readAllBytes());
      X509Certificate root =
          (X509Certificate)
              CertificateFactory.getInstance("X.509")
                  .generateCertificate(new ByteArrayInputStream(certificate));
      KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
      trust.load(null, null);
      trust.setCertificateEntry("reference-caddy", root);
      TrustManagerFactory managers =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      managers.init(trust);
      SSLContext tls = SSLContext.getInstance("TLS");
      tls.init(null, managers.getTrustManagers(), new SecureRandom());
      CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
      return new Browser(
          HttpClient.newBuilder()
              .sslContext(tls)
              .cookieHandler(cookies)
              .connectTimeout(Duration.ofSeconds(2))
              .build(),
          cookies);
    } catch (Exception failure) {
      throw new IllegalStateException("Could not trust the disposable Caddy authority.", failure);
    }
  }

  @Override
  public void close() {
    releaseHttpsPort();
    caddy.stop();
  }

  private static ServerSocket reserveHttpsPort() {
    try {
      ServerSocket reservation = new ServerSocket();
      reservation.setReuseAddress(false);
      reservation.bind(new InetSocketAddress(0));
      return reservation;
    } catch (IOException failure) {
      throw new IllegalStateException("Could not reserve the reference HTTPS port.", failure);
    }
  }

  private void releaseHttpsPort() {
    if (httpsPortReservation.isClosed()) {
      return;
    }
    try {
      httpsPortReservation.close();
    } catch (IOException failure) {
      throw new IllegalStateException("Could not release the reference HTTPS port.", failure);
    }
  }

  private void requireListening(String service, int port) {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
    } catch (IOException failure) {
      throw new IllegalStateException(
          "Reference " + service + " must listen before the HTTPS origin starts.", failure);
    }
  }

  record Browser(HttpClient client, CookieManager cookies) {}
}
