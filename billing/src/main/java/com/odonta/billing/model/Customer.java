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
      @UniqueConstraint(name = "uq_billing_customers_subject", columnNames = "subject_id"),
      @UniqueConstraint(name = "uq_billing_customers_stripe", columnNames = "stripe_customer_id")
    })
public class Customer {

  @Id @GeneratedValue private UUID id;

  @Column(name = "subject_id", nullable = false)
  private UUID subjectId;

  @Column(name = "stripe_customer_id", nullable = false)
  private String stripeCustomerId;

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
  private OffsetDateTime updatedAt;

  public static Customer create(UUID subjectId, String stripeCustomerId) {
    Customer customer = new Customer();
    customer.subjectId = subjectId;
    customer.stripeCustomerId = stripeCustomerId;
    return customer;
  }
}
