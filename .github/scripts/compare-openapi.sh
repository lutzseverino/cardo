#!/usr/bin/env bash
set -uo pipefail

if [[ $# -ne 4 ]]; then
  echo "usage: $0 <oasdiff-command> <base-directory> <current-directory> <report-directory>" >&2
  exit 2
fi

oasdiff_command=$1
base_directory=$2
current_directory=$3
report_directory=$4
mkdir -p "$report_directory"

breaking=0
for service in identity invite billing; do
  report="$report_directory/$service.txt"
  "$oasdiff_command" breaking --fail-on ERR \
    "$base_directory/$service.yaml" \
    "$current_directory/$service.yaml" >"$report" 2>&1
  status=$?
  echo "OpenAPI compatibility report: $service"
  sed -n '1,400p' "$report"
  if [[ $status -eq 1 ]]; then
    breaking=1
  elif [[ $status -ne 0 ]]; then
    echo "oasdiff failed for $service with exit status $status" >&2
    exit "$status"
  fi
done

if [[ $breaking -eq 1 ]]; then
  exit 10
fi
