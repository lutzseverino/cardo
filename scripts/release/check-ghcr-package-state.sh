#!/usr/bin/env bash

set -Eeuo pipefail

if [[ $# -eq 0 ]]; then
  allow_absent=true
  services=(identity invite billing)
elif [[ $# -eq 2 && $1 == --allow-absent ]]; then
  allow_absent=true
  services=("$2")
elif [[ $# -eq 2 && $1 == --require-private ]]; then
  allow_absent=false
  services=("$2")
else
  echo "usage: $0 [--allow-absent|--require-private <identity|invite|billing>]" >&2
  exit 2
fi

: "${GHCR_PUBLISH_TOKEN:?GHCR_PUBLISH_TOKEN is required to inspect GHCR package state}"
command -v gh >/dev/null || { echo "gh is required" >&2; exit 1; }
command -v jq >/dev/null || { echo "jq is required" >&2; exit 1; }

response=$(mktemp "${TMPDIR:-/tmp}/cardo-package-state.XXXXXX")
error=$(mktemp "${TMPDIR:-/tmp}/cardo-package-state-error.XXXXXX")
trap 'rm -f "$response" "$error"' EXIT

for service in "${services[@]}"; do
  case $service in
    identity|invite|billing) ;;
    *)
      echo "unknown Cardo runtime service: $service" >&2
      exit 2
      ;;
  esac

  : >"$response"
  : >"$error"
  if GH_TOKEN="$GHCR_PUBLISH_TOKEN" gh api \
    "/users/lutzseverino/packages/container/cardo%2F$service" \
    >"$response" 2>"$error"; then
    if ! jq --exit-status '
      type == "object" and
      has("visibility") and (.visibility | type == "string") and
      (
        (has("repository") | not) or
        .repository == null or
        (
          (.repository | type == "object") and
          (.repository.full_name | type == "string")
        )
      )
    ' "$response" >/dev/null; then
      echo "GHCR returned unknown package state for cardo/$service" >&2
      exit 1
    fi
    visibility=$(jq --raw-output .visibility "$response")
    repository=$(jq --raw-output '.repository.full_name? // empty' "$response")
    [[ $visibility == private ]] || {
      echo "GHCR package cardo/$service is $visibility; refusing private runtime publication" >&2
      exit 1
    }
    [[ -z $repository ]] || {
      echo "GHCR package cardo/$service is linked to repository $repository" >&2
      exit 1
    }
    echo "GHCR package cardo/$service is private and unlinked"
  elif grep --ignore-case --extended-regexp 'HTTP 404|not found' "$error" >/dev/null; then
    [[ $allow_absent == true ]] || {
      echo "GHCR package cardo/$service is absent after publication" >&2
      exit 1
    }
    echo "GHCR package cardo/$service is absent"
  else
    cat "$error" >&2
    echo "could not prove GHCR package state for cardo/$service" >&2
    exit 1
  fi
done
