package com.odonta.billing.service;

import com.odonta.billing.mapper.EntitlementMapper;
import com.odonta.billing.model.Entitlement;
import com.odonta.billing.model.EntitlementProjection;
import com.odonta.billing.model.EntitlementResponse;
import com.odonta.billing.model.EntitlementStatus;
import com.odonta.billing.repository.EntitlementRepository;
import com.odonta.common.api.ApiException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EntitlementService {

  private final EntitlementMapper mapper;
  private final EntitlementRepository entitlements;

  EntitlementService(EntitlementMapper mapper, EntitlementRepository entitlements) {
    this.mapper = mapper;
    this.entitlements = entitlements;
  }

  public EntitlementResponse get(UUID subjectId, String product) {
    return mapper.toResponse(projection(subjectId, product));
  }

  public EntitlementResponse require(UUID subjectId, String product) {
    EntitlementProjection entitlement = projection(subjectId, product);
    if (!entitlement.getStatus().usable()) {
      throw ApiException.forbidden("entitlement_inactive", "Entitlement is not active.");
    }
    return mapper.toResponse(entitlement);
  }

  @Transactional
  public void sync(
      UUID subjectId,
      String product,
      EntitlementStatus status,
      Integer tenantLimit,
      Integer seatLimit,
      OffsetDateTime trialEndsAt,
      OffsetDateTime currentPeriodEndsAt) {
    Entitlement entitlement =
        entitlements
            .findBySubjectIdAndProduct(subjectId, product)
            .orElseGet(() -> Entitlement.create(subjectId, product));
    entitlement.setStatus(status);
    entitlement.setTenantLimit(tenantLimit);
    entitlement.setSeatLimit(seatLimit);
    entitlement.setTrialEndsAt(trialEndsAt);
    entitlement.setCurrentPeriodEndsAt(currentPeriodEndsAt);
    entitlements.save(entitlement);
  }

  private EntitlementProjection projection(UUID subjectId, String product) {
    return entitlements
        .findProjectedBySubjectIdAndProduct(subjectId, product)
        .orElseThrow(
            () -> ApiException.notFound("entitlement_not_found", "Entitlement not found."));
  }
}
