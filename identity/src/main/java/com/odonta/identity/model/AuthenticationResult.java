package com.odonta.identity.model;

import com.odonta.authorization.grant.EffectiveGrant;
import java.util.List;

public record AuthenticationResult(
    AuthenticatedPrincipal principal, String token, List<EffectiveGrant> grants) {}
