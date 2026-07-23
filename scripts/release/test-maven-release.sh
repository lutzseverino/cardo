#!/usr/bin/env bash

set -Eeuo pipefail

root_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root_directory"

version=${1:-0.1.0-rc.0.local}
revision=$(git rev-parse HEAD)
temporary_directory=$(mktemp -d "${TMPDIR:-/tmp}/cardo-release-test.XXXXXX")
trap 'rm -rf "$temporary_directory"' EXIT

candidate_directory="$temporary_directory/candidate-utc"
comparison_directory="$temporary_directory/candidate-kiritimati"
TZ=UTC scripts/release/stage-maven.sh "$version" "$revision" "$candidate_directory"
TZ=Pacific/Kiritimati scripts/release/stage-maven.sh \
  "$version" "$revision" "$comparison_directory"
if ! cmp -s \
  "$candidate_directory/central-bundle.zip" \
  "$comparison_directory/central-bundle.zip"; then
  echo "unsigned Central candidates differ across build timezones" >&2
  shasum -a 256 \
    "$candidate_directory/central-bundle.zip" \
    "$comparison_directory/central-bundle.zip" >&2
  exit 1
fi

scripts/release/verify-consumer.sh "$version" "$candidate_directory/maven-repository"
mkdir -p "$candidate_directory/images"
printf '%s\n' '{"images":[' \
  '{"service":"identity","name":"ghcr.io/lutzseverino/cardo/identity"},' \
  '{"service":"invite","name":"ghcr.io/lutzseverino/cardo/invite"},' \
  '{"service":"billing","name":"ghcr.io/lutzseverino/cardo/billing"}' \
  ']}' >"$candidate_directory/images/images.json"
scripts/release/create-manifest.py "$version" "$revision" "$candidate_directory"

python3 - "$candidate_directory/central-bundle.zip" "$candidate_directory/release-manifest.json" "$version" "$revision" <<'PY'
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
expected_project_url = "https://github.com/lutzseverino/cardo"
expected_scm = {
    "connection": "scm:git:https://github.com/lutzseverino/cardo.git",
    "developerConnection": "scm:git:ssh://git@github.com/lutzseverino/cardo.git",
    "url": expected_project_url,
}
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
    project_url = pom.find("m:url", namespace)
    if project_url is None or project_url.text != expected_project_url:
        raise SystemExit(f"bundle POM for {artifact} has an incorrect repository URL")
    for element, value in expected_scm.items():
        node = pom.find(f"m:scm/m:{element}", namespace)
        if node is None or node.text != value:
            raise SystemExit(f"bundle POM for {artifact} has incorrect SCM {element}")
    scm_tag = pom.find("m:scm/m:tag", namespace)
    if scm_tag is None or scm_tag.text != revision:
        raise SystemExit(f"bundle POM for {artifact} lacks exact source revision")
    if artifact == "cardo-openapi-contracts" and pom.findall(
        "m:dependencies/m:dependency", namespace
    ):
        raise SystemExit("contract-only bundle POM contains an unnecessary dependency")
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
contract_inventory = json.loads(archive.read(
    f"io/github/lutzseverino/cardo/cardo-openapi-contracts/{version}/"
    f"cardo-openapi-contracts-{version}-cyclonedx.json"
))
if contract_inventory.get("components", []):
    raise SystemExit("contract-only CycloneDX inventory contains external components")
root_ref = contract_inventory["metadata"]["component"]["bom-ref"]
if contract_inventory.get("dependencies") != [{"ref": root_ref, "dependsOn": []}]:
    raise SystemExit("contract-only CycloneDX root contains external dependencies")
PY

echo "Maven release fixture passed"
