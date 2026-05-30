package com.odonta.invite.controller;

import com.odonta.invite.api.InviteApi;
import com.odonta.invite.api.model.ServiceStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${odonta.api.base-path}")
public class InviteStatusController implements InviteApi {

  @Override
  public ResponseEntity<ServiceStatusResponse> getInviteStatus() {
    return ResponseEntity.ok(new ServiceStatusResponse("ok"));
  }
}
