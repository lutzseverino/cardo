#!/usr/bin/env bash

set -Eeuo pipefail

root_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root_directory"

if [[ $# -lt 3 || $# -gt 4 ]]; then
  echo "usage: $0 <version> <candidate-images.json> <digest-output.json> [previous-release-manifest.json]" >&2
  exit 2
fi

version=$1
candidate_manifest=$2
digest_output=$3
previous_manifest=${4:-}
command -v docker >/dev/null || { echo "docker is required" >&2; exit 1; }
command -v jq >/dev/null || { echo "jq is required" >&2; exit 1; }
: "${GHCR_PUBLISH_TOKEN:?GHCR_PUBLISH_TOKEN is required to publish GHCR images}"
scripts/release/validate-version.py "$version"

registry_digest() {
  docker manifest inspect --verbose "$1" \
    | jq --exit-status --raw-output \
      'if type == "array" then
         if length == 1 then .[0].Descriptor.digest else error("multi-platform tag is out of scope") end
       else .Descriptor.digest end'
}

record_digest() {
  local service=$1
  local digest=$2
  local temporary_output="$digest_output.tmp"
  jq --arg service "$service" --arg digest "$digest" \
    '. + {($service): $digest}' "$digest_output" >"$temporary_output"
  mv "$temporary_output" "$digest_output"
  echo "recorded $service GHCR digest $digest"
}

remote_error=$(mktemp "${TMPDIR:-/tmp}/cardo-registry-check.XXXXXX")
trap 'docker logout ghcr.io >/dev/null 2>&1 || true; rm -f "$remote_error"' EXIT

printf '{}\n' >"$digest_output"
for service in identity invite billing; do
  name="ghcr.io/lutzseverino/cardo/$service"
  reference="$name:$version"
  printf '%s' "$GHCR_PUBLISH_TOKEN" \
    | docker login ghcr.io --username lutzseverino --password-stdin >/dev/null
  local_id=$(jq --exit-status --raw-output --arg service "$service" \
    '.images[] | select(.service == $service) | .localContentId' "$candidate_manifest")
  [[ $(docker image inspect --format '{{.Id}}' "$reference") == "$local_id" ]] \
    || { echo "local $reference bytes differ from the validated candidate" >&2; exit 1; }

  scripts/release/check-ghcr-package-state.sh --allow-absent "$service"

  : >"$remote_error"
  if remote=$(registry_digest "$reference" 2>"$remote_error"); then
    expected=
    if [[ -n $previous_manifest && -f $previous_manifest ]]; then
      expected=$(jq --raw-output --arg service "$service" \
        '.images[] | select(.service == $service) | .digest // empty' "$previous_manifest")
    fi
    if [[ -n $expected ]]; then
      [[ $remote == "$expected" ]] \
        || { echo "$reference digest $remote differs from recorded $expected" >&2; exit 1; }
    else
      docker pull "$reference" >/dev/null
      pulled_id=$(docker image inspect --format '{{.Id}}' "$reference")
      [[ $pulled_id == "$local_id" ]] || {
        echo "$reference already exists with content ID $pulled_id, not validated candidate $local_id" >&2
        exit 1
      }
    fi
    digest=$remote
  elif grep --ignore-case --extended-regexp 'manifest unknown|no such manifest|not found' \
    "$remote_error" >/dev/null; then
    docker push "$reference"
    digest=$(registry_digest "$reference" 2>"$remote_error")
  else
    cat "$remote_error" >&2
    echo "could not prove that $reference is absent; refusing to push" >&2
    exit 1
  fi
  [[ $digest =~ ^sha256:[0-9a-f]{64}$ ]] \
    || { echo "registry returned an invalid digest for $reference" >&2; exit 1; }
  record_digest "$service" "$digest"
  scripts/release/check-ghcr-package-state.sh --require-private "$service"
  docker logout ghcr.io >/dev/null
done

echo "published or verified private GHCR image tags for $version"
