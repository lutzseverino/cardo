package io.github.lutzseverino.cardo.integration.reference;

import io.github.lutzseverino.cardo.authorization.AuthorizationAdminClient;
import io.github.lutzseverino.cardo.authorization.grant.ClientRoleAssignment;
import io.github.lutzseverino.cardo.authorization.grant.ClientRoleRevocation;
import io.github.lutzseverino.cardo.authorization.grant.GrantedResourceAction;
import io.github.lutzseverino.cardo.authorization.grant.ResourceActionAssignment;
import io.github.lutzseverino.cardo.authorization.grant.ResourceGrantQuery;
import io.github.lutzseverino.cardo.authorization.resource.AuthorizationResource;
import io.github.lutzseverino.cardo.authorization.resource.CreatedAuthorizationResource;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
class ReferenceGrantGate {

  private static final Duration DEADLINE = Duration.ofSeconds(20);
  private final AtomicReference<CountDownLatch> barrier =
      new AtomicReference<>(new CountDownLatch(0));

  void pause() {
    barrier.set(new CountDownLatch(1));
  }

  void release() {
    barrier.get().countDown();
  }

  AuthorizationAdminClient guard(AuthorizationAdminClient delegate) {
    return new AuthorizationAdminClient() {
      @Override
      public CreatedAuthorizationResource ensureResource(AuthorizationResource resource) {
        await();
        return delegate.ensureResource(resource);
      }

      @Override
      public void grantResourceActions(ResourceActionAssignment assignment) {
        await();
        delegate.grantResourceActions(assignment);
      }

      @Override
      public List<GrantedResourceAction> findResourceActionGrants(ResourceGrantQuery query) {
        return delegate.findResourceActionGrants(query);
      }

      @Override
      public void revokeResourceActionGrant(String resourceServerClientId, String ticketId) {
        await();
        delegate.revokeResourceActionGrant(resourceServerClientId, ticketId);
      }

      @Override
      public void ensureClientRolesAssigned(ClientRoleAssignment assignment) {
        await();
        delegate.ensureClientRolesAssigned(assignment);
      }

      @Override
      public void removeClientRoles(ClientRoleRevocation revocation) {
        await();
        delegate.removeClientRoles(revocation);
      }
    };
  }

  private void await() {
    try {
      if (!barrier.get().await(DEADLINE.toMillis(), TimeUnit.MILLISECONDS)) {
        throw new IllegalStateException("Reference grant gate exceeded its bounded deadline.");
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Reference grant gate was interrupted.", interrupted);
    }
  }
}
