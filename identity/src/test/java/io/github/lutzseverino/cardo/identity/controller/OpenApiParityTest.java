package io.github.lutzseverino.cardo.identity.controller;

import static io.github.lutzseverino.cardo.openapi.testing.OpenApiParityAssertions.assertMatches;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.api.model.UserResponse;
import io.github.lutzseverino.cardo.identity.api.model.UserStatus;
import io.github.lutzseverino.cardo.identity.config.OpenApiConfiguration;
import io.github.lutzseverino.cardo.identity.mapper.AuthenticationTransportMapper;
import io.github.lutzseverino.cardo.identity.mapper.IdentityOperationTransportMapper;
import io.github.lutzseverino.cardo.identity.mapper.UserTransportMapper;
import io.github.lutzseverino.cardo.identity.model.AuthenticateInput;
import io.github.lutzseverino.cardo.identity.model.CreateUserInput;
import io.github.lutzseverino.cardo.identity.model.UpdateUserInput;
import io.github.lutzseverino.cardo.identity.model.UserResult;
import io.github.lutzseverino.cardo.identity.patch.UserPatchAdapter;
import io.github.lutzseverino.cardo.identity.reader.CurrentJwtReader;
import io.github.lutzseverino.cardo.identity.service.AuthenticationService;
import io.github.lutzseverino.cardo.identity.service.IdentityOperationService;
import io.github.lutzseverino.cardo.identity.service.UserService;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@WebMvcTest(
    controllers = {IdentityStatusController.class, SessionController.class, UserController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import({OpenApiConfiguration.class, UserPatchAdapter.class})
@ImportAutoConfiguration({
  SpringDocConfiguration.class,
  SpringDocConfigProperties.class,
  SpringDocWebMvcConfiguration.class
})
@TestPropertySource(
    properties = {
      "cardo.api.base-path=/api/v1",
      "springdoc.paths-to-match=/api/v1/**",
      "springdoc.api-docs.path=/openapi.json",
      "springdoc.api-docs.version=OPENAPI_3_0",
      "spring.autoconfigure.exclude=org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration"
    })
class OpenApiParityTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @MockitoBean private AuthenticationService authentication;
  @MockitoBean private AuthenticationTransportMapper authenticationMapper;
  @MockitoBean private CurrentJwtReader currentJwt;
  @MockitoBean private IdentityOperationService operations;
  @MockitoBean private IdentityOperationTransportMapper operationMapper;
  @MockitoBean private SessionCookiePolicy cookies;
  @MockitoBean private UserService users;
  @MockitoBean private UserTransportMapper userMapper;

  @Test
  void publicDocumentMatchesCanonicalMetadataAndTransportSurface() throws Exception {
    String runtime =
        mvc.perform(get("/openapi.json"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertMatches("openapi/identity.yaml", runtime, "/api/v1");
  }

  @Test
  void parityRejectsNullableDriftInPatchRequestBodies() throws Exception {
    String runtime =
        mvc.perform(get("/openapi.json"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode mutated = objectMapper.readTree(runtime);
    ((ObjectNode) mutated.at("/components/schemas/UpdateUserRequest/properties/name"))
        .put("nullable", true);

    assertThatThrownBy(
            () ->
                assertMatches(
                    "openapi/identity.yaml", objectMapper.writeValueAsString(mutated), "/api/v1"))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void exposesDocumentedProviderFailuresWhenCreatingUsers() throws Exception {
    assertCreateUserFailure(502, "identity_provider_error");
    assertCreateUserFailure(503, "identity_provider_unavailable");
  }

  @Test
  void exposesNullableUserFieldsWithTheirStableWireTypes() throws Exception {
    OffsetDateTime now = OffsetDateTime.parse("2026-07-22T12:00:00Z");
    when(userMapper.toResponse(nullable(UserResult.class)))
        .thenReturn(
            new UserResponse()
                .id(UUID.fromString("d27b3737-ab1c-40f4-88cc-0688361bd28d"))
                .authorizationSubject("subject-1")
                .email("invited@example.test")
                .name(null)
                .avatarUrl(URI.create("https://example.test/avatar.png"))
                .status(UserStatus.INVITED)
                .emailVerified(false)
                .createdAt(now)
                .updatedAt(now));

    mvc.perform(
            post("/api/v1/identity/users/provisional")
                .contentType("application/json")
                .content("{\"email\":\"invited@example.test\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").isEmpty())
        .andExpect(jsonPath("$.avatarUrl").value("https://example.test/avatar.png"))
        .andExpect(jsonPath("$.name.present").doesNotExist())
        .andExpect(jsonPath("$.avatarUrl.present").doesNotExist());
  }

  @Test
  void exposesPresentNullAndOmitsUndefinedUserFields() throws Exception {
    when(userMapper.toResponse(nullable(UserResult.class))).thenReturn(response().name(null));

    mvc.perform(
            post("/api/v1/identity/users/provisional")
                .contentType("application/json")
                .content("{\"email\":\"invited@example.test\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").isEmpty())
        .andExpect(jsonPath("$.avatarUrl").doesNotExist());
  }

  @Test
  void preservesMissingExplicitNullAndTypedUriThroughThePatchAdapter() throws Exception {
    UUID userId = UUID.fromString("d27b3737-ab1c-40f4-88cc-0688361bd28d");
    when(userMapper.toResponse(nullable(UserResult.class))).thenReturn(response().name(null));

    mvc.perform(
            patch("/api/v1/identity/users/{userId}", userId)
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isOk());
    mvc.perform(
            patch("/api/v1/identity/users/{userId}", userId)
                .contentType("application/json")
                .content("{\"avatarUrl\":null}"))
        .andExpect(status().isOk());
    mvc.perform(
            patch("/api/v1/identity/users/{userId}", userId)
                .contentType("application/json")
                .content("{\"avatarUrl\":\"https://example.test/avatar.png\"}"))
        .andExpect(status().isOk());

    ArgumentCaptor<UpdateUserInput> inputs = ArgumentCaptor.forClass(UpdateUserInput.class);
    verify(users, times(3)).update(eq(userId), inputs.capture());
    assertThat(inputs.getAllValues().get(0).avatarUrl().present()).isFalse();
    assertThat(inputs.getAllValues().get(1).avatarUrl().present()).isTrue();
    assertThat(inputs.getAllValues().get(1).avatarUrl().value()).isNull();
    assertThat(inputs.getAllValues().get(2).avatarUrl().value())
        .isEqualTo("https://example.test/avatar.png");
  }

  @Test
  void exposesApplicationOwnedForbiddenFailuresAsJson() throws Exception {
    doThrow(ApiException.forbidden("user_invited", "Invited users cannot sign in."))
        .when(authentication)
        .authenticate(nullable(AuthenticateInput.class));

    mvc.perform(
            post("/api/v1/identity/sessions")
                .contentType("application/json")
                .content(
                    """
                    {"email":"user@example.com","password":"password-1"}
                    """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("user_invited"));
  }

  private void assertCreateUserFailure(int status, String code) throws Exception {
    doThrow(ApiException.of(status, code, "Provider failure."))
        .when(users)
        .create(nullable(CreateUserInput.class));

    mvc.perform(
            post("/api/v1/identity/users")
                .contentType("application/json")
                .content(
                    """
                    {"email":"user@example.com","password":"password-1","name":"User"}
                    """))
        .andExpect(status().is(status))
        .andExpect(jsonPath("$.error.code").value(code));
  }

  private static UserResponse response() {
    OffsetDateTime now = OffsetDateTime.parse("2026-07-22T12:00:00Z");
    return new UserResponse()
        .id(UUID.fromString("d27b3737-ab1c-40f4-88cc-0688361bd28d"))
        .authorizationSubject("subject-1")
        .email("invited@example.test")
        .status(UserStatus.INVITED)
        .emailVerified(false)
        .createdAt(now)
        .updatedAt(now);
  }
}
