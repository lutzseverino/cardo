package com.odonta.common.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum DataRetentionReason {
  MEDICAL_RECORD_RETENTION("medical_record_retention");

  private final String wireValue;

  DataRetentionReason(String wireValue) {
    this.wireValue = wireValue;
  }

  @JsonValue
  public String wireValue() {
    return wireValue;
  }

  @JsonCreator
  public static DataRetentionReason fromWireValue(String value) {
    return Arrays.stream(values())
        .filter(reason -> reason.wireValue.equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown data retention reason: " + value));
  }
}
