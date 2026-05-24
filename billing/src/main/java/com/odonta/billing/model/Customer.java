package com.odonta.billing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "billing_customers",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_billing_customers_subject_provider",
          columnNames = {"subject_id", "provider"}),
      @UniqueConstraint(
          name = "uq_billing_customers_provider_customer",
          columnNames = {"provider", "provider_customer_id"})
    })
public class Customer {

  @Id @GeneratedValue private UUID id;

  @Column(name = "subject_id", nullable = false)
  private UUID subjectId;

  @Column(nullable = false)
  private String provider;

  @Column(name = "provider_customer_id", nullable = false)
  private String providerCustomerId;

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
  private OffsetDateTime updatedAt;

  public static Customer create(UUID subjectId, String provider, String providerCustomerId) {
    Customer customer = new Customer();
    customer.subjectId = subjectId;
    customer.provider = provider;
    customer.providerCustomerId = providerCustomerId;
    return customer;
  }
}
