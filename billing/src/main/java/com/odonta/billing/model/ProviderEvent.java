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
    name = "billing_provider_events",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_billing_provider_events_provider_event",
            columnNames = {"provider", "provider_event_id"}))
public class ProviderEvent {

  @Id @GeneratedValue private UUID id;

  @Column(nullable = false)
  private String provider;

  @Column(name = "provider_event_id", nullable = false)
  private String providerEventId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(name = "processed_at", nullable = false, insertable = false, updatable = false)
  private OffsetDateTime processedAt;

  public static ProviderEvent processed(String provider, String providerEventId, String eventType) {
    ProviderEvent event = new ProviderEvent();
    event.provider = provider;
    event.providerEventId = providerEventId;
    event.eventType = eventType;
    return event;
  }
}
