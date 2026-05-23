package com.odonta.invite.repository;

import com.odonta.invite.model.Invitation;
import com.odonta.invite.model.InvitationProjection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

  Optional<InvitationProjection> findProjectedById(UUID id);

  Optional<InvitationProjection> findProjectedByToken(String token);
}
