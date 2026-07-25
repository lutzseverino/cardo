package io.github.lutzseverino.cardo.identity.model;

import java.util.UUID;

public record BrowserSessionRefresh(
    String providerSessionId, String providerSubject, UUID leaseToken) {}
