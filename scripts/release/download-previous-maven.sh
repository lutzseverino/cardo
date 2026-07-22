#!/usr/bin/env bash

set -Eeuo pipefail

root_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root_directory"

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <previous-release-manifest> <repository-output>" >&2
  exit 2
fi

manifest=$1
output=$(mkdir -p "$2" && cd "$2" && pwd)
version=$(jq --exit-status --raw-output .version "$manifest")
group_path=io/github/lutzseverino/cardo
while IFS= read -r artifact; do
  url=$(jq --exit-status --raw-output --arg artifact "$artifact" \
    '.maven.artifacts[] | select(.artifactId == $artifact) | .url' "$manifest")
  expected=$(jq --exit-status --raw-output --arg artifact "$artifact" \
    '.maven.artifacts[] | select(.artifactId == $artifact) | .sha256' "$manifest")
  destination="$output/$group_path/$artifact/$version/$artifact-$version.jar"
  mkdir -p "$(dirname "$destination")"
  curl --fail --location --silent --show-error "$url" --output "$destination"
  actual=$(shasum -a 256 "$destination" | awk '{print $1}')
  [[ $actual == "$expected" ]] \
    || { echo "$artifact:$version differs from its release manifest" >&2; exit 1; }
done <release/supported-artifacts.txt
