#!/usr/bin/env bash

set -Eeuo pipefail

root_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root_directory"

if [[ $# -lt 3 || $# -gt 4 || ( $# -eq 4 && $4 != "--signed" ) ]]; then
  echo "usage: $0 <version> <full-source-revision> <output-directory> [--signed]" >&2
  exit 2
fi

version=$1
source_revision=$2
output_directory=$(mkdir -p "$3" && cd "$3" && pwd)
signing=${4:-}

scripts/release/validate-version.py "$version"
[[ $source_revision =~ ^[0-9a-f]{40}$ ]] \
  || { echo "source revision must be a full lowercase Git SHA" >&2; exit 1; }

actual_revision=$(git rev-parse HEAD)
[[ $actual_revision == "$source_revision" ]] \
  || { echo "checkout $actual_revision does not match requested revision $source_revision" >&2; exit 1; }

temporary_directory=$(mktemp -d "${TMPDIR:-/tmp}/cardo-maven-release.XXXXXX")
trap 'rm -rf "$temporary_directory"' EXIT
raw_repository="$temporary_directory/raw"
clean_repository="$output_directory/maven-repository"
[[ ! -e $clean_repository && ! -e $output_directory/central-bundle.zip ]] \
  || { echo "release staging output already exists: $output_directory" >&2; exit 1; }
mkdir -p "$raw_repository" "$clean_repository"

profiles=release-staging
if [[ $signing == "--signed" ]]; then
  profiles+=,central-signing
fi

projects=:cardo-bom
while IFS= read -r artifact; do
  projects+=,:$artifact
done <release/supported-artifacts.txt

./mvnw --batch-mode --no-transfer-progress \
  -P "$profiles" \
  -pl "$projects" \
  -Drevision="$version" \
  -DbuildNumber="$source_revision" \
  -DskipTests \
  -DaltDeploymentRepository="cardo-release::file://$raw_repository" \
  clean deploy

while IFS= read -r artifact; do
  if [[ -d "$raw_repository/io/github/lutzseverino/cardo/$artifact/$version" ]]; then
    echo "private artifact entered raw release staging: $artifact" >&2
    exit 1
  fi
done <release/private-artifacts.txt

python3 - "$raw_repository" "$clean_repository" "$version" "$source_revision" "$signing" <<'PY'
import hashlib
import pathlib
import shutil
import sys
import xml.etree.ElementTree as ET
import zipfile

raw = pathlib.Path(sys.argv[1])
clean = pathlib.Path(sys.argv[2])
version = sys.argv[3]
revision = sys.argv[4]
signed = sys.argv[5] == "--signed"
root = pathlib.Path.cwd()
group_path = pathlib.Path("io/github/lutzseverino/cardo")
expected = (root / "release/supported-artifacts.txt").read_text().splitlines()
private = (root / "release/private-artifacts.txt").read_text().splitlines()

namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
bom = ET.parse(root / "cardo-bom/pom.xml")
managed = [
    node.text
    for node in bom.findall("m:dependencyManagement/m:dependencies/m:dependency/m:artifactId", namespace)
]
if managed != expected or len(set(managed)) != len(expected):
    raise SystemExit("cardo-bom must manage the ordered public release allowlist")

def copy_required(source, destination):
    if not source.is_file():
        raise SystemExit(f"missing release artifact: {source}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)

components = [("cardo-bom", "pom")] + [(name, "jar") for name in expected]
for artifact, packaging in components:
    source_dir = raw / group_path / artifact / version
    destination_dir = clean / group_path / artifact / version
    base = f"{artifact}-{version}"
    required = [f"{base}.pom"]
    if packaging == "jar":
        required += [
            f"{base}.jar",
            f"{base}-sources.jar",
            f"{base}-javadoc.jar",
            f"{base}-cyclonedx.json",
        ]
    for filename in required:
        copy_required(source_dir / filename, destination_dir / filename)
        if signed:
            copy_required(source_dir / f"{filename}.asc", destination_dir / f"{filename}.asc")

    pom = ET.parse(destination_dir / f"{base}.pom")
    if pom.find("m:parent", namespace) is not None:
        raise SystemExit(f"{artifact} staged POM still requires the private reactor parent")
    scm_tag = pom.find("m:scm/m:tag", namespace)
    if scm_tag is None or scm_tag.text != revision:
        raise SystemExit(f"{artifact} staged POM lacks exact full source revision")
    for dependency in pom.findall(".//m:dependency", namespace):
        dependency_version = dependency.find("m:version", namespace)
        coordinate = ":".join(
            dependency.find(f"m:{field}", namespace).text or ""
            for field in ("groupId", "artifactId")
        )
        if dependency_version is None or not dependency_version.text:
            raise SystemExit(f"{artifact} staged POM leaves {coordinate} without a version")
        if "${" in dependency_version.text and not (
            artifact == "cardo-bom" and dependency_version.text == "${project.version}"
        ):
            raise SystemExit(f"{artifact} staged POM leaves {coordinate} version unresolved")

contract_jar = clean / group_path / "cardo-openapi-contracts" / version / f"cardo-openapi-contracts-{version}.jar"
contract_entries = {
    "META-INF/cardo/openapi/common/openapi/errors.yaml": root / "common/openapi/errors.yaml",
    "META-INF/cardo/openapi/identity/openapi/identity.yaml": root / "identity/openapi/identity.yaml",
    "META-INF/cardo/openapi/invite/openapi/invite.yaml": root / "invite/openapi/invite.yaml",
    "META-INF/cardo/openapi/billing/openapi/billing.yaml": root / "billing/openapi/billing.yaml",
}
with zipfile.ZipFile(contract_jar) as archive:
    for entry, source in contract_entries.items():
        if archive.read(entry) != source.read_bytes():
            raise SystemExit(f"contract artifact entry differs from authoritative source: {entry}")
contract_sources = contract_jar.with_name(f"cardo-openapi-contracts-{version}-sources.jar")
with zipfile.ZipFile(contract_sources) as archive:
    for entry, source in contract_entries.items():
        if archive.read(entry) != source.read_bytes():
            raise SystemExit(f"contract sources entry differs from authoritative source: {entry}")
contract_javadocs = contract_jar.with_name(f"cardo-openapi-contracts-{version}-javadoc.jar")
with zipfile.ZipFile(contract_javadocs) as archive:
    if "META-INF/MANIFEST.MF" not in archive.namelist():
        raise SystemExit("contract Javadoc archive lacks deterministic Maven archive metadata")

for jar in clean.glob(f"{group_path}/*/{version}/*-{version}.jar"):
    if jar.name.endswith(("-sources.jar", "-javadoc.jar")):
        continue
    with zipfile.ZipFile(jar) as archive:
        manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8")
    if f"Implementation-Version: {version}" not in manifest:
        raise SystemExit(f"{jar.name} lacks exact version metadata")
    if f"Build-Revision: {revision}" not in manifest:
        raise SystemExit(f"{jar.name} lacks full source revision metadata")

release_files = [path for path in sorted(clean.rglob("*")) if path.is_file()]
for path in release_files:
    if path.suffix == ".asc":
        continue
    data = path.read_bytes()
    for algorithm in ("md5", "sha1", "sha256", "sha512"):
        path.with_name(path.name + f".{algorithm}").write_text(
            hashlib.new(algorithm, data).hexdigest() + "\n"
        )

unexpected = []
allowed = {"cardo-bom", *expected}
for version_dir in clean.glob(f"{group_path}/*/{version}"):
    if version_dir.parent.name not in allowed:
        unexpected.append(version_dir.parent.name)
if unexpected:
    raise SystemExit(f"unexpected artifacts in clean staging repository: {unexpected}")
for artifact in private:
    if (clean / group_path / artifact / version).exists():
        raise SystemExit(f"private artifact entered release staging: {artifact}")
bundle = pathlib.Path(sys.argv[2]).parent / "central-bundle.zip"
with zipfile.ZipFile(bundle, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for path in sorted(clean.rglob("*")):
        if not path.is_file():
            continue
        info = zipfile.ZipInfo(path.relative_to(clean).as_posix(), (1980, 1, 1, 0, 0, 2))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o100644 << 16
        archive.writestr(info, path.read_bytes())
PY

echo "staged Maven release $version at $clean_repository"
