# Common API

The common-api artifact owns Cardo's API error value and exception contracts plus the HTTP-client
error translator. It preserves the existing io.github.lutzseverino.cardo.common.api packages
without bringing persistence, validation, or server exception handling into outbound clients.

The full common artifact remains the compatibility aggregate for services that need the other
shared primitives.

Public surface:

- `io.github.lutzseverino.cardo.common.api.ApiError`
- `io.github.lutzseverino.cardo.common.api.ApiException`
- `io.github.lutzseverino.cardo.common.api.ApiClientErrors`
