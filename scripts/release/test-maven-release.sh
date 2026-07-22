#!/usr/bin/env bash

set -Eeuo pipefail

root_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root_directory"

version=${1:-0.1.0-rc.0.local}
revision=$(git rev-parse HEAD)
temporary_directory=$(mktemp -d "${TMPDIR:-/tmp}/cardo-release-test.XXXXXX")
trap 'rm -rf "$temporary_directory"' EXIT

scripts/release/stage-maven.sh "$version" "$revision" "$temporary_directory"
scripts/release/verify-consumer.sh "$version" "$temporary_directory/maven-repository"

python3 - "$temporary_directory/central-bundle.zip" "$version" <<'PY'
import sys
import zipfile

archive = zipfile.ZipFile(sys.argv[1])
version = sys.argv[2]
names = archive.namelist()
supported = open("release/supported-artifacts.txt").read().splitlines()
for artifact in supported:
    base = f"io/github/lutzseverino/cardo/{artifact}/{version}/{artifact}-{version}"
    for suffix in (".pom", ".jar", "-sources.jar", "-javadoc.jar", "-cyclonedx.json"):
        if base + suffix not in names:
            raise SystemExit(f"bundle lacks {base + suffix}")
if any(f"openapi-support-{version}-tests.jar" in name for name in names):
    raise SystemExit("bundle contains internal OpenAPI tests classifier")
if any(f"/{service}/{version}/" in name for service in ("identity", "invite", "billing") for name in names):
    raise SystemExit("bundle contains an executable service artifact")
PY

echo "Maven release fixture passed"
