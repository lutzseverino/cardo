package io.github.lutzseverino.cardo.billing.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.authorization.spring.AuthenticatedUser;
import io.github.lutzseverino.cardo.authorization.spring.AuthenticatedUserReader;
import io.github.lutzseverino.cardo.billing.api.model.CheckoutSessionResponse;
import io.github.lutzseverino.cardo.billing.api.model.CreateCheckoutSessionRequest;
import io.github.lutzseverino.cardo.billing.mapper.CheckoutSessionTransportMapper;
import io.github.lutzseverino.cardo.billing.model.BillingSessionResult;
import io.github.lutzseverino.cardo.billing.model.CreateCheckoutSessionInput;
import io.github.lutzseverino.cardo.billing.workflow.CreateCheckoutSessionWorkflow;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CheckoutSessionControllerTest {

  @Test
  void healthySynchronousProvisioningRetainsTheCreatedResponse() {
    CheckoutSessionTransportMapper mapper = mock(CheckoutSessionTransportMapper.class);
    CreateCheckoutSessionWorkflow workflow = mock(CreateCheckoutSessionWorkflow.class);
    AuthenticatedUserReader users = mock(AuthenticatedUserReader.class);
    CheckoutSessionController controller = new CheckoutSessionController(mapper, workflow, users);
    UUID subjectId = UUID.randomUUID();
    CreateCheckoutSessionRequest request = new CreateCheckoutSessionRequest();
    CreateCheckoutSessionInput input =
        new CreateCheckoutSessionInput(
            "polity",
            URI.create("https://app.example.com/success"),
            URI.create("https://app.example.com/cancel"));
    BillingSessionResult result =
        new BillingSessionResult("cs_1", "https://checkout.stripe.example/session");
    CheckoutSessionResponse response = new CheckoutSessionResponse();
    when(users.currentUser()).thenReturn(new AuthenticatedUser(subjectId, "subject", "Ada"));
    when(mapper.toInput(request)).thenReturn(input);
    when(workflow.create(subjectId, input)).thenReturn(result);
    when(mapper.toResponse(result)).thenReturn(response);

    var actual = controller.createCheckoutSession(request);

    assertThat(actual.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(actual.getBody()).isSameAs(response);
  }
}
