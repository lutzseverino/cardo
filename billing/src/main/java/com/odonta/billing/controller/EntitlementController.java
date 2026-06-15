package com.odonta.billing.controller;

import com.odonta.authorization.spring.AuthenticatedUserReader;
import com.odonta.billing.api.EntitlementsApi;
import com.odonta.billing.api.model.EntitlementResponse;
import com.odonta.billing.mapper.EntitlementTransportMapper;
import com.odonta.billing.service.EntitlementService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${odonta.api.base-path}")
@RequiredArgsConstructor
public class EntitlementController implements EntitlementsApi {

  private final EntitlementTransportMapper mapper;
  private final EntitlementService entitlements;
  private final AuthenticatedUserReader users;

  @Override
  public ResponseEntity<EntitlementResponse> getCurrentEntitlement(String product) {
    return ResponseEntity.ok(
        mapper.toResponse(entitlements.getCurrent(users.currentUser().id(), product)));
  }

  @Override
  public ResponseEntity<EntitlementResponse> getSubjectEntitlement(UUID subjectId, String product) {
    return ResponseEntity.ok(mapper.toResponse(entitlements.get(subjectId, product)));
  }

  @Override
  public ResponseEntity<EntitlementResponse> requireSubjectEntitlement(
      UUID subjectId, String product) {
    return ResponseEntity.ok(mapper.toResponse(entitlements.require(subjectId, product)));
  }
}
