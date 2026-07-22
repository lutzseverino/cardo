#!/usr/bin/env bash

set -Eeuo pipefail

root_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root_directory"

temporary_directory=$(mktemp -d "${TMPDIR:-/tmp}/cardo-compatibility.XXXXXX")
trap 'rm -rf "$temporary_directory"' EXIT
mkdir -p "$temporary_directory/old/example" "$temporary_directory/additive/example" "$temporary_directory/breaking/example"

printf '%s\n' 'package example; public class Contract { public String value() { return "old"; } }' \
  >"$temporary_directory/old/example/Contract.java"
printf '%s\n' 'package example; public class Contract { public String value() { return "new"; } public int count() { return 1; } }' \
  >"$temporary_directory/additive/example/Contract.java"
printf '%s\n' 'package example; public class Contract { public long value() { return 1; } }' \
  >"$temporary_directory/breaking/example/Contract.java"

for fixture in old additive breaking; do
  javac -d "$temporary_directory/$fixture/classes" "$temporary_directory/$fixture/example/Contract.java"
  jar --create --file "$temporary_directory/$fixture.jar" -C "$temporary_directory/$fixture/classes" .
done

scripts/release/run-japicmp.sh \
  "$temporary_directory/old.jar" "$temporary_directory/additive.jar" "$temporary_directory/additive-report"

set +e
scripts/release/run-japicmp.sh \
  "$temporary_directory/old.jar" "$temporary_directory/breaking.jar" "$temporary_directory/breaking-report"
status=$?
set -e
[[ $status -eq 10 ]] || { echo "breaking Java fixture returned $status instead of 10" >&2; exit 1; }

echo "Java compatibility fixtures passed"
