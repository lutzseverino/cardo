package com.odonta.billing.controller;

import com.odonta.billing.BillingPermissions;
import com.odonta.billing.model.EntitlementResponse;
import com.odonta.billing.service.EntitlementService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${odonta.api.base-path}/billing/subjects/{subjectId}/entitlements")
public class SubjectEntitlementController {

  private final EntitlementService entitlements;

  SubjectEntitlementController(EntitlementService entitlements) {
    this.entitlements = entitlements;
  }

  @GetMapping("/{product}")
  @PreAuthorize("hasAuthority('" + BillingPermissions.ENTITLEMENT_READ_AUTHORITY + "')")
  EntitlementResponse get(@PathVariable UUID subjectId, @PathVariable String product) {
    return entitlements.get(subjectId, product);
  }

  @PostMapping("/{product}/access")
  @PreAuthorize("hasAuthority('" + BillingPermissions.ENTITLEMENT_READ_AUTHORITY + "')")
  EntitlementResponse require(@PathVariable UUID subjectId, @PathVariable String product) {
    return entitlements.require(subjectId, product);
  }
}
