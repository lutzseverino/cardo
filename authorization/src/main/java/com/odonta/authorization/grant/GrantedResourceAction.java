package com.odonta.authorization.grant;

public record GrantedResourceAction(
    String id,
    String resourceId,
    String resourceName,
    String requesterSubject,
    String action,
    boolean granted) {}
