#!/usr/bin/env bash

set -Eeuo pipefail

root_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root_directory"

if [[ $# -ne 6 ]]; then
  echo "usage: $0 <old-repository> <old-version> <new-repository> <new-version> <previous-revision> <report-directory>" >&2
  exit 2
fi

old_repository=$(cd "$1" && pwd)
old_version=$2
new_repository=$(cd "$3" && pwd)
new_version=$4
previous_revision=$5
report_directory=$(mkdir -p "$6" && cd "$6" && pwd)
current_revision=$(git rev-parse HEAD)
group_path=io/github/lutzseverino/cardo
breaking=0

while IFS= read -r artifact; do
  [[ $artifact == cardo-openapi-contracts ]] && continue
  old_jar="$old_repository/$group_path/$artifact/$old_version/$artifact-$old_version.jar"
  new_jar="$new_repository/$group_path/$artifact/$new_version/$artifact-$new_version.jar"
  set +e
  scripts/release/run-japicmp.sh "$old_jar" "$new_jar" "$report_directory/$artifact"
  status=$?
  set -e
  if [[ $status -eq 10 ]]; then
    breaking=1
  elif [[ $status -ne 0 ]]; then
    exit "$status"
  fi
done <release/supported-artifacts.txt

if [[ $breaking -eq 1 ]]; then
  scripts/release/check-break-policy.py Java-Migration "$previous_revision" "$current_revision"
fi
