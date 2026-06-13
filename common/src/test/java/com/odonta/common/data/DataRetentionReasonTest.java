package com.odonta.common.data;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class DataRetentionReasonTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void usesTheOpenApiWireValue() throws Exception {
    assertThat(objectMapper.writeValueAsString(DataRetentionReason.MEDICAL_RECORD_RETENTION))
        .isEqualTo("\"medical_record_retention\"");
    assertThat(objectMapper.readValue("\"medical_record_retention\"", DataRetentionReason.class))
        .isEqualTo(DataRetentionReason.MEDICAL_RECORD_RETENTION);
  }
}
