package io.github.lutzseverino.cardo.integration.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jwt.JWTParser;
import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakRequestingPartyTokenClient;
import io.github.lutzseverino.cardo.authorization.token.RequestingPartyTokenRequest;
import java.net.HttpCookie;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class ReferenceStackIT {

  private static final Duration JOURNEY_TIMEOUT = Duration.ofSeconds(30);
  private static final String OWNER_EMAIL = "owner@reference.test";
  private static final String INVITED_EMAIL = "invited@reference.test";
  private static final String OTHER_EMAIL = "other@reference.test";
  private static final String OWNER_PASSWORD = "Owner-reference-password-34";
  private static final String INVITED_PASSWORD = "Invited-reference-password-34";
  private static final String OTHER_PASSWORD = "Other-reference-password-34";

  @Test
  void runsThePortableReferenceJourney() throws Exception {
    try (ReferenceStackHarness stack = new ReferenceStackHarness()) {
      stack.start();
      ReferenceHttp internal = ReferenceHttp.plain();
      ReferenceHttp external = new ReferenceHttp(stack.httpsOrigin().browser());
      assertThat(
              external
                  .request(
                      "GET",
                      stack.origin().resolve("/internal/reference/grants/unknown"),
                      null,
                      Map.of())
                  .status())
          .isEqualTo(404);

      Map<String, Object> owner = createUser(internal, stack, OWNER_EMAIL, OWNER_PASSWORD, "Owner");
      String ownerSubject = string(owner, "authorizationSubject");
      UUID ownerId = UUID.fromString(string(owner, "id"));
      UUID ownerReceipt = setupOwner(internal, stack, ownerSubject);
      await(
          "owner grant application",
          response -> "APPLIED".equals(response.get("status")),
          () -> grant(internal, stack, ownerReceipt));

      Browser ownerBrowser = login(stack, OWNER_EMAIL, OWNER_PASSWORD);
      assertProductionCookies(ownerBrowser.lastLogin());
      assertThat(
              ownerBrowser
                  .http()
                  .request(
                      "GET",
                      stack.origin().resolve("/api/v1/identity/sessions/current"),
                      null,
                      Map.of())
                  .status())
          .isEqualTo(200);
      String ownerIdentityToken = ownerBrowser.cookie("__Host-cardo.session");
      String ownerRpt = rpt(stack, ownerIdentityToken);
      assertProductRpt(ownerRpt, ownerId);
      assertThat(
              internal
                  .request(
                      "GET",
                      stack.productInternal(
                          "/api/reference/tenants/" + ReferenceContract.TENANT_ID),
                      null,
                      bearer(ownerRpt))
                  .status())
          .isEqualTo(200);
      assertThat(
              ownerBrowser
                  .http()
                  .request(
                      "GET",
                      stack
                          .origin()
                          .resolve("/api/reference/tenants/" + ReferenceContract.TENANT_ID),
                      null,
                      Map.of())
                  .status())
          .isEqualTo(200);

      ReferenceHttp.Response missingCsrf =
          ownerBrowser
              .http()
              .request(
                  "POST",
                  stack.origin().resolve("/api/reference/invitations"),
                  Map.of("requestId", UUID.randomUUID(), "email", INVITED_EMAIL),
                  Map.of());
      assertThat(missingCsrf.status()).isEqualTo(403);
      assertThat(
              ownerBrowser
                  .http()
                  .request(
                      "POST",
                      stack.origin().resolve("/api/reference/invitations"),
                      Map.of("requestId", UUID.randomUUID(), "email", INVITED_EMAIL),
                      Map.of("X-CSRF-TOKEN", "mismatched-token"))
                  .status())
          .isEqualTo(403);

      UUID requestId = UUID.randomUUID();
      ReferenceHttp.Response created =
          ownerBrowser.request(
              "POST",
              stack.origin().resolve("/api/reference/invitations"),
              Map.of("requestId", requestId, "email", INVITED_EMAIL));
      assertThat(created.status()).isEqualTo(202);
      UUID invitationId = UUID.fromString(string(ownerBrowser.http().object(created), "id"));
      assertThat(invitationId).isEqualTo(requestId);
      stack.record("product-invitation-created-through-stable-client");

      URI invitationLink =
          stack.mailpit().awaitLink(INVITED_EMAIL, "You have been invited", JOURNEY_TIMEOUT);
      assertThat(invitationLink.getPath()).startsWith("/invitations/accept/");
      String invitationToken =
          invitationLink.getPath().substring(invitationLink.getPath().lastIndexOf('/') + 1);
      ReferenceHttp invitationBrowser = new ReferenceHttp(stack.httpsOrigin().browser());
      ReferenceHttp.Response resolved =
          invitationBrowser.request("GET", invitationLink, null, Map.of());
      assertThat(resolved.status()).isEqualTo(200);
      assertThat(resolved.body()).contains(INVITED_EMAIL, ReferenceContract.TENANT_ID.toString());
      ReferenceHttp.Response requested =
          invitationBrowser.request(
              "POST",
              stack
                  .origin()
                  .resolve("/invitations/accept/" + invitationToken + "/credential-setup"),
              null,
              Map.of());
      assertThat(requested.status()).isEqualTo(202);
      await(
          "real Identity operation acknowledgement",
          completion ->
              List.of("AWAITING_IDENTITY", "COMPLETED").contains(completion.get("status")),
          () -> completion(invitationBrowser, stack, invitationToken));
      URI actionLink =
          stack
              .mailpit()
              .awaitLink(
                  INVITED_EMAIL,
                  subject -> !"You have been invited".equals(subject),
                  JOURNEY_TIMEOUT);
      ReferenceKeycloakActions.Result action =
          new ReferenceKeycloakActions()
              .complete(actionLink, INVITED_PASSWORD, "Invited", "Reference");
      assertThat(action.passwordCompleted()).isTrue();
      assertThat(action.profileCompleted()).isTrue();
      assertThat(action.redirect().getPath()).isEqualTo("/invitations/completed");
      Map<String, Object> completed =
          await(
              "credential completion convergence",
              completion -> "COMPLETED".equals(completion.get("status")),
              () -> completion(invitationBrowser, stack, invitationToken));
      UUID invitedUserId = UUID.fromString(string(completed, "invitedUserId"));
      stack.record("mail-and-real-keycloak-actions-completed");

      Browser invitedBrowser = login(stack, INVITED_EMAIL, INVITED_PASSWORD);
      Map<String, Object> invitedPrincipal =
          invitedBrowser.http().object(invitedBrowser.lastLogin());
      @SuppressWarnings("unchecked")
      Map<String, Object> invitedUser = (Map<String, Object>) invitedPrincipal.get("user");
      assertThat(UUID.fromString(string(invitedUser, "id"))).isEqualTo(invitedUserId);
      String invitedSubject = string(invitedUser, "authorizationSubject");
      String invitedIdentityToken = invitedBrowser.cookie("__Host-cardo.session");
      assertThatThrownBy(() -> rpt(stack, invitedIdentityToken))
          .isInstanceOf(RuntimeException.class);
      assertThat(protectedTenantStatus(invitedBrowser, stack)).isEqualTo(401);

      URI accept = stack.origin().resolve("/api/reference/invitations/" + invitationId + "/accept");
      createUser(internal, stack, OTHER_EMAIL, OTHER_PASSWORD, "Other");
      Browser otherBrowser = login(stack, OTHER_EMAIL, OTHER_PASSWORD);
      assertThat(otherBrowser.request("POST", accept, null).status()).isEqualTo(403);
      assertThat(
              otherBrowser
                  .http()
                  .request(
                      "GET",
                      stack.origin().resolve("/api/reference/convergence/" + invitationId),
                      null,
                      Map.of())
                  .status())
          .isEqualTo(403);
      assertThat(
              invitationBrowser
                  .request("POST", accept, null, bearer(invitedIdentityToken))
                  .status())
          .isEqualTo(401);
      assertThat(invitedBrowser.http().request("POST", accept, null, Map.of()).status())
          .isEqualTo(403);
      assertThat(
              invitedBrowser
                  .http()
                  .request("POST", accept, null, Map.of("X-CSRF-TOKEN", "mismatched-token"))
                  .status())
          .isEqualTo(403);
      assertThat(convergence(invitedBrowser, stack, invitationId).get("status"))
          .isEqualTo("NOT_STAGED");

      control(internal, stack, "/internal/reference/grants/pause");
      control(internal, stack, "/internal/reference/fail-after-invite-accept");
      CompletableFuture<ReferenceHttp.Response> first =
          CompletableFuture.supplyAsync(() -> invitedBrowser.request("POST", accept, null));
      CompletableFuture<ReferenceHttp.Response> second =
          CompletableFuture.supplyAsync(() -> invitedBrowser.request("POST", accept, null));
      assertThat(List.of(first.join().status(), second.join().status())).containsOnly(202);
      String inviteToken =
          stack.keycloak().clientToken(ReferenceContract.PRODUCT_OUTBOUND_CLIENT, "cardo-invite");
      Map<String, Object> remoteInvitation =
          await(
              "remote Invite acceptance",
              invitation -> "accepted".equalsIgnoreCase(string(invitation, "status")),
              () ->
                  requireObject(
                      internal.request(
                          "GET",
                          stack.inviteInternal("/api/v1/invitations/" + invitationId),
                          null,
                          bearer(inviteToken)),
                      200,
                      internal));
      assertThat(remoteInvitation)
          .doesNotContainKeys("grant", "grantSnapshot", "receiptId", "convergence");
      assertThat(
              internal
                  .request(
                      "GET",
                      stack.inviteInternal("/api/v1/invitations/" + invitationId + "/convergence"),
                      null,
                      bearer(inviteToken))
                  .status())
          .isEqualTo(404);
      assertThat(convergence(invitedBrowser, stack, invitationId).get("status"))
          .isEqualTo("NOT_STAGED");
      Map<String, Object> pending =
          await(
              "durable PENDING grant receipt",
              convergence -> "PENDING".equals(convergence.get("status")),
              () -> convergence(invitedBrowser, stack, invitationId));
      UUID receiptId = UUID.fromString(string(pending, "receiptId"));
      assertThat(stack.databases().referenceReceiptCount(invitationId)).isOne();
      assertThat(stack.databases().referenceMembershipCount(invitedSubject)).isOne();
      assertThat(protectedTenantStatus(invitedBrowser, stack)).isEqualTo(401);
      control(internal, stack, "/internal/reference/grants/release");
      Map<String, Object> applied =
          await(
              "provider grant publication",
              convergence -> "APPLIED".equals(convergence.get("status")),
              () -> convergence(invitedBrowser, stack, invitationId));
      assertThat(string(applied, "receiptId")).isEqualTo(receiptId.toString());
      assertThat(stack.databases().referenceReceiptCount(invitationId)).isOne();
      assertThat(stack.databases().referenceMembershipCount(invitedSubject)).isOne();
      stack.record("remote-success-local-gap-retried-to-single-applied-receipt");

      String invitedRpt = rpt(stack, invitedIdentityToken);
      assertProductRpt(invitedRpt, invitedUserId);
      assertThat(protectedTenantStatus(invitedBrowser, stack)).isEqualTo(200);
      assertThat(
              internal
                  .request(
                      "GET",
                      stack.productInternal(
                          "/api/reference/tenants/" + ReferenceContract.TENANT_ID),
                      null,
                      bearer(invitedRpt))
                  .status())
          .isEqualTo(200);
      assertThat(
              internal
                  .request(
                      "GET",
                      stack.productInternal(
                          "/api/reference/tenants/" + ReferenceContract.TENANT_ID),
                      null,
                      bearer(invitedIdentityToken))
                  .status())
          .isEqualTo(401);

      stack.databases().seedBillingEntitlement(invitedUserId);
      String billingToken =
          stack.keycloak().clientToken(ReferenceContract.PRODUCT_OUTBOUND_CLIENT, "billing");
      URI billing =
          stack.billingInternal(
              "/api/v1/billing/subjects/"
                  + invitedUserId
                  + "/entitlements/reference-product/access");
      assertThat(internal.request("POST", billing, null, bearer(billingToken)).status())
          .isEqualTo(200);
      assertThat(internal.request("POST", billing, null, bearer(inviteToken)).status())
          .isEqualTo(401);
      assertThat(internal.request("POST", billing, null, bearer(invitedIdentityToken)).status())
          .isEqualTo(401);
      assertThat(internal.request("POST", billing, null, bearer(invitedRpt)).status())
          .isEqualTo(401);
      assertThat(
              invitedBrowser
                  .request(
                      "GET",
                      stack.origin().resolve("/api/reference/billing/" + invitedUserId),
                      null)
                  .status())
          .isEqualTo(200);
      stack.record("billing-boundary-validated");

      stack.keycloak().revokeUserSessions(ownerSubject);
      Thread.sleep(Duration.ofMillis(1200));
      assertThat(
              internal
                  .request(
                      "GET",
                      stack.productInternal(
                          "/api/reference/tenants/" + ReferenceContract.TENANT_ID),
                      null,
                      bearer(ownerRpt))
                  .status())
          .isEqualTo(401);
      stack.record("active-rpt-introspection-failed-closed-after-provider-revocation");

      String accessBeforeRefresh = invitedBrowser.cookie("__Host-cardo.session");
      String refreshBeforeRefresh = invitedBrowser.cookie("__Secure-cardo.refresh");
      ReferenceHttp.Response refreshed =
          invitedBrowser.request(
              "POST", stack.origin().resolve("/api/v1/identity/sessions/current/refresh"), null);
      assertThat(refreshed.status()).isEqualTo(200);
      assertThat(invitedBrowser.cookie("__Host-cardo.session")).isNotEqualTo(accessBeforeRefresh);
      assertThat(invitedBrowser.cookie("__Secure-cardo.refresh"))
          .isNotEqualTo(refreshBeforeRefresh);
      ReferenceHttp.Response logout =
          invitedBrowser.request(
              "DELETE", stack.origin().resolve("/api/v1/identity/sessions/current"), null);
      assertThat(logout.status()).isEqualTo(204);
      assertExpiredCookies(logout);
      assertThat(
              invitedBrowser
                  .http()
                  .request(
                      "GET",
                      stack.origin().resolve("/api/v1/identity/sessions/current"),
                      null,
                      Map.of())
                  .status())
          .isEqualTo(401);
      stack.record("browser-refresh-and-logout-completed");
    }
  }

  private Map<String, Object> createUser(
      ReferenceHttp http, ReferenceStackHarness stack, String email, String password, String name) {
    ReferenceHttp.Response response =
        http.request(
            "POST",
            stack.identityInternal("/api/v1/identity/users"),
            Map.of("email", email, "password", password, "name", name),
            Map.of());
    if (response.status() == 404) {
      throw new IllegalStateException("Identity user route was not reachable through the harness.");
    }
    return requireObject(response, 201, http);
  }

  private UUID setupOwner(
      ReferenceHttp http, ReferenceStackHarness stack, String authorizationSubject) {
    Map<String, Object> receipt =
        requireObject(
            http.request(
                "POST",
                stack.productInternal("/internal/reference/owner"),
                Map.of("subject", authorizationSubject),
                controlHeaders()),
            200,
            http);
    return UUID.fromString(string(receipt, "id"));
  }

  private Map<String, Object> grant(
      ReferenceHttp http, ReferenceStackHarness stack, UUID receiptId) {
    return requireObject(
        http.request(
            "GET",
            stack.productInternal("/internal/reference/grants/" + receiptId),
            null,
            controlHeaders()),
        200,
        http);
  }

  private void control(ReferenceHttp http, ReferenceStackHarness stack, String path) {
    assertThat(http.request("POST", stack.productInternal(path), null, controlHeaders()).status())
        .isBetween(200, 299);
  }

  private Map<String, Object> completion(
      ReferenceHttp browser, ReferenceStackHarness stack, String token) {
    return requireObject(
        browser.request(
            "GET",
            stack.origin().resolve("/invitations/accept/" + token + "/credential-setup"),
            null,
            Map.of()),
        200,
        browser);
  }

  private Map<String, Object> convergence(
      Browser browser, ReferenceStackHarness stack, UUID invitationId) {
    return requireObject(
        browser.request(
            "GET", stack.origin().resolve("/api/reference/convergence/" + invitationId), null),
        200,
        browser.http());
  }

  private Browser login(ReferenceStackHarness stack, String email, String password) {
    ReferenceHttpsOrigin.Browser session = stack.httpsOrigin().browserSession();
    Browser browser = new Browser(new ReferenceHttp(session.client()), session.cookies(), null);
    ReferenceHttp.Response csrf =
        browser
            .http()
            .request(
                "GET", stack.origin().resolve("/api/v1/identity/sessions/csrf"), null, Map.of());
    assertThat(csrf.status()).isEqualTo(204);
    String csrfCookie = setCookie(csrf, "__Host-cardo.csrf");
    assertThat(csrfCookie)
        .contains("Path=/", "Secure", "SameSite=Lax")
        .doesNotContain("Domain=", "HttpOnly");
    URI loginUri = stack.origin().resolve("/api/v1/identity/sessions");
    Map<String, String> credentials = Map.of("email", email, "password", password);
    assertThat(browser.http().request("POST", loginUri, credentials, Map.of()).status())
        .isEqualTo(403);
    assertThat(
            browser
                .http()
                .request("POST", loginUri, credentials, Map.of("X-CSRF-TOKEN", "mismatched-token"))
                .status())
        .isEqualTo(403);
    ReferenceHttp.Response login = browser.request("POST", loginUri, credentials);
    assertThat(login.status())
        .as("identity response: %s", ReferenceDiagnostics.sanitize(login.body()))
        .isEqualTo(201);
    return new Browser(browser.http(), browser.cookies(), login);
  }

  private int protectedTenantStatus(Browser browser, ReferenceStackHarness stack) {
    return browser
        .http()
        .request(
            "GET",
            stack.origin().resolve("/api/reference/tenants/" + ReferenceContract.TENANT_ID),
            null,
            Map.of())
        .status();
  }

  private String rpt(ReferenceStackHarness stack, String identityToken) {
    return new KeycloakRequestingPartyTokenClient(
            stack.keycloakBaseUrl(),
            ReferenceKeycloakMaterializer.REALM,
            ReferenceKeycloakMaterializer.boundedRestClient())
        .authorize(
            RequestingPartyTokenRequest.allPermissions(
                identityToken, ReferenceContract.PRODUCT_CLIENT))
        .token();
  }

  @SuppressWarnings("unchecked")
  private void assertProductRpt(String token, UUID userId) throws Exception {
    var claims = JWTParser.parse(token).getJWTClaimsSet();
    assertThat(claims.getAudience()).containsExactly(ReferenceContract.PRODUCT_CLIENT);
    assertThat(claims.getStringClaim("cardo_user_id")).isEqualTo(userId.toString());
    Map<String, Object> authorization = (Map<String, Object>) claims.getClaim("authorization");
    Collection<Map<String, Object>> permissions =
        (Collection<Map<String, Object>>) authorization.get("permissions");
    assertThat(permissions)
        .anySatisfy(
            permission -> {
              assertThat(permission.get("rsname")).isEqualTo(ReferenceContract.TENANT_RESOURCE);
              assertThat((Collection<String>) permission.get("scopes"))
                  .contains(ReferenceContract.TENANT_ACTION);
            });
  }

  private void assertProductionCookies(ReferenceHttp.Response login) {
    String access = setCookie(login, "__Host-cardo.session");
    assertThat(access)
        .contains("Path=/", "Secure", "HttpOnly", "SameSite=Lax")
        .doesNotContain("Domain=");
    String refresh = setCookie(login, "__Secure-cardo.refresh");
    assertThat(refresh)
        .contains("Path=/api/v1/identity/sessions/current", "Secure", "HttpOnly", "SameSite=Lax")
        .doesNotContain("Domain=");
  }

  private void assertExpiredCookies(ReferenceHttp.Response logout) {
    List<String> cookies = logout.headers().getOrDefault("set-cookie", List.of());
    assertExpired(cookies, "__Host-cardo.session", "/", true);
    assertExpired(cookies, "__Secure-cardo.refresh", "/api/v1/identity/sessions/current", true);
    assertExpired(cookies, "__Host-cardo.csrf", "/", false);
  }

  private void assertExpired(List<String> cookies, String name, String path, boolean httpOnly) {
    String cookie = setCookie(cookies, name);
    assertThat(cookie).contains("Max-Age=0", "Path=" + path, "Secure", "SameSite=Lax");
    if (httpOnly) {
      assertThat(cookie).contains("HttpOnly");
    } else {
      assertThat(cookie).doesNotContain("HttpOnly");
    }
  }

  private String setCookie(ReferenceHttp.Response response, String name) {
    return setCookie(response.headers().getOrDefault("set-cookie", List.of()), name);
  }

  private String setCookie(List<String> cookies, String name) {
    return cookies.stream()
        .filter(value -> value.startsWith(name + "="))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing Set-Cookie for " + name));
  }

  private static Map<String, String> bearer(String token) {
    return Map.of("Authorization", "Bearer " + token);
  }

  private static Map<String, String> controlHeaders() {
    return Map.of("X-Reference-Control", ReferenceStackHarness.CONTROL_SECRET);
  }

  private static Map<String, Object> requireObject(
      ReferenceHttp.Response response, int status, ReferenceHttp http) {
    if (response.status() != status) {
      throw new IllegalStateException(
          "Expected HTTP "
              + status
              + " but received "
              + response.status()
              + ": "
              + response.body());
    }
    return http.object(response);
  }

  private static String string(Map<String, Object> object, String name) {
    return String.valueOf(object.get(name));
  }

  private static <T> T await(
      String description, Predicate<T> complete, java.util.function.Supplier<T> read) {
    Instant deadline = Instant.now().plus(JOURNEY_TIMEOUT);
    T value = null;
    while (Instant.now().isBefore(deadline)) {
      value = read.get();
      if (complete.test(value)) {
        return value;
      }
      try {
        Thread.sleep(100);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(
            "Interrupted while awaiting " + description + ".", interrupted);
      }
    }
    throw new IllegalStateException("Timed out awaiting " + description + ": " + value);
  }

  private record Browser(
      ReferenceHttp http, java.net.CookieManager cookies, ReferenceHttp.Response lastLogin) {

    ReferenceHttp.Response request(String method, URI uri, Object body) {
      return http.request(method, uri, body, Map.of("X-CSRF-TOKEN", cookie("__Host-cardo.csrf")));
    }

    String cookie(String name) {
      return cookies.getCookieStore().getCookies().stream()
          .filter(cookie -> name.equals(cookie.getName()))
          .map(HttpCookie::getValue)
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("Browser cookie was missing: " + name));
    }
  }
}
