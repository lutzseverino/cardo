package io.github.lutzseverino.cardo.integration.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.lutzseverino.cardo.authorization.AuthorizationAdminClient;
import io.github.lutzseverino.cardo.authorization.resource.CreatedAuthorizationResource;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ReferenceGrantGateTest {

  @Test
  void pausesBeforeTheRealAdapterMutationAndResumesAfterRelease() throws Exception {
    ReferenceGrantGate gate = new ReferenceGrantGate();
    AuthorizationAdminClient delegate = mock(AuthorizationAdminClient.class);
    CreatedAuthorizationResource created = new CreatedAuthorizationResource("resource-id", "name");
    when(delegate.ensureResource(ReferenceContract.tenantResource())).thenReturn(created);
    AuthorizationAdminClient guarded = gate.guard(delegate);
    gate.pause();

    CompletableFuture<CreatedAuthorizationResource> call =
        CompletableFuture.supplyAsync(
            () -> guarded.ensureResource(ReferenceContract.tenantResource()));
    Thread.sleep(50);
    assertThat(call).isNotDone();

    gate.release();
    assertThat(
            call.get(Duration.ofSeconds(2).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS))
        .isEqualTo(created);
    verify(delegate).ensureResource(ReferenceContract.tenantResource());
  }
}
