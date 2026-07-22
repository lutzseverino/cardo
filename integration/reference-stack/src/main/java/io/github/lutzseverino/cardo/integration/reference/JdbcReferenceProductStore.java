package io.github.lutzseverino.cardo.integration.reference;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcReferenceProductStore implements ReferenceProductStore {

  private final JdbcOperations jdbc;

  JdbcReferenceProductStore(JdbcOperations jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  @Transactional
  public InvitationState createInvitation(UUID requestId, String email, UUID invitedBy) {
    UUID id = requestId;
    int inserted =
        jdbc.update(
            "insert into reference_invitation(id, request_id, email, invited_by) "
                + "values (?, ?, ?, ?) on conflict do nothing",
            id,
            requestId,
            email,
            invitedBy);
    if (inserted == 1) {
      insertCommand(CommandType.CREATE, id, null, null);
    }
    InvitationState state = invitation(id);
    if (!state.email().equals(email) || !state.invitedBy().equals(invitedBy)) {
      throw new IllegalStateException("Invitation request identifier changed meaning.");
    }
    return state;
  }

  @Override
  @Transactional
  public Optional<ReferenceCommand> nextCommand() {
    return jdbc
        .query(
            "select id, command_type, invitation_id, accepted_subject, accepted_at "
                + "from reference_command where completed_at is null order by created_at limit 1 "
                + "for update skip locked",
            this::command)
        .stream()
        .findFirst();
  }

  @Override
  @Transactional
  public void recordCreated(UUID invitationId, UUID remoteInvitationId, UUID invitedUserId) {
    int updated =
        jdbc.update(
            "update reference_invitation set remote_invitation_id = coalesce(remote_invitation_id, ?), "
                + "invited_user_id = coalesce(invited_user_id, ?), "
                + "updated_at = current_timestamp where id = ? "
                + "and (remote_invitation_id is null or remote_invitation_id = ?) "
                + "and (invited_user_id is null or invited_user_id = ?)",
            remoteInvitationId,
            invitedUserId,
            invitationId,
            remoteInvitationId,
            invitedUserId);
    if (updated != 1) {
      throw new IllegalStateException("Remote invitation identifier changed.");
    }
  }

  @Override
  @Transactional
  public void recordAcceptanceIntent(UUID invitationId, String subject, OffsetDateTime acceptedAt) {
    int updated =
        jdbc.update(
            "update reference_invitation set acceptance_intent_at = coalesce(acceptance_intent_at, ?), "
                + "accepted_subject = coalesce(accepted_subject, ?), updated_at = current_timestamp "
                + "where id = ? and (accepted_subject is null or accepted_subject = ?)",
            acceptedAt,
            subject,
            invitationId,
            subject);
    if (updated != 1) {
      throw new IllegalStateException("Invitation acceptance subject changed.");
    }
    insertCommand(CommandType.ACCEPT, invitationId, subject, acceptedAt);
  }

  @Override
  public void completeAcceptance(UUID invitationId, String subject, UUID receiptId) {
    jdbc.update(
        "insert into reference_membership(tenant_id, subject) values (?, ?) on conflict do nothing",
        ReferenceContract.TENANT_ID,
        subject);
    int updated =
        jdbc.update(
            "update reference_invitation set receipt_id = coalesce(receipt_id, ?), "
                + "accepted_at = coalesce(accepted_at, current_timestamp), updated_at = current_timestamp "
                + "where id = ? and accepted_subject = ?",
            receiptId,
            invitationId,
            subject);
    if (updated != 1) {
      throw new IllegalStateException("Invitation acceptance subject changed.");
    }
  }

  @Override
  public void completeCommand(UUID commandId) {
    jdbc.update(
        "update reference_command set completed_at = coalesce(completed_at, current_timestamp) where id = ?",
        commandId);
  }

  @Override
  public InvitationState invitation(UUID invitationId) {
    return jdbc
        .query(
            "select id, request_id, email, invited_by, remote_invitation_id, invited_user_id, "
                + "accepted_subject, receipt_id from reference_invitation where id = ?",
            this::mapInvitation,
            invitationId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown reference invitation."));
  }

  @Override
  public InvitationState lockInvitation(UUID invitationId) {
    return jdbc
        .query(
            "select id, request_id, email, invited_by, remote_invitation_id, invited_user_id, "
                + "accepted_subject, receipt_id from reference_invitation where id = ? for update",
            this::mapInvitation,
            invitationId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown reference invitation."));
  }

  @Override
  public long membershipCount(String subject) {
    Long count =
        jdbc.queryForObject(
            "select count(*) from reference_membership where tenant_id = ? and subject = ?",
            Long.class,
            ReferenceContract.TENANT_ID,
            subject);
    return count == null ? 0 : count;
  }

  @Override
  public long receiptCount(UUID invitationId) {
    Long count =
        jdbc.queryForObject(
            "select count(receipt_id) from reference_invitation where id = ?",
            Long.class,
            invitationId);
    return count == null ? 0 : count;
  }

  @Override
  public void createOwnerMembership(String subject) {
    jdbc.update(
        "insert into reference_membership(tenant_id, subject) values (?, ?) on conflict do nothing",
        ReferenceContract.TENANT_ID,
        subject);
  }

  private void insertCommand(
      CommandType type, UUID invitationId, String acceptedSubject, OffsetDateTime acceptedAt) {
    jdbc.update(
        "insert into reference_command(id, command_type, invitation_id, accepted_subject, accepted_at) "
            + "values (?, ?, ?, ?, ?) on conflict (command_type, invitation_id) do nothing",
        UUID.randomUUID(),
        type.name(),
        invitationId,
        acceptedSubject,
        acceptedAt);
  }

  private ReferenceCommand command(ResultSet row, int index) throws SQLException {
    return new ReferenceCommand(
        row.getObject("id", UUID.class),
        CommandType.valueOf(row.getString("command_type")),
        row.getObject("invitation_id", UUID.class),
        row.getString("accepted_subject"),
        row.getObject("accepted_at", OffsetDateTime.class));
  }

  private InvitationState mapInvitation(ResultSet row, int index) throws SQLException {
    return new InvitationState(
        row.getObject("id", UUID.class),
        row.getObject("request_id", UUID.class),
        row.getString("email"),
        row.getObject("invited_by", UUID.class),
        row.getObject("remote_invitation_id", UUID.class),
        row.getObject("invited_user_id", UUID.class),
        row.getString("accepted_subject"),
        row.getObject("receipt_id", UUID.class));
  }
}
