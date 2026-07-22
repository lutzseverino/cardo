package io.github.lutzseverino.cardo.identity.controller;

import static io.github.lutzseverino.cardo.openapi.testing.OpenApiParityAssertions.assertMatches;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.identity.config.OpenApiConfiguration;
import io.github.lutzseverino.cardo.identity.mapper.AuthenticationTransportMapper;
import io.github.lutzseverino.cardo.identity.mapper.IdentityOperationTransportMapper;
import io.github.lutzseverino.cardo.identity.mapper.UserTransportMapper;
import io.github.lutzseverino.cardo.identity.model.AuthenticateInput;
import io.github.lutzseverino.cardo.identity.model.CreateUserInput;
import io.github.lutzseverino.cardo.identity.patch.UserPatchAdapter;
import io.github.lutzseverino.cardo.identity.reader.CurrentJwtReader;
import io.github.lutzseverino.cardo.identity.service.AuthenticationService;
import io.github.lutzseverino.cardo.identity.service.IdentityOperationService;
import io.github.lutzseverino.cardo.identity.service.UserService;
import org.junit.jupiter.api.Test;
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
@Import(OpenApiConfiguration.class)
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
  @MockitoBean private UserPatchAdapter patchAdapter;
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
}
