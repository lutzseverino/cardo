package com.odonta.billing.service;

import com.odonta.billing.model.Entitlement;
import com.odonta.billing.model.EntitlementProjection;
import com.odonta.billing.model.EntitlementStatus;
import com.odonta.billing.model.EntitlementSyncItem;
import com.odonta.billing.repository.EntitlementRepository;
import com.odonta.common.api.ApiException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EntitlementService {

  private final EntitlementRepository entitlements;

  public EntitlementProjection get(UUID subjectId, String product) {
    return projection(subjectId, product);
  }

  public EntitlementProjection require(UUID subjectId, String product) {
    EntitlementProjection entitlement = projection(subjectId, product);
    if (!entitlement.getStatus().usable()) {
      throw ApiException.forbidden("entitlement_inactive", "Entitlement is not active.");
    }
    return entitlement;
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
