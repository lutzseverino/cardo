package com.odonta.authorization.spring;

import java.util.UUID;

public record AuthenticatedUser(UUID id, String authorizationSubject, String name) {}
