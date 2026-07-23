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
anonymous_error=$(mktemp "${TMPDIR:-/tmp}/cardo-anonymous-pull.XXXXXX")
trap 'rm -f "$anonymous_error"; docker logout ghcr.io >/dev/null 2>&1 || true' EXIT
for service in identity invite billing; do
  reference=$(jq --exit-status --raw-output --arg service "$service" \
    '.images[] | select(.service == $service) | .reference' "$manifest")
  [[ $reference == ghcr.io/lutzseverino/cardo/$service@sha256:* ]] \
    || { echo "manifest lacks immutable $service image reference" >&2; exit 1; }
  if docker pull "$reference" >"$anonymous_error" 2>&1; then
    echo "anonymous pull unexpectedly succeeded for private $service image" >&2
    exit 1
  fi
done

printf '%s' "$GHCR_PULL_TOKEN" \
  | docker login ghcr.io --username "$registry_username" --password-stdin >/dev/null
for service in identity invite billing; do
  reference=$(jq --exit-status --raw-output --arg service "$service" \
    '.images[] | select(.service == $service) | .reference' "$manifest")
  docker pull "$reference"
done

echo "anonymous image rejection and authenticated digest pulls passed for $version"
