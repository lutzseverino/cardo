package io.github.lutzseverino.cardo.billing.integration.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stripe.StripeClient;
import com.stripe.model.Customer;
import com.stripe.net.ApiRequest;
import com.stripe.net.StripeResponseGetter;
import java.lang.reflect.Type;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StripeBillingProviderTest {

  @Test
  void usesTheSameBoundedIdempotencyKeyForRepeatedCustomerProvisioning() throws Exception {
    StripeResponseGetter responseGetter = mock(StripeResponseGetter.class);
    Customer customer = new Customer();
    customer.setId("cus_1");
    when(responseGetter.request(any(ApiRequest.class), eq((Type) Customer.class)))
        .thenReturn(customer);
    StripeBillingProvider provider =
        new StripeBillingProvider(
            mock(StripeCheckoutCatalog.class), new StripeClient(responseGetter));
    UUID subjectId = UUID.fromString("97ed77fc-a6b4-445d-a26b-a63f12800be1");

    provider.createCustomer(subjectId);
    provider.createCustomer(subjectId);

    ArgumentCaptor<ApiRequest> requests = ArgumentCaptor.forClass(ApiRequest.class);
    verify(responseGetter, times(2)).request(requests.capture(), eq((Type) Customer.class));
    assertThat(requests.getAllValues())
        .extracting(request -> request.getOptions().getIdempotencyKey())
        .containsExactly(
            "cardo-billing-customer-v1:97ed77fc-a6b4-445d-a26b-a63f12800be1",
            "cardo-billing-customer-v1:97ed77fc-a6b4-445d-a26b-a63f12800be1");
    assertThat(requests.getValue().getOptions().getIdempotencyKey()).hasSizeLessThanOrEqualTo(255);
  }
}
