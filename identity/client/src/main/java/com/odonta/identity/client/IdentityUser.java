package com.odonta.identity.client;

import java.util.UUID;

public record IdentityUser(UUID id, String authorizationSubject, String email, String name) {}
