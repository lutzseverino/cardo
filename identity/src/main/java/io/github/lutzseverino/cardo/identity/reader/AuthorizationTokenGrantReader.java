package io.github.lutzseverino.cardo.identity.reader;

import io.github.lutzseverino.cardo.authorization.grant.EffectiveGrant;
import java.util.List;

public interface AuthorizationTokenGrantReader {

  List<EffectiveGrant> read(String token);
}
