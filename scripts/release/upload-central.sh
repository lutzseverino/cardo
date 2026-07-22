#!/usr/bin/env bash

set -Eeuo pipefail

if [[ $# -ne 3 ]]; then
  echo "usage: $0 <version> <central-bundle.zip> <deployment-id-output>" >&2
  exit 2
fi

version=$1
bundle=$2
deployment_output=$3
: "${CENTRAL_TOKEN_USERNAME:?CENTRAL_TOKEN_USERNAME is required}"
: "${CENTRAL_TOKEN_PASSWORD:?CENTRAL_TOKEN_PASSWORD is required}"
[[ -f $bundle ]] || { echo "Central bundle not found: $bundle" >&2; exit 1; }

authorization=$(printf '%s:%s' "$CENTRAL_TOKEN_USERNAME" "$CENTRAL_TOKEN_PASSWORD" | base64 | tr -d '\n')
deployment_id=$(curl --fail --location --silent --show-error \
  --header "Authorization: Bearer $authorization" \
  --form "bundle=@$bundle;type=application/octet-stream" \
  "https://central.sonatype.com/api/v1/publisher/upload?name=cardo-$version&publishingType=USER_MANAGED")
[[ $deployment_id =~ ^[0-9a-f-]{36}$ ]] \
  || { echo "Central returned an invalid deployment id" >&2; exit 1; }
printf '%s\n' "$deployment_id" >"$deployment_output"
echo "Central deployment $deployment_id uploaded for manual publication"
