package com.odonta.identity.model;

import com.odonta.common.model.FieldUpdate;
import com.odonta.common.validation.NullOrNotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCurrentUserInput(
    @NullOrNotBlank @Size(max = 200) String name, FieldUpdate<String> avatarUrl) {}
