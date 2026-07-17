package io.github.lutzseverino.cardo.invite.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvitationGrantSnapshot {

  @Column(name = "resource_type", nullable = false)
  private String resourceType;

  @Column(nullable = false)
  private String action;

  InvitationGrantSnapshot(String resourceType, String action) {
    this.resourceType = resourceType;
    this.action = action;
  }

  InvitationGrantInput toInput() {
    return new InvitationGrantInput(resourceType, action);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof InvitationGrantSnapshot value)) {
      return false;
    }
    return Objects.equals(resourceType, value.resourceType) && Objects.equals(action, value.action);
  }

  @Override
  public int hashCode() {
    return Objects.hash(resourceType, action);
  }
}
