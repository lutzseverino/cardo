package com.odonta.identity.repository;

import com.odonta.identity.model.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<UserProjection> findProjectedById(UUID id);

  Optional<UserProjection> findProjectedByEmail(String email);

  Optional<UserProjection> findProjectedByKeycloakSubject(String keycloakSubject);

  List<UserProjection> findProjectedByKeycloakSubjectIn(Collection<String> keycloakSubjects);
}
