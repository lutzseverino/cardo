#!/usr/bin/env bash

set -Eeuo pipefail

root_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root_directory"

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <final-release-manifest.json>" >&2
  exit 2
fi

manifest=$1
version=$(jq --exit-status --raw-output .version "$manifest")
scripts/release/verify-consumer.sh "$version" https://repo1.maven.org/maven2

docker logout ghcr.io >/dev/null 2>&1 || true
for service in identity invite billing; do
  reference=$(jq --exit-status --raw-output --arg service "$service" \
    '.images[] | select(.service == $service) | .reference' "$manifest")
  [[ $reference == ghcr.io/lutzseverino/cardo/$service@sha256:* ]] \
    || { echo "manifest lacks immutable $service image reference" >&2; exit 1; }
  docker pull "$reference"
done

echo "anonymous Maven Central and GHCR resolution passed for $version"
