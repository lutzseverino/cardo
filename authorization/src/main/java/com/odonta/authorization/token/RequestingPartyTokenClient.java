package com.odonta.authorization.token;

public interface RequestingPartyTokenClient {

  RequestingPartyToken authorize(RequestingPartyTokenRequest request);
}
