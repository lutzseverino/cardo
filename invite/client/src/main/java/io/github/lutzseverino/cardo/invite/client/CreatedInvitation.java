package io.github.lutzseverino.cardo.invite.client;

import java.net.URI;

public record CreatedInvitation(Invitation invitation, URI acceptUrl) {}
