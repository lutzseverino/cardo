package com.odonta.identity.model;

public record AuthenticationResult(AuthenticatedPrincipalResponse principal, String token) {}
