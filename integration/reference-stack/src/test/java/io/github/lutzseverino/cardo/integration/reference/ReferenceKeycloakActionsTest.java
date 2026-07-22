package io.github.lutzseverino.cardo.integration.reference;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class ReferenceKeycloakActionsTest {

  @Test
  void parsesPinnedProviderFormsAndPreservesHiddenExecutionState() {
    ReferenceKeycloakActions.Form form =
        ReferenceKeycloakActions.form(
            """
            <form action="/realms/reference/login-actions/required-action?code=secret&amp;execution=one"
                  id="kc-passwd-update-form" method="post">
              <input type="hidden" name="execution" value="UPDATE_PASSWORD">
              <input type="password" name="password-new">
              <input disabled name="ignored" value="nope">
            </form>
            """,
            URI.create("http://provider.test/current"));

    assertThat(form.id()).isEqualTo("kc-passwd-update-form");
    assertThat(form.action())
        .isEqualTo(
            URI.create(
                "http://provider.test/realms/reference/login-actions/required-action?code=secret&execution=one"));
    assertThat(form.values())
        .containsEntry("execution", "UPDATE_PASSWORD")
        .doesNotContainKey("ignored");
  }
}
