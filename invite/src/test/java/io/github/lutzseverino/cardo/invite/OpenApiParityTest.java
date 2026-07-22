package io.github.lutzseverino.cardo.invite;

import static io.github.lutzseverino.cardo.openapi.testing.OpenApiParityAssertions.assertMatches;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.lutzseverino.cardo.common.api.ApiException;
import io.github.lutzseverino.cardo.invite.config.OpenApiConfiguration;
import io.github.lutzseverino.cardo.invite.controller.InvitationController;
import io.github.lutzseverino.cardo.invite.controller.InvitationGrantConvergenceController;
import io.github.lutzseverino.cardo.invite.controller.InviteStatusController;
import io.github.lutzseverino.cardo.invite.mapper.InvitationGrantConvergenceTransportMapper;
import io.github.lutzseverino.cardo.invite.mapper.InvitationTransportMapper;
import io.github.lutzseverino.cardo.invite.model.CreateInvitationInput;
import io.github.lutzseverino.cardo.invite.reader.ProductCallerReader;
import io.github.lutzseverino.cardo.invite.service.InvitationCompletionService;
import io.github.lutzseverino.cardo.invite.service.InvitationGrantConvergenceService;
import io.github.lutzseverino.cardo.invite.service.InvitationService;
import io.github.lutzseverino.cardo.invite.workflow.AcceptInvitationWorkflow;
import io.github.lutzseverino.cardo.invite.workflow.CreateInvitationWorkflow;
import io.github.lutzseverino.cardo.invite.workflow.RevokeInvitationWorkflow;
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

@WebMvcTest(
    controllers = {
      InvitationController.class,
      InvitationGrantConvergenceController.class,
      InviteStatusController.class
    })
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
  @MockitoBean private AcceptInvitationWorkflow acceptance;
  @MockitoBean private CreateInvitationWorkflow creation;
  @MockitoBean private InvitationCompletionService completions;
  @MockitoBean private InvitationGrantConvergenceService convergence;
  @MockitoBean private InvitationGrantConvergenceTransportMapper convergenceMapper;
  @MockitoBean private InvitationService invitations;
  @MockitoBean private InvitationTransportMapper mapper;
  @MockitoBean private ProductCallerReader callers;
  @MockitoBean private RevokeInvitationWorkflow revocation;

  @Test
  void publicDocumentMatchesCanonicalMetadataAndTransportSurface() throws Exception {
    String runtime =
        mvc.perform(get("/openapi.json"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertMatches("openapi/invite.yaml", runtime, "/api/v1");
  }

  @Test
  void exposesDownstreamIdentityUnavailabilityAsDocumented() throws Exception {
    doThrow(ApiException.of(503, "identity_provider_unavailable", "Identity unavailable."))
        .when(creation)
        .create(nullable(String.class), nullable(CreateInvitationInput.class));

    mvc.perform(
            post("/api/v1/invitations")
                .contentType("application/json")
                .content(
                    """
                    {"requestId":"11111111-1111-1111-1111-111111111111",
                     "tenantId":"22222222-2222-2222-2222-222222222222",
                     "tenantResourceType":"clinic:clinic",
                     "email":"user@example.com","accessProfile":"clinic:employee",
                     "grants":[{"resourceType":"clinic:clinic","action":"clinic:read"}],
                     "invitedBy":"33333333-3333-3333-3333-333333333333",
                     "acceptUrlBase":"https://clinic.example/invitations"}
                    """))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error.code").value("identity_provider_unavailable"));
  }
}
