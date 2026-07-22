#!/usr/bin/env bash

set -Eeuo pipefail

root_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root_directory"

temporary_directory=$(mktemp -d "${TMPDIR:-/tmp}/cardo-image-rebuild.XXXXXX")
trap 'rm -rf "$temporary_directory"' EXIT
candidate="$temporary_directory/candidate"
rebuilt="$temporary_directory/rebuilt"
mkdir -p "$candidate" "$rebuilt"

for service in identity invite billing; do
  printf '{"bomFormat":"CycloneDX","components":[{"name":"%s"}]}\n' "$service" \
    >"$candidate/$service-image.cyclonedx.json"
done

python3 - "$candidate" <<'PY'
import hashlib
import json
import pathlib
import sys

directory = pathlib.Path(sys.argv[1])
images = []
for service in ("identity", "invite", "billing"):
    sbom = directory / f"{service}-image.cyclonedx.json"
    images.append({
        "service": service,
        "name": f"ghcr.io/lutzseverino/cardo/{service}",
        "tag": "1.2.3",
        "localContentId": f"sha256:{service}",
        "sbom": sbom.name,
        "sbomSha256": hashlib.sha256(sbom.read_bytes()).hexdigest(),
    })
(directory / "images.json").write_text(json.dumps({"images": images}) + "\n")
PY
cp -R "$candidate/." "$rebuilt/"

scripts/release/verify-image-rebuild.sh "$candidate" "$rebuilt"

jq '(.images[] | select(.service == "invite") | .localContentId) = "sha256:different"' \
  "$rebuilt/images.json" >"$rebuilt/images.changed.json"
mv "$rebuilt/images.changed.json" "$rebuilt/images.json"
if scripts/release/verify-image-rebuild.sh "$candidate" "$rebuilt" >/dev/null 2>&1; then
  echo "different image content ID was accepted" >&2
  exit 1
fi

cp "$candidate/images.json" "$rebuilt/images.json"
printf 'different\n' >"$rebuilt/invite-image.cyclonedx.json"
if scripts/release/verify-image-rebuild.sh "$candidate" "$rebuilt" >/dev/null 2>&1; then
  echo "different image inventory was accepted" >&2
  exit 1
fi

echo "image rebuild fixtures passed"
