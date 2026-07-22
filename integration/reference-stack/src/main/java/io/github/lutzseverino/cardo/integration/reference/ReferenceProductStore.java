package io.github.lutzseverino.cardo.integration.reference;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

interface ReferenceProductStore {

  InvitationState createInvitation(UUID requestId, String email, UUID invitedBy);

  Optional<ReferenceCommand> nextCommand();

  void recordCreated(UUID invitationId, UUID remoteInvitationId, UUID invitedUserId);

  void recordAcceptanceIntent(UUID invitationId, String subject, OffsetDateTime acceptedAt);

  void completeAcceptance(UUID invitationId, String subject, UUID receiptId);

  void completeCommand(UUID commandId);

  InvitationState invitation(UUID invitationId);

  InvitationState lockInvitation(UUID invitationId);

  long membershipCount(String subject);

  long receiptCount(UUID invitationId);

  void createOwnerMembership(String subject);

  enum CommandType {
    CREATE,
    ACCEPT
  }

  record InvitationState(
      UUID id,
      UUID requestId,
      String email,
      UUID invitedBy,
      UUID remoteInvitationId,
      UUID invitedUserId,
      String acceptedSubject,
      UUID receiptId) {}

  record ReferenceCommand(
      UUID id,
      CommandType type,
      UUID invitationId,
      String acceptedSubject,
      OffsetDateTime acceptedAt) {}
}
