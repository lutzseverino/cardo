package com.odonta.identity.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String authorizationSubject,
    String email,
    String name,
    String avatarUrl,
    String status,
    boolean emailVerified,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
