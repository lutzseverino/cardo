package io.github.lutzseverino.cardo.integration.reference;

import java.io.ByteArrayInputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

final class ReferenceHttpsOrigin implements AutoCloseable {

  private final GenericContainer<?> caddy;

  private ReferenceHttpsOrigin(GenericContainer<?> caddy) {
    this.caddy = caddy;
  }

  static ReferenceHttpsOrigin start(int productPort, int identityPort) {
    Testcontainers.exposeHostPorts(productPort, identityPort);
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
    caddy.start();
    return new ReferenceHttpsOrigin(caddy);
  }

  URI origin() {
    return URI.create("https://localhost:" + caddy.getMappedPort(443));
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
    caddy.stop();
  }

  record Browser(HttpClient client, CookieManager cookies) {}
}
