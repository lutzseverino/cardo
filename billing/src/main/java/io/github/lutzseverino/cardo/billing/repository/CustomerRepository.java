package io.github.lutzseverino.cardo.billing.repository;

import io.github.lutzseverino.cardo.billing.model.Customer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

  Optional<Customer> findBySubjectIdAndProvider(UUID subjectId, String provider);

  Optional<Customer> findByProviderAndProviderCustomerId(
      String provider, String providerCustomerId);
}
