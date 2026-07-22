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
scripts/release/validate-version.py "$version"

registry_digest() {
  docker manifest inspect --verbose "$1" \
    | jq --exit-status --raw-output \
      'if type == "array" then
         if length == 1 then .[0].Descriptor.digest else error("multi-platform tag is out of scope") end
       else .Descriptor.digest end'
}

remote_error=$(mktemp "${TMPDIR:-/tmp}/cardo-registry-check.XXXXXX")
trap 'find "$remote_error" -type f -delete 2>/dev/null || true' EXIT

printf '{}\n' >"$digest_output"
for service in identity invite billing; do
  name="ghcr.io/lutzseverino/cardo/$service"
  reference="$name:$version"
  local_id=$(jq --exit-status --raw-output --arg service "$service" \
    '.images[] | select(.service == $service) | .localContentId' "$candidate_manifest")
  [[ $(docker image inspect --format '{{.Id}}' "$reference") == "$local_id" ]] \
    || { echo "local $reference bytes differ from the validated candidate" >&2; exit 1; }

  : >"$remote_error"
  if remote=$(registry_digest "$reference" 2>"$remote_error"); then
    [[ -n $previous_manifest && -f $previous_manifest ]] \
      || { echo "$reference already exists but no prior release manifest proves its digest" >&2; exit 1; }
    expected=$(jq --exit-status --raw-output --arg service "$service" \
      '.images[] | select(.service == $service) | .digest' "$previous_manifest")
    [[ $remote == "$expected" ]] \
      || { echo "$reference digest $remote differs from recorded $expected" >&2; exit 1; }
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
  temporary_output="$digest_output.tmp"
  jq --arg service "$service" --arg digest "$digest" \
    '. + {($service): $digest}' "$digest_output" >"$temporary_output"
  mv "$temporary_output" "$digest_output"
done

echo "published or verified exact GHCR image tags for $version"
