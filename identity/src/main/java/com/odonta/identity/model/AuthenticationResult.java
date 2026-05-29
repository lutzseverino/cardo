package com.odonta.identity.model;

public record AuthenticationResult(AuthenticatedPrincipal principal, String token) {}
