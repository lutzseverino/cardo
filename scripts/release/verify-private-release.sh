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
trap 'rm -f "$anonymous_response" "$anonymous_error"; docker logout ghcr.io >/dev/null 2>&1 || true' EXIT
for service in identity invite billing; do
  reference=$(jq --exit-status --raw-output --arg service "$service" \
    '.images[] | select(.service == $service) | .reference' "$manifest")
  [[ $reference == ghcr.io/lutzseverino/cardo/$service@sha256:* ]] \
    || { echo "manifest lacks immutable $service image reference" >&2; exit 1; }
  repository=${reference#ghcr.io/}
  repository=${repository%@*}
  digest=${reference##*@}

  curl_status=0
  token_status=$(curl --silent --show-error \
    --output "$anonymous_response" --write-out '%{http_code}' \
    --get --data-urlencode 'service=ghcr.io' \
    --data-urlencode "scope=repository:$repository:pull" \
    https://ghcr.io/token 2>"$anonymous_error") || curl_status=$?
  if [[ $curl_status -ne 0 ]]; then
    cat "$anonymous_error" >&2
    echo "could not obtain fresh anonymous GHCR authorization for $service ($digest)" >&2
    exit 1
  fi
  [[ $token_status == 200 ]] || {
    echo "anonymous GHCR authorization returned HTTP $token_status for $service ($digest)" >&2
    exit 1
  }
  anonymous_token=$(jq --exit-status --raw-output \
    '(.token // .access_token) | select(type == "string" and length > 0)' \
    "$anonymous_response") || {
    echo "anonymous GHCR authorization returned no usable token for $service ($digest)" >&2
    exit 1
  }

  curl_status=0
  manifest_status=$(curl --silent --show-error \
    --output "$anonymous_response" --write-out '%{http_code}' \
    --header "Authorization: Bearer $anonymous_token" \
    --header 'Accept: application/vnd.oci.image.manifest.v1+json, application/vnd.docker.distribution.manifest.v2+json' \
    "https://ghcr.io/v2/$repository/manifests/$digest" 2>"$anonymous_error") || curl_status=$?
  unset anonymous_token
  if [[ $curl_status -ne 0 ]]; then
    cat "$anonymous_error" >&2
    echo "anonymous GHCR manifest check failed for $service ($digest)" >&2
    exit 1
  fi
  denial_code=$(jq --raw-output \
    '[.errors[]?.code | select(type == "string")] | first // empty' \
    "$anonymous_response" 2>/dev/null || true)
  [[ $manifest_status == 401 && $denial_code == UNAUTHORIZED ]] || {
    echo "anonymous GHCR manifest check returned HTTP $manifest_status code ${denial_code:-none} for $service ($digest); expected explicit 401 UNAUTHORIZED denial" >&2
    exit 1
  }
  echo "anonymous GHCR access explicitly denied for $service ($digest)"
done

printf '%s' "$GHCR_PULL_TOKEN" \
  | docker login ghcr.io --username "$registry_username" --password-stdin >/dev/null
for service in identity invite billing; do
  reference=$(jq --exit-status --raw-output --arg service "$service" \
    '.images[] | select(.service == $service) | .reference' "$manifest")
  docker pull "$reference"
done

echo "explicit anonymous GHCR digest denial and authenticated digest pulls passed for $version"
