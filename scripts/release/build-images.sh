#!/usr/bin/env bash

set -Eeuo pipefail

root_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root_directory"

if [[ $# -ne 3 ]]; then
  echo "usage: $0 <version> <full-source-revision> <output-directory>" >&2
  exit 2
fi

version=$1
source_revision=$2
output_directory=$(mkdir -p "$3" && cd "$3" && pwd)
scripts/release/validate-version.py "$version"
command -v docker >/dev/null || { echo "docker is required" >&2; exit 1; }
command -v syft >/dev/null || { echo "syft is required for image inventories" >&2; exit 1; }
[[ $(git rev-parse HEAD) == "$source_revision" ]] \
  || { echo "checkout does not match requested source revision" >&2; exit 1; }

printf '{"images":[' >"$output_directory/images.json"
separator=
./mvnw --batch-mode --no-transfer-progress \
  -Drevision="$version" \
  -DbuildNumber="$source_revision" \
  -DskipTests \
  -pl identity,invite,billing \
  -am \
  clean install

for service in identity invite billing; do
  image="ghcr.io/lutzseverino/cardo/$service:$version"
  ./mvnw --batch-mode --no-transfer-progress \
    -Drevision="$version" \
    -DbuildNumber="$source_revision" \
    -Dcardo.image.name="$image" \
    -DskipTests \
    -pl "$service" \
    spring-boot:build-image-no-fork
  image_id=$(docker image inspect --format '{{.Id}}' "$image")
  labels=$(docker image inspect --format '{{json .Config.Labels}}' "$image")
  jq --exit-status \
    --arg version "$version" \
    --arg revision "$source_revision" \
    '."org.opencontainers.image.version" == $version and ."org.opencontainers.image.revision" == $revision' \
    <<<"$labels" >/dev/null \
    || { echo "$image does not carry exact release metadata" >&2; exit 1; }
  sbom="$output_directory/$service-image.cyclonedx.json"
  syft "docker:$image" --output "cyclonedx-json=$sbom"
  normalized_sbom="$sbom.normalized"
  jq --sort-keys 'del(.serialNumber, .metadata.timestamp)' "$sbom" >"$normalized_sbom"
  mv "$normalized_sbom" "$sbom"
  sbom_sha256=$(shasum -a 256 "$sbom" | awk '{print $1}')
  printf '%s' "$separator" >>"$output_directory/images.json"
  jq --compact-output --null-input \
    --arg service "$service" \
    --arg name "ghcr.io/lutzseverino/cardo/$service" \
    --arg tag "$version" \
    --arg localContentId "$image_id" \
    --arg sbom "$service-image.cyclonedx.json" \
    --arg sbomSha256 "$sbom_sha256" \
    '{service:$service,name:$name,tag:$tag,localContentId:$localContentId,sbom:$sbom,sbomSha256:$sbomSha256}' \
    >>"$output_directory/images.json"
  separator=,
done
printf ']}\n' >>"$output_directory/images.json"

echo "built exact Cardo service images and CycloneDX inventories for $version"
