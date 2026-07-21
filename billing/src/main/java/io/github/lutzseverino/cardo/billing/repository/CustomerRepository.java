package io.github.lutzseverino.cardo.billing.repository;

import io.github.lutzseverino.cardo.billing.model.Customer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

  Optional<Customer> findEntityBySubjectIdAndProvider(UUID subjectId, String provider);

  Optional<Customer> findEntityByProviderAndProviderCustomerId(
      String provider, String providerCustomerId);

  Optional<CustomerProjection> findProjectedBySubjectIdAndProvider(UUID subjectId, String provider);

  Optional<CustomerProjection> findProjectedByProviderAndProviderCustomerId(
      String provider, String providerCustomerId);
}
