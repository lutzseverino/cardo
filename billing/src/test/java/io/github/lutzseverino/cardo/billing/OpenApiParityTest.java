package io.github.lutzseverino.cardo.billing;

import static io.github.lutzseverino.cardo.openapi.testing.OpenApiParityAssertions.assertMatches;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.lutzseverino.cardo.authorization.spring.AuthenticatedUserReader;
import io.github.lutzseverino.cardo.billing.config.OpenApiConfiguration;
import io.github.lutzseverino.cardo.billing.controller.BillingStatusController;
import io.github.lutzseverino.cardo.billing.controller.CheckoutSessionController;
import io.github.lutzseverino.cardo.billing.controller.EntitlementController;
import io.github.lutzseverino.cardo.billing.controller.PortalSessionController;
import io.github.lutzseverino.cardo.billing.controller.StripeWebhookController;
import io.github.lutzseverino.cardo.billing.mapper.CheckoutSessionTransportMapper;
import io.github.lutzseverino.cardo.billing.mapper.EntitlementTransportMapper;
import io.github.lutzseverino.cardo.billing.mapper.PortalSessionTransportMapper;
import io.github.lutzseverino.cardo.billing.service.EntitlementService;
import io.github.lutzseverino.cardo.billing.workflow.CreateCheckoutSessionWorkflow;
import io.github.lutzseverino.cardo.billing.workflow.CreatePortalSessionWorkflow;
import io.github.lutzseverino.cardo.billing.workflow.ProcessStripeWebhookWorkflow;
import io.github.lutzseverino.cardo.common.api.ApiException;
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
      BillingStatusController.class,
      CheckoutSessionController.class,
      EntitlementController.class,
      PortalSessionController.class,
      StripeWebhookController.class
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
  @MockitoBean private AuthenticatedUserReader users;
  @MockitoBean private CheckoutSessionTransportMapper checkoutMapper;
  @MockitoBean private CreateCheckoutSessionWorkflow checkout;
  @MockitoBean private EntitlementTransportMapper entitlementMapper;
  @MockitoBean private EntitlementService entitlements;
  @MockitoBean private PortalSessionTransportMapper portalMapper;
  @MockitoBean private CreatePortalSessionWorkflow portal;
  @MockitoBean private ProcessStripeWebhookWorkflow webhooks;

  @Test
  void publicDocumentMatchesCanonicalMetadataAndTransportSurface() throws Exception {
    String runtime =
        mvc.perform(get("/openapi.json"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertMatches("openapi/billing.yaml", runtime, "/api/v1");
  }

  @Test
  void exposesStripeSynchronizationFailureAsDocumentedBadGateway() throws Exception {
    doThrow(ApiException.of(502, "billing_entitlements_sync_failed", "Sync failed."))
        .when(webhooks)
        .process("{}", "signature");

    mvc.perform(
            post("/api/v1/billing/webhooks/stripe")
                .header("Stripe-Signature", "signature")
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error.code").value("billing_entitlements_sync_failed"));
  }
}
