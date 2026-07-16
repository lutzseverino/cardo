package io.github.lutzseverino.cardo.identity.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuthenticatedPrincipal(
    String sessionId,
    UUID userId,
    String keycloakSubject,
    String userEmail,
    String userName,
    String userAvatarUrl,
    UserStatus userStatus,
    boolean userEmailVerified,
    OffsetDateTime userCreatedAt,
    OffsetDateTime userUpdatedAt,
    AuthenticationMethod authenticationMethod,
    UUID authProviderId,
    OffsetDateTime expiresAt) {}
