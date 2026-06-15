package com.odonta.identity.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResult(
    UUID id,
    String authorizationSubject,
    String email,
    String name,
    String avatarUrl,
    UserStatus status,
    boolean emailVerified,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
