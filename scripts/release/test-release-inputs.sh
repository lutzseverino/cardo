#!/usr/bin/env bash

set -Eeuo pipefail

root_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root_directory"

for version in 0.1.0 1.2.3-rc.1 2.0.0-alpha.1-x; do
  scripts/release/validate-version.py "$version"
done
[[ $(scripts/release/validate-version.py 1.2.3-rc.1 --github-prerelease) == true ]] \
  || { echo "SemVer prerelease was not classified as a GitHub prerelease" >&2; exit 1; }
[[ $(scripts/release/validate-version.py 1.2.3 --github-prerelease) == false ]] \
  || { echo "stable SemVer was classified as a GitHub prerelease" >&2; exit 1; }
for version in 01.0.0 1.0.0-01 1.0.0-rc. 1.0.0+build 1.0.0-SNAPSHOT 1.0.0-rc.SNAPSHOT; do
  if scripts/release/validate-version.py "$version" >/dev/null 2>&1; then
    echo "invalid release version was accepted: $version" >&2
    exit 1
  fi
done

python3 - .github/workflows/release.yml <<'PY'
import pathlib
import sys

workflow = pathlib.Path(sys.argv[1]).read_text()
commands = []
lines = workflow.splitlines()
for index, line in enumerate(lines):
    if 'gh release create "v$RELEASE_VERSION"' not in line and (
        'gh release edit "v$RELEASE_VERSION"' not in line
    ):
        continue
    command = line.strip()
    while command.endswith("\\"):
        index += 1
        command = f"{command[:-1].rstrip()} {lines[index].strip()}"
    commands.append(command)

if len(commands) != 4:
    raise SystemExit(
        f"expected four GitHub release create/edit paths, found {len(commands)}"
    )
for command in commands:
    if '--prerelease="$RELEASE_PRERELEASE"' not in command:
        raise SystemExit(f"GitHub release classification is missing from: {command}")
PY

temporary_directory=$(mktemp -d "${TMPDIR:-/tmp}/cardo-release-request.XXXXXX")
trap 'rm -rf "$temporary_directory"' EXIT
repository="$temporary_directory/repository"
remote="$temporary_directory/remote.git"
mkdir -p "$repository/scripts/release"
cp scripts/release/validate-request.sh scripts/release/validate-version.py \
  "$repository/scripts/release/"
chmod +x "$repository/scripts/release/"*
git init --bare --quiet "$remote"
git -C "$repository" init --quiet --initial-branch=main
git -C "$repository" config user.email release-fixture@example.invalid
git -C "$repository" config user.name 'Release fixture'
git -C "$repository" remote add origin "$remote"
git -C "$repository" add scripts
git -C "$repository" commit --quiet --message initial
first_revision=$(git -C "$repository" rev-parse HEAD)
git -C "$repository" push --quiet --set-upstream origin main
(
  cd "$repository"
  scripts/release/validate-request.sh 1.2.3 "$first_revision"
)

python3 - "$temporary_directory" "$first_revision" <<'PY'
import hashlib
import json
import pathlib
import sys
import zipfile

directory = pathlib.Path(sys.argv[1])
revision = sys.argv[2]
anchor = directory / "central-bundle.zip"
candidate = directory / "candidate-bundle.zip"
for path, content in ((anchor, b"same-core"), (candidate, b"same-core")):
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("io/github/lutzseverino/cardo/cardo/1.2.3/cardo-1.2.3.pom", content)
        if path == anchor:
            archive.writestr("io/github/lutzseverino/cardo/cardo/1.2.3/cardo-1.2.3.pom.asc", b"signature")
manifest = {
    "schemaVersion": 1,
    "version": "1.2.3",
    "sourceRevision": revision,
    "maven": {
        "centralBundle": {
            "asset": "central-bundle.zip",
            "sha256": hashlib.sha256(anchor.read_bytes()).hexdigest(),
        }
    },
}
(directory / "release-manifest.json").write_text(json.dumps(manifest))
PY

printf 'main advanced\n' >"$repository/advance"
git -C "$repository" add advance
git -C "$repository" commit --quiet --message advance
second_revision=$(git -C "$repository" rev-parse HEAD)
git -C "$repository" push --quiet origin main
git -C "$repository" checkout --quiet --detach "$first_revision"

expect_failure() {
  if "$@" >/dev/null 2>&1; then
    echo "release request fixture unexpectedly passed: $*" >&2
    exit 1
  fi
}

(
  cd "$repository"
  expect_failure scripts/release/validate-request.sh 1.2.3 "$first_revision"
  scripts/release/validate-request.sh 1.2.3 "$first_revision" \
    "$temporary_directory/release-manifest.json" "$temporary_directory/central-bundle.zip" \
    "$temporary_directory/candidate-bundle.zip"
)

python3 - "$temporary_directory/release-manifest.json" "$second_revision" <<'PY'
import json
import pathlib
import sys
path = pathlib.Path(sys.argv[1])
manifest = json.loads(path.read_text())
manifest["sourceRevision"] = sys.argv[2]
pathlib.Path(str(path) + ".wrong-revision").write_text(json.dumps(manifest))
PY
(
  cd "$repository"
  expect_failure scripts/release/validate-request.sh 1.2.3 "$first_revision" \
    "$temporary_directory/release-manifest.json.wrong-revision" "$temporary_directory/central-bundle.zip"
)

cp "$temporary_directory/central-bundle.zip" "$temporary_directory/wrong-bundle.zip"
printf 'different bytes' >>"$temporary_directory/wrong-bundle.zip"
(
  cd "$repository"
  expect_failure scripts/release/validate-request.sh 1.2.3 "$first_revision" \
    "$temporary_directory/release-manifest.json" "$temporary_directory/wrong-bundle.zip"
)

python3 - "$temporary_directory/wrong-candidate.zip" <<'PY'
import pathlib
import sys
import zipfile
with zipfile.ZipFile(pathlib.Path(sys.argv[1]), "w") as archive:
    archive.writestr("io/github/lutzseverino/cardo/cardo/1.2.3/cardo-1.2.3.pom", b"different-core")
PY
(
  cd "$repository"
  expect_failure scripts/release/validate-request.sh 1.2.3 "$first_revision" \
    "$temporary_directory/release-manifest.json" "$temporary_directory/central-bundle.zip" \
    "$temporary_directory/wrong-candidate.zip"
)

echo "release input fixtures passed"
