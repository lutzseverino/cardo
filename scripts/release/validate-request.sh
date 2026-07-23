#!/usr/bin/env bash

set -Eeuo pipefail

published_release=false
if [[ ${1:-} == --published-release ]]; then
  [[ $# -eq 5 ]] || {
    echo "usage: $0 --published-release <version> <full-source-revision> <release-manifest> <central-bundle>" >&2
    exit 2
  }
  published_release=true
  shift
elif [[ $# -ne 2 && $# -ne 4 && $# -ne 5 ]]; then
  echo "usage: $0 <version> <full-source-revision> [<anchor-manifest> <anchor-bundle> [<candidate-bundle>]]" >&2
  exit 2
fi

root_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root_directory"
version=$1
source_revision=$2
anchor_manifest=${3:-}
anchor_bundle=${4:-}
candidate_bundle=${5:-}
scripts/release/validate-version.py "$version"
[[ $source_revision =~ ^[0-9a-f]{40}$ ]] \
  || { echo "revision must be a full lowercase Git SHA" >&2; exit 1; }
if [[ $published_release == false ]]; then
  [[ $(git rev-parse HEAD) == "$source_revision" ]] \
    || { echo "checked-out revision differs from release request" >&2; exit 1; }
  git fetch --no-tags origin main
fi

if [[ -n $anchor_manifest ]]; then
  [[ -f $anchor_manifest && -f $anchor_bundle ]] \
    || { echo "release resume requires both the preserved manifest and Central bundle" >&2; exit 1; }
  python3 - "$version" "$source_revision" "$anchor_manifest" "$anchor_bundle" "$candidate_bundle" <<'PY'
import hashlib
import json
import pathlib
import re
import sys
import zipfile

version, revision = sys.argv[1:3]
manifest_path = pathlib.Path(sys.argv[3])
anchor_bundle = pathlib.Path(sys.argv[4])
candidate_bundle = pathlib.Path(sys.argv[5]) if sys.argv[5] else None

try:
    manifest = json.loads(manifest_path.read_text())
except (json.JSONDecodeError, OSError) as error:
    raise SystemExit(f"cannot read preserved release manifest: {error}")
if not isinstance(manifest, dict) or manifest.get("schemaVersion") != 1:
    raise SystemExit("preserved release manifest must use schemaVersion 1")
if manifest.get("version") != version:
    raise SystemExit("preserved release manifest version differs from release request")
if manifest.get("tag") != f"v{version}":
    raise SystemExit("preserved release manifest tag differs from release request")
if manifest.get("sourceRevision") != revision:
    raise SystemExit("preserved release manifest revision differs from release request")
central_bundle = manifest.get("maven", {}).get("centralBundle", {})
expected_digest = central_bundle.get("sha256")
if central_bundle.get("asset") != "central-bundle.zip" or not isinstance(expected_digest, str):
    raise SystemExit("preserved release manifest lacks the Central bundle identity")
if not re.fullmatch(r"[0-9a-f]{64}", expected_digest):
    raise SystemExit("preserved Central bundle digest is invalid")
actual_digest = hashlib.sha256(anchor_bundle.read_bytes()).hexdigest()
if actual_digest != expected_digest:
    raise SystemExit("preserved Central bundle differs from its release manifest")

if candidate_bundle:
    ignored_suffixes = (".asc", ".md5", ".sha1", ".sha256", ".sha512")

    def core_entries(path):
        try:
            with zipfile.ZipFile(path) as archive:
                names = [name for name in archive.namelist() if not name.endswith("/")]
                if not names or len(names) != len(set(names)):
                    raise SystemExit(f"Central bundle {path} has empty or duplicate payload entries")
                return {
                    name: hashlib.sha256(archive.read(name)).hexdigest()
                    for name in names
                    if not name.endswith(ignored_suffixes)
                }
        except (OSError, zipfile.BadZipFile) as error:
            raise SystemExit(f"cannot read Central bundle {path}: {error}")

    if core_entries(anchor_bundle) != core_entries(candidate_bundle):
        raise SystemExit("preserved Central bundle core differs from the current candidate")
PY
else
  [[ $(git rev-parse origin/main) == "$source_revision" ]] \
    || { echo "release revision is not the exact current origin/main" >&2; exit 1; }
fi

if [[ $published_release == true ]]; then
  tag_revision=$(git ls-remote --tags origin "refs/tags/v$version^{}" | awk '{print $1}')
  [[ -n $tag_revision && $tag_revision == "$source_revision" ]] \
    || { echo "v$version does not identify the requested source revision through an annotated remote tag" >&2; exit 1; }
elif git ls-remote --exit-code --tags origin "refs/tags/v$version" >/dev/null 2>&1; then
  tag_revision=$(git ls-remote --tags origin "refs/tags/v$version^{}" | awk '{print $1}')
  [[ -n $tag_revision && $tag_revision == "$source_revision" ]] \
    || { echo "v$version already identifies a different source revision" >&2; exit 1; }
fi

if [[ $published_release == true ]]; then
  echo "published release v$version identifies exact revision $source_revision"
elif [[ -n $anchor_manifest ]]; then
  echo "release resume is anchored to exact revision $source_revision"
else
  echo "release request identifies exact main revision $source_revision"
fi
