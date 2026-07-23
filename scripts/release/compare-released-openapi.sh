#!/usr/bin/env bash

set -Eeuo pipefail

root_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root_directory"

if [[ $# -ne 4 ]]; then
  echo "usage: $0 <previous-release-manifest> <current-bundle-directory> <oasdiff-command> <report-directory>" >&2
  exit 2
fi

manifest=$1
current_directory=$(cd "$2" && pwd)
oasdiff=$3
report_directory=$(mkdir -p "$4" && cd "$4" && pwd)
temporary_directory=$(mktemp -d "${TMPDIR:-/tmp}/cardo-released-openapi.XXXXXX")
trap 'rm -rf "$temporary_directory"' EXIT

contract_jar="$temporary_directory/cardo-openapi-contracts.jar"
url=$(jq --exit-status --raw-output '.openapiContracts.url' "$manifest")
expected=$(jq --exit-status --raw-output '.openapiContracts.sha256' "$manifest")
curl --fail --location --silent --show-error "$url" --output "$contract_jar"
actual=$(shasum -a 256 "$contract_jar" | awk '{print $1}')
[[ $actual == "$expected" ]] \
  || { echo "released OpenAPI contracts artifact differs from its manifest" >&2; exit 1; }

previous_source="$temporary_directory/source"
previous_bundles="$temporary_directory/bundles"
mkdir -p "$previous_source"
python3 - "$manifest" "$contract_jar" "$previous_source" <<'PY'
import hashlib
import json
import pathlib
import sys
import zipfile

manifest = json.loads(pathlib.Path(sys.argv[1]).read_text())
output = pathlib.Path(sys.argv[3])
with zipfile.ZipFile(sys.argv[2]) as archive:
    for document in manifest["openapiContracts"]["documents"]:
        entry = document["path"]
        data = archive.read(entry)
        if hashlib.sha256(data).hexdigest() != document["sha256"]:
            raise SystemExit(f"released OpenAPI document differs from its manifest: {entry}")
        relative = pathlib.PurePosixPath(entry).relative_to("META-INF/cardo/openapi")
        destination = output / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
PY
.github/scripts/bundle-openapi.sh "$previous_source" "$previous_bundles" --bundle-only

.github/scripts/compare-openapi.sh \
  "$oasdiff" "$previous_bundles" "$current_directory" "$report_directory"
