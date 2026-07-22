# Common API

The common-api artifact owns Cardo's dependency-light API contracts: API error values and
exceptions, HTTP-client error translation, and the partial-update value and Jakarta Validation
extractor. It preserves the existing `io.github.lutzseverino.cardo.common.api`,
`io.github.lutzseverino.cardo.common.model`, and
`io.github.lutzseverino.cardo.common.validation` packages without bringing persistence, a
validation implementation, or server exception handling into consumers.

The full common artifact remains the compatibility aggregate for services that need the other
shared primitives.

Public surface:

- `io.github.lutzseverino.cardo.common.api.ApiError`
- `io.github.lutzseverino.cardo.common.api.ApiException`
- `io.github.lutzseverino.cardo.common.api.ApiClientErrors`
- `io.github.lutzseverino.cardo.common.model.FieldUpdate`
- `io.github.lutzseverino.cardo.common.validation.FieldUpdateValueExtractor`
