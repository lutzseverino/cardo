#!/usr/bin/env bash

set -Eeuo pipefail

root_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root_directory"

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <validated-candidate-image-directory> <rebuilt-image-directory>" >&2
  exit 2
fi

candidate=$(cd "$1" && pwd)
rebuilt=$(cd "$2" && pwd)

for directory in "$candidate" "$rebuilt"; do
  jq --exit-status '
    (.images | length) == 3 and
    ([.images[].service] | sort) == ["billing", "identity", "invite"] and
    ([.images[].service] | unique | length) == 3
  ' "$directory/images.json" >/dev/null
  for service in identity invite billing; do
    sbom="$directory/$service-image.cyclonedx.json"
    [[ -f $sbom ]] || { echo "missing $service image inventory in $directory" >&2; exit 1; }
    expected=$(jq --exit-status --raw-output --arg service "$service" \
      '.images[] | select(.service == $service) | .sbomSha256' "$directory/images.json")
    actual=$(shasum -a 256 "$sbom" | awk '{print $1}')
    [[ $actual == "$expected" ]] \
      || { echo "$service image inventory differs from $directory/images.json" >&2; exit 1; }
  done
done

candidate_identity=$(jq --sort-keys '.images | sort_by(.service)' "$candidate/images.json")
rebuilt_identity=$(jq --sort-keys '.images | sort_by(.service)' "$rebuilt/images.json")
[[ $candidate_identity == "$rebuilt_identity" ]] || {
  diff --unified \
    <(printf '%s\n' "$candidate_identity") \
    <(printf '%s\n' "$rebuilt_identity") >&2 || true
  echo "publish rebuild differs from the validated image candidate; refusing registry writes" >&2
  exit 1
}

for service in identity invite billing; do
  cmp --silent \
    "$candidate/$service-image.cyclonedx.json" \
    "$rebuilt/$service-image.cyclonedx.json" \
    || { echo "$service image inventory is not reproducible" >&2; exit 1; }
done

echo "publish rebuild exactly matches all validated image IDs and inventories"
