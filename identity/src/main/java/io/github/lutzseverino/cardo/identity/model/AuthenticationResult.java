package io.github.lutzseverino.cardo.identity.model;

import io.github.lutzseverino.cardo.authorization.grant.EffectiveGrant;
import java.util.List;

public record AuthenticationResult(
    AuthenticatedPrincipal principal, String token, List<EffectiveGrant> grants) {}
