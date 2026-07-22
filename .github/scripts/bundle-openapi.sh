#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 || $# -gt 3 || ( $# -eq 3 && $3 != "--bundle-only" ) ]]; then
  echo "usage: $0 <repository-root> <output-directory> [--bundle-only]" >&2
  exit 2
fi

repository_root=$(cd "$1" && pwd)
output_directory=$2
bundle_only=${3:-}
mkdir -p "$output_directory"

specifications=(
  "common/openapi/errors.yaml:common-errors.yaml"
  "identity/openapi/identity.yaml:identity.yaml"
  "invite/openapi/invite.yaml:invite.yaml"
  "billing/openapi/billing.yaml:billing.yaml"
)

for specification in "${specifications[@]}"; do
  source_path=${specification%%:*}
  output_name=${specification##*:}
  if [[ $bundle_only != "--bundle-only" ]]; then
    npx --no-install redocly lint \
      --config "$(pwd)/.redocly.yaml" \
      "$repository_root/$source_path"
  fi
  npx --no-install redocly bundle \
    --config "$(pwd)/.redocly.yaml" \
    "$repository_root/$source_path" \
    --output "$output_directory/$output_name"
done
