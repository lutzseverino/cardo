package io.github.lutzseverino.cardo.identity.model;

import io.github.lutzseverino.cardo.authorization.grant.EffectiveGrant;
import java.time.OffsetDateTime;
import java.util.List;

public record AuthenticationResult(
    AuthenticatedPrincipal principal,
    List<EffectiveGrant> grants,
    OffsetDateTime authorizationExpiresAt,
    BrowserSessionMetadata browserSession) {}
