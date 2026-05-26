package com.odonta.billing.controller;

import com.odonta.billing.api.StripeWebhooksApi;
import com.odonta.billing.integration.stripe.StripeWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${odonta.api.base-path}")
@RequiredArgsConstructor
public class StripeWebhookController implements StripeWebhooksApi {

  private final StripeWebhookService webhooks;

  @Override
  public ResponseEntity<Void> createStripeWebhookEvent(String stripeSignature, String body) {
    webhooks.handle(body, stripeSignature);
    return ResponseEntity.noContent().build();
  }
}
