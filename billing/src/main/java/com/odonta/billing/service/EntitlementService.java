package com.odonta.billing.service;

import com.odonta.billing.mapper.EntitlementMapper;
import com.odonta.billing.model.Entitlement;
import com.odonta.billing.model.EntitlementProjection;
import com.odonta.billing.model.EntitlementResponse;
import com.odonta.billing.model.EntitlementStatus;
import com.odonta.billing.model.EntitlementSyncItem;
import com.odonta.billing.repository.EntitlementRepository;
import com.odonta.common.api.ApiException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
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
  public void replaceActive(UUID subjectId, Collection<EntitlementSyncItem> activeItems) {
    Map<String, EntitlementSyncItem> activeByProduct = activeByProduct(activeItems);

    for (Entitlement entitlement : entitlements.findBySubjectId(subjectId)) {
      EntitlementSyncItem active = activeByProduct.remove(entitlement.getProduct());
      if (active == null) {
        entitlement.setStatus(EntitlementStatus.CANCELED);
      } else {
        syncActive(entitlement, active);
      }
    }

    for (EntitlementSyncItem active : activeByProduct.values()) {
      Entitlement entitlement = Entitlement.create(subjectId, active.product());
      syncActive(entitlement, active);
      entitlements.save(entitlement);
    }
  }

  private Map<String, EntitlementSyncItem> activeByProduct(
      Collection<EntitlementSyncItem> activeItems) {
    Map<String, EntitlementSyncItem> activeByProduct = new LinkedHashMap<>();
    for (EntitlementSyncItem active : activeItems) {
      activeByProduct.put(active.product(), active);
    }
    return activeByProduct;
  }

  private void syncActive(Entitlement entitlement, EntitlementSyncItem active) {
    entitlement.setStatus(EntitlementStatus.ACTIVE);
    entitlement.setTenantLimit(active.tenantLimit());
    entitlement.setSeatLimit(active.seatLimit());
    entitlement.setTrialEndsAt(null);
    entitlement.setCurrentPeriodEndsAt(null);
  }

  private EntitlementProjection projection(UUID subjectId, String product) {
    return entitlements
        .findProjectedBySubjectIdAndProduct(subjectId, product)
        .orElseThrow(
            () -> ApiException.notFound("entitlement_not_found", "Entitlement not found."));
  }
}
