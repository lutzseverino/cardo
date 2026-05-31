package com.odonta.common.data;

public enum DataRetentionReason {
  MEDICAL_RECORD_RETENTION("medical_record_retention");

  private final String wireValue;

  DataRetentionReason(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }
}
