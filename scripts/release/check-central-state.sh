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
present=0
absent=0
components=("cardo:pom" "cardo-bom:pom")
while IFS= read -r artifact; do
  components+=("$artifact:jar")
done <release/supported-artifacts.txt

for component in "${components[@]}"; do
  artifact=${component%%:*}
  extension=${component##*:}
  relative="$artifact/$version/$artifact-$version.$extension"
  expected="$repository/io/github/lutzseverino/cardo/$relative"
  temporary=$(mktemp)
  status=$(curl --location --silent --show-error --output "$temporary" --write-out '%{http_code}' \
    "$base_url/$relative")
  if [[ $status == 404 ]]; then
    absent=$((absent + 1))
  elif [[ $status == 200 ]]; then
    present=$((present + 1))
    cmp --silent "$expected" "$temporary" \
      || { rm -f "$temporary"; echo "Central already contains different bytes for $artifact:$version" >&2; exit 1; }
  else
    rm -f "$temporary"
    echo "Central state check failed with HTTP $status for $artifact:$version" >&2
    exit 1
  fi
  rm -f "$temporary"
done

if [[ $present -gt 0 && $absent -gt 0 ]]; then
  echo "Central contains a partial release for $version; immutable recovery requires a new version" >&2
  exit 1
elif [[ $present -eq ${#components[@]} ]]; then
  while IFS= read -r expected; do
    relative=${expected#"$repository/io/github/lutzseverino/cardo/"}
    temporary=$(mktemp)
    status=$(curl --location --silent --show-error --output "$temporary" --write-out '%{http_code}' \
      "$base_url/$relative")
    if [[ $status != 200 ]] || ! cmp --silent "$expected" "$temporary"; then
      rm -f "$temporary"
      echo "Central release file is absent or differs: $relative" >&2
      exit 1
    fi
    rm -f "$temporary"
  done < <(find "$repository/io/github/lutzseverino/cardo" -type f \
    ! -name '*.md5' ! -name '*.sha1' ! -name '*.sha256' ! -name '*.sha512' | sort)
  echo published
else
  echo absent
fi
