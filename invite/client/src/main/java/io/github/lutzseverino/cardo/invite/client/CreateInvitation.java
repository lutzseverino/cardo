package io.github.lutzseverino.cardo.invite.client;

import java.net.URI;
import java.util.UUID;

public record CreateInvitation(
    UUID requestId,
    UUID tenantId,
    String tenantResourceType,
    String email,
    UUID invitedBy,
    URI acceptUrlBase) {}
