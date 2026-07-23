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

temporary_directory=$(mktemp -d "${TMPDIR:-/tmp}/cardo-release-request.XXXXXX")
trap 'rm -rf "$temporary_directory"' EXIT
tag_step_fixture="$temporary_directory/create-or-validate-tag.sh"

python3 - .github/workflows/release.yml "$tag_step_fixture" <<'PY'
import pathlib
import sys
import textwrap

workflow = pathlib.Path(sys.argv[1]).read_text()
tag_step_fixture = pathlib.Path(sys.argv[2])
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
    if command.startswith("gh release create ") and "--verify-tag" not in command:
        raise SystemExit(f"GitHub release creation does not require an existing tag: {command}")
    if "--target" in command:
        raise SystemExit(f"GitHub release creation can still synthesize a tag: {command}")

publish = workflow.split("  publish:", 1)[1].split("\n  verify-private-runtime:", 1)[0]
tag_name = "- name: Create or validate immutable release tag"
first_release = 'gh release create "v$RELEASE_VERSION"'
if publish.count(tag_name) != 1:
    raise SystemExit("publish job must contain exactly one immutable-tag step")
if not (
    publish.index("run: scripts/release/check-ghcr-package-state.sh")
    < publish.index(tag_name)
    < publish.index(first_release)
):
    raise SystemExit("immutable tag is not proven after preflight and before the staging draft")
tag_step = publish.split(tag_name, 1)[1].split("\n      - name:", 1)[0]
tag_script = textwrap.dedent(tag_step.split("        run: |\n", 1)[1])
tag_step_fixture.write_text("#!/usr/bin/env bash\nset -Eeuo pipefail\n" + tag_script)
tag_step_fixture.chmod(0o755)

expected_tag_command = (
    "git -c user.name='github-actions[bot]' "
    "-c user.email='41898282+github-actions[bot]@users.noreply.github.com' "
    'tag --annotate "$tag" "$RELEASE_REVISION" '
    '--message "Cardo $RELEASE_VERSION"'
)
collapsed_tag_script = " ".join(line.strip().rstrip("\\").strip() for line in tag_script.splitlines())
collapsed_tag_script = " ".join(collapsed_tag_script.split())
if expected_tag_command not in collapsed_tag_script:
    raise SystemExit("release tag command differs from deterministic bot policy")
for value in [
    'git ls-remote --exit-code --tags origin "refs/tags/$tag"',
    'git push origin "refs/tags/$tag"',
    'git ls-remote --tags origin "refs/tags/$tag^{}"',
    '[[ -n $tag_revision && $tag_revision == "$RELEASE_REVISION" ]]',
]:
    if value not in tag_script:
        raise SystemExit(f"immutable tag step lacks: {value}")
PY

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
git -C "$repository" config tag.gpgSign false
git -C "$repository" remote add origin "$remote"
git -C "$repository" add scripts
git -C "$repository" commit --quiet --message initial
first_revision=$(git -C "$repository" rev-parse HEAD)
git -C "$repository" push --quiet --set-upstream origin main
(
  cd "$repository"
  scripts/release/validate-request.sh 1.2.3 "$first_revision"
  RELEASE_VERSION=1.2.3 RELEASE_REVISION="$first_revision" "$tag_step_fixture"
  RELEASE_VERSION=1.2.3 RELEASE_REVISION="$first_revision" "$tag_step_fixture"
)
[[ $(git -C "$repository" rev-parse "v1.2.3^{}") == "$first_revision" ]] \
  || { echo "created annotated tag does not peel to the requested revision" >&2; exit 1; }
git -C "$repository" cat-file tag v1.2.3 >"$temporary_directory/tag-object"
grep --fixed-strings \
  'tagger github-actions[bot] <41898282+github-actions[bot]@users.noreply.github.com>' \
  "$temporary_directory/tag-object" >/dev/null \
  || { echo "created annotated tag lacks deterministic bot identity" >&2; exit 1; }
grep --fixed-strings 'Cardo 1.2.3' "$temporary_directory/tag-object" >/dev/null \
  || { echo "created annotated tag lacks deterministic message" >&2; exit 1; }

git -C "$repository" tag v1.2.4 "$first_revision"
git -C "$repository" push --quiet origin refs/tags/v1.2.4
(
  cd "$repository"
  if RELEASE_VERSION=1.2.4 RELEASE_REVISION="$first_revision" \
    "$tag_step_fixture" >/dev/null 2>&1; then
    echo "lightweight release tag was accepted" >&2
    exit 1
  fi
)
git -C "$repository" tag --annotate v1.2.5 "$first_revision" --message 'Cardo 1.2.5'
git -C "$repository" push --quiet origin refs/tags/v1.2.5
(
  cd "$repository"
  if RELEASE_VERSION=1.2.5 RELEASE_REVISION="$(printf 'f%.0s' {1..40})" \
    "$tag_step_fixture" >/dev/null 2>&1; then
    echo "annotated release tag on the wrong target was accepted" >&2
    exit 1
  fi
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
    "tag": "v1.2.3",
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

python3 - "$temporary_directory/release-manifest.json" <<'PY'
import json
import pathlib
import sys
path = pathlib.Path(sys.argv[1])
manifest = json.loads(path.read_text())
manifest["tag"] = "v1.2.4"
pathlib.Path(str(path) + ".wrong-tag").write_text(json.dumps(manifest))
PY
(
  cd "$repository"
  expect_failure scripts/release/validate-request.sh 1.2.3 "$first_revision" \
    "$temporary_directory/release-manifest.json.wrong-tag" "$temporary_directory/central-bundle.zip"
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

git -C "$repository" checkout --quiet --detach "$second_revision"
(
  cd "$repository"
  scripts/release/validate-request.sh --published-release \
    1.2.3 "$first_revision" \
    "$temporary_directory/release-manifest.json" "$temporary_directory/central-bundle.zip"
  expect_failure scripts/release/validate-request.sh --published-release \
    1.2.4 "$first_revision" \
    "$temporary_directory/release-manifest.json" "$temporary_directory/central-bundle.zip"
  expect_failure scripts/release/validate-request.sh --published-release \
    1.2.3 "$second_revision" \
    "$temporary_directory/release-manifest.json" "$temporary_directory/central-bundle.zip"
  expect_failure scripts/release/validate-request.sh --published-release \
    1.2.3 "$first_revision" \
    "$temporary_directory/release-manifest.json.wrong-tag" "$temporary_directory/central-bundle.zip"
  expect_failure scripts/release/validate-request.sh --published-release \
    1.2.3 "$first_revision" \
    "$temporary_directory/release-manifest.json" "$temporary_directory/wrong-bundle.zip"
)

git -C "$repository" checkout --quiet --detach "$first_revision"
(
  cd "$repository"
  scripts/release/validate-request.sh 1.2.3 "$first_revision" \
    "$temporary_directory/release-manifest.json" "$temporary_directory/central-bundle.zip"
)

git -C "$repository" tag --force --annotate v1.2.3 "$second_revision" --message 'Cardo 1.2.3'
git -C "$repository" push --quiet --force origin refs/tags/v1.2.3
(
  cd "$repository"
  expect_failure scripts/release/validate-request.sh 1.2.3 "$first_revision" \
    "$temporary_directory/release-manifest.json" "$temporary_directory/central-bundle.zip"
  expect_failure scripts/release/validate-request.sh --published-release \
    1.2.3 "$first_revision" \
    "$temporary_directory/release-manifest.json" "$temporary_directory/central-bundle.zip"
)

echo "release input fixtures passed"
