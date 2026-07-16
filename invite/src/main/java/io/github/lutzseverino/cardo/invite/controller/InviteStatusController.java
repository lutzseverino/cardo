package io.github.lutzseverino.cardo.invite.controller;

import io.github.lutzseverino.cardo.invite.api.InviteApi;
import io.github.lutzseverino.cardo.invite.api.model.ServiceStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${cardo.api.base-path}")
public class InviteStatusController implements InviteApi {

  @Override
  public ResponseEntity<ServiceStatusResponse> getInviteStatus() {
    return ResponseEntity.ok(new ServiceStatusResponse("ok"));
  }
}
