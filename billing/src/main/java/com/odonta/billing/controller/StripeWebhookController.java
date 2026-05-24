package com.odonta.billing.controller;

import com.odonta.billing.integration.stripe.StripeWebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${odonta.api.base-path}/billing/webhooks/stripe")
public class StripeWebhookController {

  private final StripeWebhookService webhooks;

  StripeWebhookController(StripeWebhookService webhooks) {
    this.webhooks = webhooks;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void create(@RequestBody String payload, @RequestHeader("Stripe-Signature") String signature) {
    webhooks.handle(payload, signature);
  }
}
