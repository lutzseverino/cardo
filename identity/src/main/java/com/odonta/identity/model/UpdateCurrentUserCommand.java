package com.odonta.identity.model;

import com.odonta.common.validation.NullOrNotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCurrentUserCommand(
    @NullOrNotBlank @Size(max = 200) String name, @NullOrNotBlank String avatarUrl) {}
