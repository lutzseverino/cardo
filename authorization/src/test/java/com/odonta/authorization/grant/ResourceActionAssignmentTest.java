package com.odonta.authorization.grant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ResourceActionAssignmentTest {

  @Test
  void rejectsBlankResourceId() {
    assertThatThrownBy(() -> new ResourceActionAssignment("clinic", " ", "user-1", List.of("read")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("resourceId must not be blank");
  }

  @Test
  void rejectsEmptyActions() {
    assertThatThrownBy(
            () -> new ResourceActionAssignment("clinic", "resource-1", "user-1", List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("actions must not be empty");
  }
}
