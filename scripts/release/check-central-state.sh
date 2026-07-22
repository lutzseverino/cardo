#!/usr/bin/env bash

set -Eeuo pipefail

root_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root_directory"

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <version> <staged-maven-repository>" >&2
  exit 2
fi

version=$1
repository=$(cd "$2" && pwd)
base_url=https://repo1.maven.org/maven2/io/github/lutzseverino/cardo
release_root="$repository/io/github/lutzseverino/cardo"
[[ -d $release_root ]] || { echo "staged Cardo Maven repository is missing" >&2; exit 1; }

temporary_directory=$(mktemp -d "${TMPDIR:-/tmp}/cardo-central-state.XXXXXX")
trap 'rm -rf "$temporary_directory"' EXIT
present=0
absent=0
release_files=()
while IFS= read -r expected; do
  release_files+=("$expected")
done < <(find "$release_root" -type f \
  ! -name '*.md5' ! -name '*.sha1' ! -name '*.sha256' ! -name '*.sha512' | sort)
[[ ${#release_files[@]} -gt 0 ]] \
  || { echo "staged Cardo Maven repository contains no release files" >&2; exit 1; }

for index in "${!release_files[@]}"; do
  expected=${release_files[$index]}
  relative=${expected#"$release_root/"}
  temporary="$temporary_directory/$index"
  status=$(curl --location --silent --show-error --output "$temporary" --write-out '%{http_code}' \
    "$base_url/$relative")
  if [[ $status == 404 ]]; then
    absent=$((absent + 1))
  elif [[ $status == 200 ]]; then
    present=$((present + 1))
    cmp --silent "$expected" "$temporary" \
      || { echo "Central already contains different bytes for $relative" >&2; exit 1; }
  else
    echo "Central state check failed with HTTP $status for $relative" >&2
    exit 1
  fi
done

if [[ $present -gt 0 && $absent -gt 0 ]]; then
  echo "Central contains a partial release for $version; immutable recovery requires a new version" >&2
  exit 1
elif [[ $present -eq ${#release_files[@]} ]]; then
  echo published
else
  echo absent
fi
