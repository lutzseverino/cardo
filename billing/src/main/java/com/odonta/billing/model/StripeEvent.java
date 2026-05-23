package com.odonta.billing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "billing_stripe_events")
public class StripeEvent {

  @Id
  @Column(name = "stripe_event_id", nullable = false)
  private String stripeEventId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(name = "processed_at", nullable = false, insertable = false, updatable = false)
  private OffsetDateTime processedAt;

  public static StripeEvent processed(String stripeEventId, String eventType) {
    StripeEvent event = new StripeEvent();
    event.stripeEventId = stripeEventId;
    event.eventType = eventType;
    return event;
  }
}
