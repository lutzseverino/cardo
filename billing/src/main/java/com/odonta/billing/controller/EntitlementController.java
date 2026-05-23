package com.odonta.billing.controller;

import com.odonta.authorization.spring.AuthenticatedUserReader;
import com.odonta.billing.model.EntitlementResponse;
import com.odonta.billing.service.EntitlementService;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${odonta.api.base-path}/billing/entitlements")
public class EntitlementController {

  private final EntitlementService entitlements;
  private final AuthenticatedUserReader users;

  EntitlementController(EntitlementService entitlements, AuthenticatedUserReader users) {
    this.entitlements = entitlements;
    this.users = users;
  }

  @GetMapping("/{product}")
  EntitlementResponse getCurrent(
      JwtAuthenticationToken authentication, @PathVariable String product) {
    return entitlements.get(users.currentUser(authentication).id(), product);
  }
}
