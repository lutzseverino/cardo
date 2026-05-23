package com.odonta.identity.model;

import jakarta.validation.constraints.Size;

public record UpdateCurrentUserRequest(@Size(min = 1, max = 200) String name, String avatarUrl) {}
