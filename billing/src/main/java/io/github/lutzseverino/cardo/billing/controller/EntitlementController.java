package io.github.lutzseverino.cardo.billing.controller;

import io.github.lutzseverino.cardo.authorization.spring.AuthenticatedUserReader;
import io.github.lutzseverino.cardo.billing.api.EntitlementsApi;
import io.github.lutzseverino.cardo.billing.api.model.EntitlementResponse;
import io.github.lutzseverino.cardo.billing.mapper.EntitlementTransportMapper;
import io.github.lutzseverino.cardo.billing.service.EntitlementService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${cardo.api.base-path}")
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
