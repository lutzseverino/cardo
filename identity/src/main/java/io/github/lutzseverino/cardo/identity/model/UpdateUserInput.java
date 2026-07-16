package io.github.lutzseverino.cardo.identity.model;

import io.github.lutzseverino.cardo.common.model.FieldUpdate;
import io.github.lutzseverino.cardo.common.validation.NullOrNotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserInput(
    @NullOrNotBlank @Size(max = 200) String name,
    FieldUpdate<String> avatarUrl,
    UserStatus status) {}
