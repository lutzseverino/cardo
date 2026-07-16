package io.github.lutzseverino.cardo.invite.repository;

import io.github.lutzseverino.cardo.invite.model.Invitation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

  Optional<InvitationProjection> findProjectedById(UUID id);

  Optional<InvitationProjection> findProjectedByToken(String token);
}
