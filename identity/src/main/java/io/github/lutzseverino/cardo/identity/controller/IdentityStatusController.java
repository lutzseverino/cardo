package io.github.lutzseverino.cardo.identity.controller;

import io.github.lutzseverino.cardo.identity.api.IdentityApi;
import io.github.lutzseverino.cardo.identity.api.model.ServiceStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${cardo.api.base-path}")
public class IdentityStatusController implements IdentityApi {

  @Override
  public ResponseEntity<ServiceStatusResponse> getIdentityStatus() {
    return ResponseEntity.ok(new ServiceStatusResponse("ok"));
  }
}
