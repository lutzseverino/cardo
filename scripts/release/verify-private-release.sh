#!/usr/bin/env bash

set -Eeuo pipefail

root_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root_directory"

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <final-release-manifest.json> <registry-username>" >&2
  exit 2
fi

manifest=$1
registry_username=$2
: "${GHCR_PULL_TOKEN:?GHCR_PULL_TOKEN is required}"
version=$(jq --exit-status --raw-output .version "$manifest")
scripts/release/verify-consumer.sh "$version" https://repo1.maven.org/maven2

docker logout ghcr.io >/dev/null 2>&1 || true
anonymous_response=$(mktemp "${TMPDIR:-/tmp}/cardo-anonymous-response.XXXXXX")
anonymous_error=$(mktemp "${TMPDIR:-/tmp}/cardo-anonymous-error.XXXXXX")
anonymous_authorization=$(mktemp "${TMPDIR:-/tmp}/cardo-anonymous-authorization.XXXXXX")
chmod 600 "$anonymous_authorization"
trap 'rm -f "$anonymous_response" "$anonymous_error" "$anonymous_authorization"; docker logout ghcr.io >/dev/null 2>&1 || true' EXIT
for service in identity invite billing; do
  reference=$(jq --exit-status --raw-output --arg service "$service" \
    '.images[] | select(.service == $service) | .reference' "$manifest")
  [[ $reference == ghcr.io/lutzseverino/cardo/$service@sha256:* ]] \
    || { echo "manifest lacks immutable $service image reference" >&2; exit 1; }
  repository=${reference#ghcr.io/}
  repository=${repository%@*}
  digest=${reference##*@}

  curl_status=0
  token_status=$(curl --disable --silent --show-error --noproxy '*' \
    --output "$anonymous_response" --write-out '%{http_code}' \
    --get --data-urlencode 'service=ghcr.io' \
    --data-urlencode "scope=repository:lutzseverino/cardo/$service:pull" \
    https://ghcr.io/token 2>"$anonymous_error") || curl_status=$?
  if [[ $curl_status -ne 0 ]]; then
    cat "$anonymous_error" >&2
    echo "anonymous GHCR token request failed for $service ($digest)" >&2
    exit 1
  fi

  if [[ $token_status == 401 ]]; then
    jq --exit-status \
      '(.errors | type) == "array"
       and any(.errors[]; type == "object" and .code == "UNAUTHORIZED")' \
      "$anonymous_response" >/dev/null 2>&1 || {
      echo "anonymous GHCR token request returned HTTP 401 without an UNAUTHORIZED errors entry for $service ($digest)" >&2
      exit 1
    }
    echo "anonymous GHCR token request explicitly denied for $service ($digest)"
    continue
  fi

  [[ $token_status == 200 ]] || {
    echo "anonymous GHCR token request returned HTTP $token_status for $service ($digest); expected 200 or explicit 401 UNAUTHORIZED denial" >&2
    exit 1
  }
  anonymous_token=$(jq --exit-status --raw-output \
    'if (.token | type) == "string" and (.token | length) > 0 then
       .token
     elif (.access_token | type) == "string"
          and (.access_token | length) > 0 then
       .access_token
     else
       error("missing anonymous bearer token")
     end' "$anonymous_response" 2>/dev/null) || {
    echo "anonymous GHCR token request returned HTTP 200 without a nonempty string token for $service ($digest)" >&2
    exit 1
  }
  printf 'Authorization: Bearer %s\n' "$anonymous_token" >"$anonymous_authorization"
  unset anonymous_token

  curl_status=0
  manifest_status=$(curl --disable --silent --show-error --noproxy '*' \
    --output "$anonymous_response" --write-out '%{http_code}' \
    --header "@$anonymous_authorization" \
    --header 'Accept: application/vnd.oci.image.manifest.v1+json, application/vnd.docker.distribution.manifest.v2+json' \
    "https://ghcr.io/v2/$repository/manifests/$digest" 2>"$anonymous_error") || curl_status=$?
  : >"$anonymous_authorization"
  if [[ $curl_status -ne 0 ]]; then
    cat "$anonymous_error" >&2
    echo "anonymous GHCR manifest retry failed for $service ($digest)" >&2
    exit 1
  fi
  if [[ $manifest_status != 401 ]] || ! jq --exit-status \
      '(.errors | type) == "array"
       and any(.errors[]; type == "object" and .code == "UNAUTHORIZED")' \
      "$anonymous_response" >/dev/null 2>&1; then
    echo "anonymous GHCR manifest retry returned HTTP $manifest_status without the required UNAUTHORIZED denial for $service ($digest)" >&2
    exit 1
  fi
  echo "anonymous GHCR bearer access explicitly denied for $service ($digest)"
done

printf '%s' "$GHCR_PULL_TOKEN" \
  | docker login ghcr.io --username "$registry_username" --password-stdin >/dev/null
for service in identity invite billing; do
  reference=$(jq --exit-status --raw-output --arg service "$service" \
    '.images[] | select(.service == $service) | .reference' "$manifest")
  docker pull "$reference"
done

echo "completed anonymous GHCR digest denial and authenticated digest pulls passed for $version"
