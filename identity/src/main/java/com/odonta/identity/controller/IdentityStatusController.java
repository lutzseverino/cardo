package com.odonta.identity.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${odonta.api.base-path}/identity")
public class IdentityStatusController {

  @GetMapping
  Map<String, String> get() {
    return Map.of("service", "identity", "status", "ok");
  }
}
