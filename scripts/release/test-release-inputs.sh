#!/usr/bin/env bash

set -Eeuo pipefail

root_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root_directory"

for version in 0.1.0 1.2.3-rc.1 2.0.0-alpha.1-x; do
  scripts/release/validate-version.py "$version"
done
for version in 01.0.0 1.0.0-01 1.0.0-rc. 1.0.0+build 1.0.0-SNAPSHOT 1.0.0-rc.SNAPSHOT; do
  if scripts/release/validate-version.py "$version" >/dev/null 2>&1; then
    echo "invalid release version was accepted: $version" >&2
    exit 1
  fi
done

echo "release input fixtures passed"
