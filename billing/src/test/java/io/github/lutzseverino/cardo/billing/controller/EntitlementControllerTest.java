package io.github.lutzseverino.cardo.billing.controller;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.lutzseverino.cardo.authorization.spring.AuthenticatedUserReader;
import io.github.lutzseverino.cardo.billing.mapper.EntitlementTransportMapper;
import io.github.lutzseverino.cardo.billing.service.EntitlementService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EntitlementController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(
    properties = {
      "cardo.api.base-path=/api/v1",
      "spring.autoconfigure.exclude=org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration"
    })
class EntitlementControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private AuthenticatedUserReader users;
  @MockitoBean private EntitlementTransportMapper mapper;
  @MockitoBean private EntitlementService entitlements;

  @Test
  void getsSubjectEntitlementFromPathParameters() throws Exception {
    UUID subjectId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    mvc.perform(
            get("/api/v1/billing/subjects/{subjectId}/entitlements/{product}", subjectId, "polity"))
        .andExpect(status().isOk());

    verify(entitlements).get(subjectId, "polity");
  }

  @Test
  void requiresSubjectEntitlementFromPathParameters() throws Exception {
    UUID subjectId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    mvc.perform(
            post(
                "/api/v1/billing/subjects/{subjectId}/entitlements/{product}/access",
                subjectId,
                "assembly"))
        .andExpect(status().isOk());

    verify(entitlements).require(subjectId, "assembly");
  }
}
