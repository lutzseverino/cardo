package io.github.lutzseverino.cardo.identity.model;

import io.github.lutzseverino.cardo.authorization.grant.EffectiveGrant;
import java.time.OffsetDateTime;
import java.util.List;

public record CurrentAuthentication(
    String authorizationSubject,
    String sessionId,
    String accessToken,
    OffsetDateTime expiresAt,
    List<EffectiveGrant> grants) {}
