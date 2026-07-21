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
import com.stripe.model.StripeSearchResult;
import com.stripe.net.ApiRequest;
import com.stripe.net.StripeResponseGetter;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StripeBillingProviderTest {

  @Test
  void createReusesTheLegacySubjectKeyBeforeAddingTheOperationMarker() throws Exception {
    StripeResponseGetter responseGetter = mock(StripeResponseGetter.class);
    Customer customer = new Customer();
    customer.setId("cus_1");
    when(responseGetter.request(any(ApiRequest.class), eq((Type) Customer.class)))
        .thenReturn(customer);
    StripeBillingProvider provider = provider(responseGetter);
    UUID subjectId = UUID.fromString("97ed77fc-a6b4-445d-a26b-a63f12800be1");
    UUID operationId = UUID.fromString("ce129973-ec3c-47f2-acf1-2364295798b7");

    provider.createCustomer(subjectId, operationId);

    ArgumentCaptor<ApiRequest> requests = ArgumentCaptor.forClass(ApiRequest.class);
    verify(responseGetter, times(2)).request(requests.capture(), eq((Type) Customer.class));
    ApiRequest create = requests.getAllValues().get(0);
    ApiRequest mark = requests.getAllValues().get(1);
    assertThat(create.getOptions().getIdempotencyKey())
        .isEqualTo("cardo-billing-customer-v1:" + subjectId)
        .hasSizeLessThanOrEqualTo(255);
    assertThat(create.getParams().get("metadata"))
        .isInstanceOfSatisfying(
            Map.class,
            metadata -> {
              assertThat(metadata).containsOnly(Map.entry("subject_id", subjectId.toString()));
            });
    assertThat(mark.getOptions().getIdempotencyKey())
        .isEqualTo("cardo-billing-customer-marker-v2:" + operationId);
    assertThat(mark.getParams().get("metadata"))
        .isInstanceOfSatisfying(
            Map.class,
            metadata ->
                assertThat(metadata)
                    .containsOnly(
                        Map.entry(
                            StripeBillingProvider.PROVISIONING_METADATA_KEY,
                            operationId.toString())));
  }

  @Test
  void recoverySearchesTheExactOperationMarkerAndReturnsAllMatchesUpToTwo() throws Exception {
    StripeResponseGetter responseGetter = mock(StripeResponseGetter.class);
    Customer first = new Customer();
    first.setId("cus_1");
    Customer second = new Customer();
    second.setId("cus_2");
    StripeSearchResult<Customer> result = new StripeSearchResult<>();
    result.setData(List.of(first, second));
    when(responseGetter.request(any(ApiRequest.class), any(Type.class))).thenReturn(result);
    StripeBillingProvider provider = provider(responseGetter);
    UUID operationId = UUID.fromString("ce129973-ec3c-47f2-acf1-2364295798b7");

    assertThat(provider.findCustomersByProvisioningId(operationId))
        .containsExactly("cus_1", "cus_2");

    ArgumentCaptor<ApiRequest> request = ArgumentCaptor.forClass(ApiRequest.class);
    verify(responseGetter).request(request.capture(), any(Type.class));
    assertThat(request.getValue().getParams())
        .containsEntry("query", "metadata['cardo_provisioning_id']:'" + operationId + "'")
        .containsEntry("limit", 2L);
  }

  @Test
  void migrationRecoverySearchesTheLegacySubjectMarker() throws Exception {
    StripeResponseGetter responseGetter = mock(StripeResponseGetter.class);
    Customer customer = new Customer();
    customer.setId("cus_legacy");
    StripeSearchResult<Customer> result = new StripeSearchResult<>();
    result.setData(List.of(customer));
    when(responseGetter.request(any(ApiRequest.class), any(Type.class))).thenReturn(result);
    StripeBillingProvider provider = provider(responseGetter);
    UUID subjectId = UUID.fromString("97ed77fc-a6b4-445d-a26b-a63f12800be1");

    assertThat(provider.findCustomersBySubjectId(subjectId)).containsExactly("cus_legacy");

    ArgumentCaptor<ApiRequest> request = ArgumentCaptor.forClass(ApiRequest.class);
    verify(responseGetter).request(request.capture(), any(Type.class));
    assertThat(request.getValue().getParams())
        .containsEntry("query", "metadata['subject_id']:'" + subjectId + "'")
        .containsEntry("limit", 2L);
  }

  private StripeBillingProvider provider(StripeResponseGetter responseGetter) {
    return new StripeBillingProvider(
        mock(StripeCheckoutCatalog.class), new StripeClient(responseGetter));
  }
}
