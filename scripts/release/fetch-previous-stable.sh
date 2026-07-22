#!/usr/bin/env bash

set -Eeuo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <output-manifest>" >&2
  exit 2
fi

: "${GITHUB_TOKEN:?GITHUB_TOKEN is required}"
output=$1
releases=$(mktemp)
trap 'find "$releases" -type f -delete 2>/dev/null || true' EXIT
curl --fail --location --silent --show-error \
  --header "Authorization: Bearer $GITHUB_TOKEN" \
  --header 'Accept: application/vnd.github+json' \
  'https://api.github.com/repos/lutzseverino/cardo/releases?per_page=100' \
  --output "$releases"
url=$(jq --exit-status --raw-output \
  '[.[] | select(.draft == false and .prerelease == false) | .assets[] | select(.name == "release-manifest.json")][0].browser_download_url // empty' \
  "$releases") || true
if [[ -z $url ]]; then
  : >"$output"
  echo "no previous stable release baseline exists"
else
  curl --fail --location --silent --show-error \
    --header "Authorization: Bearer $GITHUB_TOKEN" "$url" --output "$output"
  jq --exit-status '.schemaVersion == 1 and (.version | type == "string") and (.sourceRevision | test("^[0-9a-f]{40}$"))' \
    "$output" >/dev/null
  echo "selected previous stable Cardo $(jq --raw-output .version "$output")"
fi
