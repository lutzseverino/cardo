package com.odonta.identity.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuthenticatedPrincipalResponse(
    String sessionId,
    UserResponse user,
    String authenticationMethod,
    UUID authProviderId,
    OffsetDateTime expiresAt) {}
