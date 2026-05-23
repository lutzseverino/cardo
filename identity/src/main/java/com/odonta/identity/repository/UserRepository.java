package com.odonta.identity.repository;

import com.odonta.identity.model.User;
import com.odonta.identity.model.UserProjection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<UserProjection> findProjectedById(UUID id);

  Optional<UserProjection> findProjectedByEmail(String email);

  Optional<UserProjection> findProjectedByKeycloakSubject(String keycloakSubject);
}
