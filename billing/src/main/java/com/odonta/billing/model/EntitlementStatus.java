package com.odonta.billing.model;

import com.odonta.common.api.ApiException;

public enum EntitlementStatus {
  ACTIVE("active"),
  TRIALING("trialing"),
  PAST_DUE("past_due"),
  CANCELED("canceled");

  private final String wireValue;

  EntitlementStatus(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }

  public boolean usable() {
    return this == ACTIVE || this == TRIALING;
  }

  public static EntitlementStatus fromWireValue(String value) {
    for (EntitlementStatus status : values()) {
      if (status.wireValue.equals(value)) {
        return status;
      }
    }
    throw ApiException.badRequest("entitlement_status_invalid", "Entitlement status is invalid.");
  }
}
