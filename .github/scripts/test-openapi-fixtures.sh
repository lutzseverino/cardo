#!/usr/bin/env bash
set -euo pipefail

if [[ $# -gt 1 ]]; then
  echo "usage: $0 [oasdiff-command]" >&2
  exit 2
fi

oasdiff_command=${1:-oasdiff}
fixtures=".github/openapi-fixtures"
work_directory=$(mktemp -d)
trap 'rm -rf "$work_directory"' EXIT

npx --no-install redocly bundle "$fixtures/base.yaml" --output "$work_directory/base.yaml"
npx --no-install redocly bundle "$fixtures/additive.yaml" --output "$work_directory/additive.yaml"
npx --no-install redocly bundle "$fixtures/breaking.yaml" --output "$work_directory/breaking.yaml"

if npx --no-install redocly bundle \
  "$fixtures/invalid-ref.yaml" \
  --output "$work_directory/invalid-ref.yaml"; then
  echo "invalid-ref fixture unexpectedly bundled" >&2
  exit 1
fi

"$oasdiff_command" breaking --fail-on ERR "$work_directory/base.yaml" "$work_directory/additive.yaml"
if "$oasdiff_command" breaking --fail-on ERR \
  "$work_directory/base.yaml" "$work_directory/breaking.yaml"; then
  echo "breaking fixture unexpectedly passed compatibility" >&2
  exit 1
fi

echo "OpenAPI compatibility fixtures passed."
