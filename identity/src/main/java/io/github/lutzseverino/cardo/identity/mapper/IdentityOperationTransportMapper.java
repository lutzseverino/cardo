package io.github.lutzseverino.cardo.identity.mapper;

import io.github.lutzseverino.cardo.identity.api.model.IdentityOperationResponse;
import io.github.lutzseverino.cardo.identity.api.model.IdentityOperationStatus;
import io.github.lutzseverino.cardo.identity.model.IdentityOperationResult;
import org.springframework.stereotype.Component;

@Component
public class IdentityOperationTransportMapper {

  public IdentityOperationResponse toResponse(IdentityOperationResult result) {
    return new IdentityOperationResponse(
            result.id(),
            result.userId(),
            IdentityOperationStatus.fromValue(result.status().wireValue()),
            result.attemptCount(),
            result.createdAt(),
            result.updatedAt())
        .lastError(result.lastError())
        .expiresAt(result.expiresAt())
        .completedAt(result.completedAt());
  }
}
