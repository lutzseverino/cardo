#!/usr/bin/env bash

set -Eeuo pipefail

case ${0##*/} in
  mvnw)
    repository=
    for option in ${MAVEN_OPTS:-}; do
      case $option in
        -Dmaven.repo.local=*) repository=${option#*=} ;;
      esac
    done
    [[ -n $repository ]] || { echo "fixture Maven repository is not configured" >&2; exit 1; }
    arguments=$*
    (IFS=$'\t'; printf '%s\n' "$*") >>"$FIXTURE_COMMAND_LOG"

    version=
    image=
    project=
    while [[ $# -gt 0 ]]; do
      case $1 in
        -Drevision=*) version=${1#*=} ;;
        -Dcardo.image.name=*) image=${1#*=} ;;
        -pl) project=$2; shift ;;
      esac
      shift
    done

    coordinates="$repository/io/github/lutzseverino/cardo"
    if [[ " $arguments " == *' clean install '* ]]; then
      for artifact in common identity-client invite-client billing-client; do
        directory="$coordinates/$artifact/$version"
        mkdir -p "$directory"
        : >"$directory/$artifact-$version.pom"
      done
      exit
    fi

    case $project in
      identity) sibling=common ;;
      invite) sibling=invite-client ;;
      billing) sibling=billing-client ;;
      *) echo "fixture image goal did not select an executable service" >&2; exit 1 ;;
    esac
    [[ -f $coordinates/$sibling/$version/$sibling-$version.pom ]] \
      || { echo "fixture image goal cannot resolve reactor-installed $sibling" >&2; exit 1; }
    [[ $image == "ghcr.io/lutzseverino/cardo/$project:$version" ]] \
      || { echo "fixture image goal has an unexpected image name" >&2; exit 1; }
    printf '%s\n' "$image" >>"$FIXTURE_IMAGE_STATE"
    exit
    ;;
  docker)
    [[ $1 == image && $2 == inspect && $3 == --format ]] \
      || { echo "fixture received an unexpected Docker command" >&2; exit 1; }
    format=$4
    image=$5
    grep --fixed-strings --line-regexp "$image" "$FIXTURE_IMAGE_STATE" >/dev/null \
      || { echo "fixture Docker image was not built: $image" >&2; exit 1; }
    service=${image#ghcr.io/lutzseverino/cardo/}
    service=${service%%:*}
    case $format in
      '{{.Id}}') printf 'sha256:%s\n' "$service" ;;
      '{{json .Config.Labels}}')
        printf \
          '{"org.opencontainers.image.version":"%s","org.opencontainers.image.revision":"%s"}\n' \
          "$FIXTURE_VERSION" "$FIXTURE_REVISION"
        ;;
      *) echo "fixture received an unexpected Docker inspect format" >&2; exit 1 ;;
    esac
    exit
    ;;
  syft)
    image=${1#docker:}
    [[ $2 == --output && $3 == cyclonedx-json=* ]] \
      || { echo "fixture received an unexpected Syft command" >&2; exit 1; }
    output=${3#*=}
    service=${image#ghcr.io/lutzseverino/cardo/}
    service=${service%%:*}
    printf '{"bomFormat":"CycloneDX","components":[{"name":"%s"}]}\n' "$service" >"$output"
    exit
    ;;
esac

root_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root_directory"

temporary_directory=$(mktemp -d "${TMPDIR:-/tmp}/cardo-image-rebuild.XXXXXX")
trap 'rm -rf "$temporary_directory"' EXIT
fixture_repository="$temporary_directory/repository"
fixture_bin="$temporary_directory/bin"
local_repository="$temporary_directory/maven-repository"
candidate="$temporary_directory/candidate"
rebuilt="$temporary_directory/rebuilt"
mkdir -p "$fixture_repository/scripts/release" "$fixture_bin" "$candidate" "$rebuilt"
cp scripts/release/build-images.sh scripts/release/validate-version.py \
  "$fixture_repository/scripts/release/"
chmod +x "$fixture_repository/scripts/release/"*
ln -s "$root_directory/scripts/release/test-image-rebuild.sh" "$fixture_repository/mvnw"
ln -s "$root_directory/scripts/release/test-image-rebuild.sh" "$fixture_bin/docker"
ln -s "$root_directory/scripts/release/test-image-rebuild.sh" "$fixture_bin/syft"
git -C "$fixture_repository" init --quiet --initial-branch=main
git -C "$fixture_repository" config user.email release-fixture@example.invalid
git -C "$fixture_repository" config user.name 'Release fixture'
git -C "$fixture_repository" add .
git -C "$fixture_repository" commit --quiet --message initial
revision=$(git -C "$fixture_repository" rev-parse HEAD)
version=1.2.3

run_dispatch() {
  local output_directory=$1
  local command_log=$2
  local image_state=$3
  rm -rf "$local_repository"
  mkdir -p "$local_repository"
  [[ ! -e $local_repository/io/github/lutzseverino/cardo ]] \
    || { echo "fixture Maven repository unexpectedly contains Cardo coordinates" >&2; exit 1; }
  : >"$command_log"
  : >"$image_state"
  (
    cd "$fixture_repository"
    PATH="$fixture_bin:$PATH" \
      MAVEN_OPTS="-Dmaven.repo.local=$local_repository" \
      FIXTURE_COMMAND_LOG="$command_log" \
      FIXTURE_IMAGE_STATE="$image_state" \
      FIXTURE_REVISION="$revision" \
      FIXTURE_VERSION="$version" \
      scripts/release/build-images.sh "$version" "$revision" "$output_directory"
  )
  python3 - "$command_log" "$version" "$revision" <<'PY'
import pathlib
import sys

commands = [line.split("\t") for line in pathlib.Path(sys.argv[1]).read_text().splitlines()]
version = sys.argv[2]
revision = sys.argv[3]
common = [
    "--batch-mode",
    "--no-transfer-progress",
    f"-Drevision={version}",
    f"-DbuildNumber={revision}",
]
expected = [
    [*common, "-DskipTests", "-pl", "identity,invite,billing", "-am", "clean", "install"],
    *[
        [
            *common,
            f"-Dcardo.image.name=ghcr.io/lutzseverino/cardo/{service}:{version}",
            "-DskipTests",
            "-pl",
            service,
            "spring-boot:build-image-no-fork",
        ]
        for service in ("identity", "invite", "billing")
    ],
]
if commands != expected:
    raise SystemExit(f"unexpected Maven image orchestration: {commands!r}")
PY
}

run_dispatch "$candidate" "$temporary_directory/candidate-commands" \
  "$temporary_directory/candidate-images"
run_dispatch "$rebuilt" "$temporary_directory/rebuilt-commands" \
  "$temporary_directory/rebuilt-images"

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
