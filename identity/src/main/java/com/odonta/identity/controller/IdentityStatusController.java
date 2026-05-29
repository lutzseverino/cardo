package com.odonta.identity.controller;

import com.odonta.identity.api.IdentityApi;
import com.odonta.identity.api.model.ServiceStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${odonta.api.base-path}")
public class IdentityStatusController implements IdentityApi {

  @Override
  public ResponseEntity<ServiceStatus> getIdentity() {
    return ResponseEntity.ok(
        new ServiceStatus(ServiceStatus.ServiceEnum.IDENTITY, ServiceStatus.StatusEnum.OK));
  }
}
