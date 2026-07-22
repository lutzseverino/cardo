#!/usr/bin/env bash

set -Eeuo pipefail

root_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$root_directory"

if ! command -v docker >/dev/null 2>&1; then
  echo "The reference stack requires a Docker-compatible daemon." >&2
  exit 1
fi

docker_endpoint=$(docker context inspect "$(docker context show)" --format '{{.Endpoints.docker.Host}}')
if [[ -z "${DOCKER_HOST:-}" ]]; then
  export DOCKER_HOST=$docker_endpoint
  if [[ "$docker_endpoint" == unix://* && "$docker_endpoint" != unix:///var/run/docker.sock ]]; then
    export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=${TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE:-/var/run/docker.sock}
  fi
fi

if ! docker info >/dev/null 2>&1; then
  echo "The configured Docker-compatible daemon is unavailable." >&2
  exit 1
fi

java_version=$(java -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version/ {print $2; exit}')
if [[ "$java_version" != 21 ]]; then
  echo "The reference stack requires Java 21; found ${java_version:-unknown}." >&2
  exit 1
fi

./mvnw --batch-mode --no-transfer-progress \
  -pl identity,invite,billing,integration/reference-stack \
  -am -DskipTests install

./mvnw --batch-mode --no-transfer-progress \
  -pl integration/reference-stack \
  -Preference-stack verify
