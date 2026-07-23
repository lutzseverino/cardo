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
mkdir -p "$temporary_directory/images"
printf '%s\n' '{"images":[' \
  '{"service":"identity","name":"ghcr.io/lutzseverino/cardo/identity"},' \
  '{"service":"invite","name":"ghcr.io/lutzseverino/cardo/invite"},' \
  '{"service":"billing","name":"ghcr.io/lutzseverino/cardo/billing"}' \
  ']}' >"$temporary_directory/images/images.json"
scripts/release/create-manifest.py "$version" "$revision" "$temporary_directory"

python3 - "$temporary_directory/central-bundle.zip" "$temporary_directory/release-manifest.json" "$version" "$revision" <<'PY'
import json
import sys
import zipfile
import xml.etree.ElementTree as ET

archive = zipfile.ZipFile(sys.argv[1])
manifest = json.load(open(sys.argv[2]))
version = sys.argv[3]
revision = sys.argv[4]
names = archive.namelist()
supported = open("release/supported-artifacts.txt").read().splitlines()
private = open("release/private-artifacts.txt").read().splitlines()
if [item["artifactId"] for item in manifest["maven"]["artifacts"]] != supported:
    raise SystemExit("release manifest does not contain the exact public Maven allowlist")
if "openapiBundles" in manifest:
    raise SystemExit("release manifest retained duplicate OpenAPI release assets")
documents = manifest["openapiContracts"]["documents"]
if [item["document"] for item in documents] != ["common-errors", "identity", "invite", "billing"]:
    raise SystemExit("release manifest does not identify all public contract documents")
for artifact in ["cardo-bom", *supported]:
    base = f"io/github/lutzseverino/cardo/{artifact}/{version}/{artifact}-{version}"
    suffixes = (".pom",) if artifact == "cardo-bom" else (
        ".pom", ".jar", "-sources.jar", "-javadoc.jar", "-cyclonedx.json"
    )
    for suffix in suffixes:
        if base + suffix not in names:
            raise SystemExit(f"bundle lacks {base + suffix}")
    pom = ET.fromstring(archive.read(base + ".pom"))
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    if pom.find("m:parent", namespace) is not None:
        raise SystemExit(f"bundle POM for {artifact} still requires a parent")
    scm_tag = pom.find("m:scm/m:tag", namespace)
    if scm_tag is None or scm_tag.text != revision:
        raise SystemExit(f"bundle POM for {artifact} lacks exact source revision")
for artifact in private:
    if any(f"/{artifact}/{version}/" in name for name in names):
        raise SystemExit(f"bundle contains private artifact {artifact}")

contract_base = "META-INF/cardo/openapi"
expected_contracts = {
    f"{contract_base}/common/openapi/errors.yaml": "common/openapi/errors.yaml",
    f"{contract_base}/identity/openapi/identity.yaml": "identity/openapi/identity.yaml",
    f"{contract_base}/invite/openapi/invite.yaml": "invite/openapi/invite.yaml",
    f"{contract_base}/billing/openapi/billing.yaml": "billing/openapi/billing.yaml",
}
contract_jar = archive.read(
    f"io/github/lutzseverino/cardo/cardo-openapi-contracts/{version}/"
    f"cardo-openapi-contracts-{version}.jar"
)
import io
with zipfile.ZipFile(io.BytesIO(contract_jar)) as contracts:
    for entry, source in expected_contracts.items():
        if contracts.read(entry) != open(source, "rb").read():
            raise SystemExit(f"contract entry differs from authoritative source: {entry}")
contract_sources = archive.read(
    f"io/github/lutzseverino/cardo/cardo-openapi-contracts/{version}/"
    f"cardo-openapi-contracts-{version}-sources.jar"
)
with zipfile.ZipFile(io.BytesIO(contract_sources)) as contracts:
    for entry, source in expected_contracts.items():
        if contracts.read(entry) != open(source, "rb").read():
            raise SystemExit(f"contract sources entry differs from authoritative source: {entry}")
contract_javadocs = archive.read(
    f"io/github/lutzseverino/cardo/cardo-openapi-contracts/{version}/"
    f"cardo-openapi-contracts-{version}-javadoc.jar"
)
with zipfile.ZipFile(io.BytesIO(contract_javadocs)) as javadocs:
    if "META-INF/MANIFEST.MF" not in javadocs.namelist():
        raise SystemExit("contract Javadoc archive lacks Maven archive metadata")
PY

echo "Maven release fixture passed"
