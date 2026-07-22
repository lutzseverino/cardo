#!/usr/bin/env bash

set -Eeuo pipefail

root_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root_directory"

if [[ $# -ne 4 ]]; then
  echo "usage: $0 <previous-release-manifest> <current-bundle-directory> <oasdiff-command> <report-directory>" >&2
  exit 2
fi

manifest=$1
current_directory=$(cd "$2" && pwd)
oasdiff=$3
report_directory=$(mkdir -p "$4" && cd "$4" && pwd)
temporary_directory=$(mktemp -d "${TMPDIR:-/tmp}/cardo-released-openapi.XXXXXX")
trap 'find "$temporary_directory" -type f -delete; rmdir "$temporary_directory"' EXIT

for service in identity invite billing; do
  url=$(jq --exit-status --raw-output --arg service "$service" \
    '.openapiBundles[] | select(.service == $service) | .url' "$manifest")
  expected=$(jq --exit-status --raw-output --arg service "$service" \
    '.openapiBundles[] | select(.service == $service) | .sha256' "$manifest")
  curl --fail --location --silent --show-error "$url" --output "$temporary_directory/$service.yaml"
  actual=$(shasum -a 256 "$temporary_directory/$service.yaml" | awk '{print $1}')
  [[ $actual == "$expected" ]] \
    || { echo "released $service OpenAPI checksum does not match its manifest" >&2; exit 1; }
done

.github/scripts/compare-openapi.sh \
  "$oasdiff" "$temporary_directory" "$current_directory" "$report_directory"
