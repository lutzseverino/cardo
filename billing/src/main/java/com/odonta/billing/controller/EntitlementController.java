package com.odonta.billing.controller;

import com.odonta.authorization.spring.AuthenticatedUserReader;
import com.odonta.billing.BillingPermissions;
import com.odonta.billing.api.EntitlementsApi;
import com.odonta.billing.api.model.EntitlementResponse;
import com.odonta.billing.mapper.EntitlementMapper;
import com.odonta.billing.service.EntitlementService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${odonta.api.base-path}")
@RequiredArgsConstructor
public class EntitlementController implements EntitlementsApi {

  private final EntitlementMapper mapper;
  private final EntitlementService entitlements;
  private final AuthenticatedUserReader users;

  @Override
  public ResponseEntity<EntitlementResponse> getCurrentEntitlement(String product) {
    return ResponseEntity.ok(
        mapper.toResponse(entitlements.get(users.currentUser().id(), product)));
  }

  @Override
  @PreAuthorize("hasAuthority('" + BillingPermissions.ENTITLEMENT_READ_AUTHORITY + "')")
  public ResponseEntity<EntitlementResponse> getSubjectEntitlement(UUID subjectId, String product) {
    return ResponseEntity.ok(mapper.toResponse(entitlements.get(subjectId, product)));
  }

  @Override
  @PreAuthorize("hasAuthority('" + BillingPermissions.ENTITLEMENT_READ_AUTHORITY + "')")
  public ResponseEntity<EntitlementResponse> requireSubjectEntitlement(
      UUID subjectId, String product) {
    return ResponseEntity.ok(mapper.toResponse(entitlements.require(subjectId, product)));
  }
}
