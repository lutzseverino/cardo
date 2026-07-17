package io.github.lutzseverino.cardo.billing.service;

import io.github.lutzseverino.cardo.billing.BillingPermissions;
import io.github.lutzseverino.cardo.billing.mapper.EntitlementApplicationMapper;
import io.github.lutzseverino.cardo.billing.model.Entitlement;
import io.github.lutzseverino.cardo.billing.model.EntitlementResult;
import io.github.lutzseverino.cardo.billing.model.EntitlementStatus;
import io.github.lutzseverino.cardo.billing.model.EntitlementSyncItem;
import io.github.lutzseverino.cardo.billing.repository.EntitlementProjection;
import io.github.lutzseverino.cardo.billing.repository.EntitlementRepository;
import io.github.lutzseverino.cardo.common.api.ApiException;
import jakarta.validation.constraints.NotBlank;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Validated
@Service
@RequiredArgsConstructor
public class EntitlementService {

  private final EntitlementRepository entitlements;
  private final EntitlementApplicationMapper mapper;

  public EntitlementResult getCurrent(UUID subjectId, @NotBlank String product) {
    return getResult(subjectId, product);
  }

  @PreAuthorize("hasAuthority('" + BillingPermissions.ENTITLEMENT_READ_AUTHORITY + "')")
  public EntitlementResult get(UUID subjectId, @NotBlank String product) {
    return getResult(subjectId, product);
  }

  @PreAuthorize("hasAuthority('" + BillingPermissions.ENTITLEMENT_READ_AUTHORITY + "')")
  public EntitlementResult require(UUID subjectId, @NotBlank String product) {
    EntitlementProjection entitlement = getProjection(subjectId, product);
    if (!entitlement.getStatus().usable()) {
      throw ApiException.forbidden("entitlement_inactive", "Entitlement is not active.");
    }
    return mapper.toResult(entitlement);
  }

  @Transactional
  public void replaceActive(UUID subjectId, Collection<EntitlementSyncItem> activeItems) {
    Map<String, EntitlementSyncItem> activeByProduct = activeByProduct(activeItems);

    for (Entitlement entitlement : entitlements.findEntitiesBySubjectId(subjectId)) {
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

  private EntitlementProjection getProjection(UUID subjectId, String product) {
    return entitlements
        .findProjectedBySubjectIdAndProduct(subjectId, product)
        .orElseThrow(
            () -> ApiException.notFound("entitlement_not_found", "Entitlement not found."));
  }

  private EntitlementResult getResult(UUID subjectId, String product) {
    return mapper.toResult(getProjection(subjectId, product));
  }
}
