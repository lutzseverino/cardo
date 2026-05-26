package com.odonta.billing.controller;

import com.odonta.authorization.spring.AuthenticatedUserReader;
import com.odonta.billing.BillingPermissions;
import com.odonta.billing.api.EntitlementsApi;
import com.odonta.billing.api.model.EntitlementResponse;
import com.odonta.billing.service.EntitlementService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${odonta.api.base-path}")
public class EntitlementController implements EntitlementsApi {

  private final EntitlementService entitlements;
  private final AuthenticatedUserReader users;

  EntitlementController(EntitlementService entitlements, AuthenticatedUserReader users) {
    this.entitlements = entitlements;
    this.users = users;
  }

  @Override
  public ResponseEntity<EntitlementResponse> getCurrentEntitlement(String product) {
    return ResponseEntity.ok(entitlements.get(users.currentUser().id(), product));
  }

  @Override
  @PreAuthorize("hasAuthority('" + BillingPermissions.ENTITLEMENT_READ_AUTHORITY + "')")
  public ResponseEntity<EntitlementResponse> getSubjectEntitlement(UUID subjectId, String product) {
    return ResponseEntity.ok(entitlements.get(subjectId, product));
  }

  @Override
  @PreAuthorize("hasAuthority('" + BillingPermissions.ENTITLEMENT_READ_AUTHORITY + "')")
  public ResponseEntity<EntitlementResponse> requireSubjectEntitlement(
      UUID subjectId, String product) {
    return ResponseEntity.ok(entitlements.require(subjectId, product));
  }
}
