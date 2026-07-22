package io.github.lutzseverino.cardo.integration.reference;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.github.lutzseverino.cardo.identity.productauth.ProductRequestRules;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.http.HttpMethod;

class ReferenceProductConfigurationTest {

  @Test
  void limitsTheReferenceProductToItsDocumentedPublicAndAuthenticatedRoutes() {
    ProductRequestRules rules = mock(ProductRequestRules.class, Answers.RETURNS_SELF);

    new ReferenceProductConfiguration().referenceRequestPolicy().authorize(rules);

    verify(rules).permitAll(HttpMethod.GET, "/");
    verify(rules).permitAll("/invitations/**");
    verify(rules).permitAll("/internal/reference/**");
    verify(rules).authenticated("/api/reference/invitations/**", "/api/reference/convergence/**");
    verify(rules)
        .hasAuthority(
            ReferenceContract.TENANT_AUTHORITY, HttpMethod.GET, "/api/reference/tenants/*");
    verify(rules).authenticated("/api/reference/billing/**");
    verifyNoMoreInteractions(rules);
  }
}
