package io.github.lutzseverino.cardo.identity.reader;

import io.github.lutzseverino.cardo.identity.model.AuthorizationTokenResult;

public interface AuthorizationTokenReader {

  AuthorizationTokenResult read(String token);
}
