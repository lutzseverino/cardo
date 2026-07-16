package io.github.lutzseverino.cardo.identity.client;

import java.util.UUID;

public record ProvisionalUser(UUID id, String authorizationSubject) {}
