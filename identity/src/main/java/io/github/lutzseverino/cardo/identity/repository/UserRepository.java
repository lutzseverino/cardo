package io.github.lutzseverino.cardo.identity.repository;

import io.github.lutzseverino.cardo.identity.model.User;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select user from User user where user.id = :id")
  Optional<User> findEntityByIdForUpdate(@Param("id") UUID id);

  Optional<UserProjection> findProjectedById(UUID id);

  Optional<UserProjection> findProjectedByEmail(String email);

  Optional<UserProjection> findProjectedByKeycloakSubject(String keycloakSubject);

  List<UserProjection> findProjectedByKeycloakSubjectIn(Collection<String> keycloakSubjects);

  List<UserProjection> findAllProjectedBy();
}
