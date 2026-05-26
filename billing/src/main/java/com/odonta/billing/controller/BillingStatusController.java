package com.odonta.billing.controller;

import com.odonta.billing.api.BillingApi;
import com.odonta.billing.api.model.ServiceStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${odonta.api.base-path}")
public class BillingStatusController implements BillingApi {

  @Override
  public ResponseEntity<ServiceStatusResponse> getBillingStatus() {
    return ResponseEntity.ok(new ServiceStatusResponse("ok"));
  }
}
