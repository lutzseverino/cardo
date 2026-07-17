package io.github.lutzseverino.cardo.billing.controller;

import io.github.lutzseverino.cardo.billing.api.StripeWebhooksApi;
import io.github.lutzseverino.cardo.billing.workflow.ProcessStripeWebhookWorkflow;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${cardo.api.base-path}")
@RequiredArgsConstructor
public class StripeWebhookController implements StripeWebhooksApi {

  private final ProcessStripeWebhookWorkflow webhooks;

  @Override
  public ResponseEntity<Void> createStripeWebhookEvent(String stripeSignature, String body) {
    webhooks.process(body, stripeSignature);
    return ResponseEntity.noContent().build();
  }
}
