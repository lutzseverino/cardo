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
  manifest_status=$(curl --disable --silent --show-error \
    --output "$anonymous_response" --write-out '%{http_code}' \
    --header 'Accept: application/vnd.oci.image.manifest.v1+json, application/vnd.docker.distribution.manifest.v2+json' \
    "https://ghcr.io/v2/$repository/manifests/$digest" 2>"$anonymous_error") || curl_status=$?
  if [[ $curl_status -ne 0 ]]; then
    cat "$anonymous_error" >&2
    echo "anonymous GHCR manifest check failed for $service ($digest)" >&2
    exit 1
  fi
  denial_code=$(jq --raw-output \
    'if (.errors | type) == "array" then
       if any(.errors[]; .code == "UNAUTHORIZED") then
         "UNAUTHORIZED"
       else
         [.errors[].code | select(type == "string")] | first // empty
       end
     else
       empty
     end' \
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
