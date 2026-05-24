package com.odonta.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.odonta.billing.mapper.EntitlementMapperImpl;
import com.odonta.billing.model.Entitlement;
import com.odonta.billing.model.EntitlementProjection;
import com.odonta.billing.model.EntitlementStatus;
import com.odonta.billing.model.EntitlementSyncItem;
import com.odonta.billing.repository.EntitlementRepository;
import com.odonta.common.api.ApiException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntitlementServiceTest {

  @Mock private EntitlementRepository entitlements;

  @Test
  void requiresUsableEntitlement() {
    UUID subjectId = UUID.randomUUID();
    EntitlementService service = new EntitlementService(new EntitlementMapperImpl(), entitlements);
    when(entitlements.findProjectedBySubjectIdAndProduct(subjectId, "clinic"))
        .thenReturn(Optional.of(entitlement(subjectId, EntitlementStatus.TRIALING)));

    assertThat(service.require(subjectId, "clinic").status()).isEqualTo("trialing");
  }

  @Test
  void rejectsInactiveEntitlement() {
    UUID subjectId = UUID.randomUUID();
    EntitlementService service = new EntitlementService(new EntitlementMapperImpl(), entitlements);
    when(entitlements.findProjectedBySubjectIdAndProduct(subjectId, "clinic"))
        .thenReturn(Optional.of(entitlement(subjectId, EntitlementStatus.PAST_DUE)));

    assertThatThrownBy(() -> service.require(subjectId, "clinic"))
        .isInstanceOf(ApiException.class)
        .hasMessage("Entitlement is not active.");
  }

  @Test
  void replacesActiveEntitlements() {
    UUID subjectId = UUID.randomUUID();
    EntitlementService service = new EntitlementService(new EntitlementMapperImpl(), entitlements);
    when(entitlements.findBySubjectId(subjectId)).thenReturn(List.of());

    service.replaceActive(subjectId, List.of(new EntitlementSyncItem("clinic", 1, 5)));

    ArgumentCaptor<Entitlement> captor = ArgumentCaptor.forClass(Entitlement.class);
    verify(entitlements).save(captor.capture());
    Entitlement entitlement = captor.getValue();
    assertThat(entitlement.getSubjectId()).isEqualTo(subjectId);
    assertThat(entitlement.getProduct()).isEqualTo("clinic");
    assertThat(entitlement.getStatus()).isEqualTo(EntitlementStatus.ACTIVE);
    assertThat(entitlement.getTenantLimit()).isEqualTo(1);
    assertThat(entitlement.getSeatLimit()).isEqualTo(5);
  }

  private EntitlementProjection entitlement(UUID subjectId, EntitlementStatus status) {
    return new TestEntitlementProjection(subjectId, status);
  }

  private record TestEntitlementProjection(UUID subjectId, EntitlementStatus status)
      implements EntitlementProjection {

    @Override
    public UUID getId() {
      return UUID.randomUUID();
    }

    @Override
    public UUID getSubjectId() {
      return subjectId;
    }

    @Override
    public String getProduct() {
      return "clinic";
    }

    @Override
    public EntitlementStatus getStatus() {
      return status;
    }

    @Override
    public Integer getTenantLimit() {
      return 1;
    }

    @Override
    public Integer getSeatLimit() {
      return 5;
    }

    @Override
    public OffsetDateTime getTrialEndsAt() {
      return null;
    }

    @Override
    public OffsetDateTime getCurrentPeriodEndsAt() {
      return null;
    }

    @Override
    public OffsetDateTime getCreatedAt() {
      return OffsetDateTime.now();
    }

    @Override
    public OffsetDateTime getUpdatedAt() {
      return OffsetDateTime.now();
    }
  }
}
