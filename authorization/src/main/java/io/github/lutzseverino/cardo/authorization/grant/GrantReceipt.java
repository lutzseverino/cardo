package io.github.lutzseverino.cardo.authorization.grant;

import java.util.Objects;
import java.util.UUID;

public record GrantReceipt(UUID id, GrantReceiptStatus status, String failureCode) {

  public GrantReceipt {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(status, "status");
    if (GrantReceiptStatus.FAILED.equals(status)) {
      if (failureCode == null || failureCode.isBlank()) {
        throw new IllegalArgumentException("failed grant receipts require a failure code");
      }
    } else if (failureCode != null) {
      throw new IllegalArgumentException("only failed grant receipts may have a failure code");
    }
  }
}
