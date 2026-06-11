package com.odonta.identity.client;

import java.util.UUID;

public record ProvisionalUser(UUID id, String authorizationSubject) {}
