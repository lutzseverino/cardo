package io.github.lutzseverino.cardo.identity.client;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IdentityUser(
    UUID id,
    String authorizationSubject,
    String email,
    String name,
    String avatarUrl,
    IdentityUserStatus status,
    boolean emailVerified,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
