package io.github.lutzseverino.cardo.authorization.access;

import java.util.UUID;

public record AccessProfileResult(
    UUID id, String product, UUID tenantId, String name, String description, boolean template) {}
