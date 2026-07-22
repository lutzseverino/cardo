package io.github.lutzseverino.cardo.authorization.token;

public interface RequestingPartyTokenClient {

  RequestingPartyToken authorize(RequestingPartyTokenRequest request);
}
