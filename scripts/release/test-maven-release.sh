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

python3 - "$temporary_directory/central-bundle.zip" "$version" "$revision" <<'PY'
import sys
import zipfile
import xml.etree.ElementTree as ET

archive = zipfile.ZipFile(sys.argv[1])
version = sys.argv[2]
revision = sys.argv[3]
names = archive.namelist()
supported = open("release/supported-artifacts.txt").read().splitlines()
for artifact in ["cardo", "cardo-bom", *supported]:
    base = f"io/github/lutzseverino/cardo/{artifact}/{version}/{artifact}-{version}"
    suffixes = (".pom",) if artifact in {"cardo", "cardo-bom"} else (
        ".pom", ".jar", "-sources.jar", "-javadoc.jar", "-cyclonedx.json"
    )
    for suffix in suffixes:
        if base + suffix not in names:
            raise SystemExit(f"bundle lacks {base + suffix}")
    pom = ET.fromstring(archive.read(base + ".pom"))
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    scm_tag = pom.find("m:scm/m:tag", namespace)
    if scm_tag is None or scm_tag.text != revision:
        raise SystemExit(f"bundle POM for {artifact} lacks exact source revision")
if any(f"openapi-support-{version}-tests.jar" in name for name in names):
    raise SystemExit("bundle contains internal OpenAPI tests classifier")
if any(f"/{service}/{version}/" in name for service in ("identity", "invite", "billing") for name in names):
    raise SystemExit("bundle contains an executable service artifact")
PY

echo "Maven release fixture passed"
