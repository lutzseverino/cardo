#!/usr/bin/env bash

set -Eeuo pipefail

if [[ $# -lt 2 ]]; then
  echo "usage: $0 <output-file> <asset>..." >&2
  exit 2
fi

output=$1
shift
: >"$output"
for asset in "$@"; do
  [[ -f $asset ]] || { echo "release asset not found: $asset" >&2; exit 1; }
  checksum=$(shasum -a 256 "$asset" | awk '{print $1}')
  printf '%s  %s\n' "$checksum" "$(basename "$asset")" >>"$output"
done
