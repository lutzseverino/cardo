package io.github.lutzseverino.cardo.billing.controller;

import io.github.lutzseverino.cardo.billing.api.BillingApi;
import io.github.lutzseverino.cardo.billing.api.model.ServiceStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${cardo.api.base-path}")
public class BillingStatusController implements BillingApi {

  @Override
  public ResponseEntity<ServiceStatusResponse> getBillingStatus() {
    return ResponseEntity.ok(new ServiceStatusResponse("ok"));
  }
}
