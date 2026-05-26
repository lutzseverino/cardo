package com.odonta.billing.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odonta.common.api.ApiClientErrors;
import com.odonta.common.api.ApiException;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

public class BillingHttpClient {

  private final ObjectMapper json;
  private final RestClient rest;
  private final Supplier<String> serviceTokens;

  public BillingHttpClient(
      BillingClientProperties properties,
      Supplier<String> serviceTokens,
      ObjectMapper json,
      RestClient.Builder rest) {
    this.json = json;
    this.rest = rest.baseUrl(properties.baseUrl()).build();
    this.serviceTokens = serviceTokens;
  }

  public EntitlementResponse entitlement(UUID subjectId, String product) {
    return call(
        () ->
            rest.get()
                .uri("/billing/subjects/{subjectId}/entitlements/{product}", subjectId, product)
                .headers(headers -> headers.setBearerAuth(serviceToken()))
                .retrieve()
                .body(EntitlementResponse.class));
  }

  public EntitlementResponse requireEntitlement(UUID subjectId, String product) {
    return call(
        () ->
            rest.post()
                .uri(
                    "/billing/subjects/{subjectId}/entitlements/{product}/access",
                    subjectId,
                    product)
                .headers(headers -> headers.setBearerAuth(serviceToken()))
                .retrieve()
                .body(EntitlementResponse.class));
  }

  private <T> T call(BillingCall<T> call) {
    try {
      return call.run();
    } catch (RestClientResponseException exception) {
      throw apiException(exception);
    }
  }

  private String serviceToken() {
    String token = serviceTokens.get();
    if (token == null || token.isBlank()) {
      throw ApiException.of(
          500, "billing_service_token_missing", "Billing service token provider is missing.");
    }
    return token;
  }

  private ApiException apiException(RestClientResponseException exception) {
    return ApiClientErrors.from(exception, json, "billing_client_error", "Billing request failed.");
  }

  private interface BillingCall<T> {
    T run();
  }
}
