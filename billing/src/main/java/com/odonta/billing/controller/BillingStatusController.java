package com.odonta.billing.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${odonta.api.base-path}/billing")
public class BillingStatusController {

  @GetMapping
  Map<String, String> status() {
    return Map.of("status", "ok");
  }
}
