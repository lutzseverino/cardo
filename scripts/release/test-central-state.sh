#!/usr/bin/env bash

set -Eeuo pipefail

if [[ ${0##*/} == curl ]]; then
  output=
  url=
  while [[ $# -gt 0 ]]; do
    case $1 in
      --output)
        output=$2
        shift 2
        ;;
      --write-out)
        shift 2
        ;;
      --location|--silent|--show-error)
        shift
        ;;
      *)
        url=$1
        shift
        ;;
    esac
  done
  relative=${url#https://repo1.maven.org/maven2/io/github/lutzseverino/cardo/}
  source_file="$MOCK_CENTRAL_DIRECTORY/$relative"
  if [[ -f $source_file ]]; then
    cp "$source_file" "$output"
    printf 200
  else
    : >"$output"
    printf 404
  fi
  exit
fi

root_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root_directory"
temporary_directory=$(mktemp -d "${TMPDIR:-/tmp}/cardo-central-state-test.XXXXXX")
trap 'rm -rf "$temporary_directory"' EXIT

repository="$temporary_directory/maven-repository"
release_root="$repository/io/github/lutzseverino/cardo"
central="$temporary_directory/central"
mock_bin="$temporary_directory/bin"
version=1.2.3
mkdir -p "$release_root/cardo/$version" "$release_root/example/$version" "$central" "$mock_bin"
printf 'root-pom\n' >"$release_root/cardo/$version/cardo-$version.pom"
printf 'binary\n' >"$release_root/example/$version/example-$version.jar"
printf 'sources\n' >"$release_root/example/$version/example-$version-sources.jar"
printf 'javadoc\n' >"$release_root/example/$version/example-$version-javadoc.jar"
printf 'inventory\n' >"$release_root/example/$version/example-$version-cyclonedx.json"
printf 'signature\n' >"$release_root/example/$version/example-$version.jar.asc"
printf 'ignored checksum\n' >"$release_root/example/$version/example-$version.jar.sha256"

ln -s "$root_directory/scripts/release/test-central-state.sh" "$mock_bin/curl"

check_state() {
  local expected=$1
  local remote_directory=$2
  local actual
  actual=$(PATH="$mock_bin:$PATH" MOCK_CENTRAL_DIRECTORY="$remote_directory" \
    scripts/release/check-central-state.sh "$version" "$repository")
  [[ $actual == "$expected" ]] \
    || { echo "expected Central state $expected, got $actual" >&2; exit 1; }
}

expect_failure() {
  local label=$1
  local remote_directory=$2
  if PATH="$mock_bin:$PATH" MOCK_CENTRAL_DIRECTORY="$remote_directory" \
    scripts/release/check-central-state.sh "$version" "$repository" >/dev/null 2>&1; then
    echo "Central state fixture unexpectedly accepted $label" >&2
    exit 1
  fi
}

absent="$central/absent"
identical="$central/identical"
lone_secondary="$central/lone-secondary"
lone_signature="$central/lone-signature"
mixed="$central/mixed"
mismatch="$central/mismatch"
mkdir -p "$absent" "$identical" "$lone_secondary" "$lone_signature" "$mixed" "$mismatch"
cp -R "$release_root/." "$identical/"
printf 'different ignored checksum\n' \
  >"$identical/example/$version/example-$version.jar.sha256"
mkdir -p "$lone_secondary/example/$version" "$lone_signature/example/$version"
cp "$release_root/example/$version/example-$version-sources.jar" \
  "$lone_secondary/example/$version/"
cp "$release_root/example/$version/example-$version.jar.asc" \
  "$lone_signature/example/$version/"
mkdir -p "$mixed/cardo/$version"
cp "$release_root/cardo/$version/cardo-$version.pom" "$mixed/cardo/$version/"
cp -R "$release_root/." "$mismatch/"
printf 'different binary\n' >"$mismatch/example/$version/example-$version.jar"

check_state absent "$absent"
check_state published "$identical"
expect_failure lone-secondary "$lone_secondary"
expect_failure lone-signature "$lone_signature"
expect_failure mixed "$mixed"
expect_failure mismatch "$mismatch"

echo "Central state fixtures passed"
