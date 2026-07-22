#!/usr/bin/env bash

set -Eeuo pipefail

root_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$root_directory"

run_id="${GITHUB_RUN_ID:-local}-$$"
temporary_directory=$(mktemp -d "${TMPDIR:-/tmp}/cardo-runtime-smoke.XXXXXX")
declare -a containers=()
declare -a processes=()
declare -a images=()
declare -a volumes=()
network="cardo-runtime-smoke-$run_id"
watched_process=""
watched_container=""

cleanup() {
  local process container image volume
  for process in "${processes[@]:-}"; do
    if kill -0 "$process" 2>/dev/null; then
      kill -TERM "$process" 2>/dev/null || true
      wait "$process" 2>/dev/null || true
    fi
  done
  for container in "${containers[@]:-}"; do
    docker rm --force "$container" >/dev/null 2>&1 || true
  done
  for image in "${images[@]:-}"; do
    docker image rm --force "$image" >/dev/null 2>&1 || true
  done
  for volume in "${volumes[@]:-}"; do
    docker volume rm --force "$volume" >/dev/null 2>&1 || true
  done
  docker network rm "$network" >/dev/null 2>&1 || true
  rm -rf "$temporary_directory"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

fail() {
  echo "runtime smoke failed: $*" >&2
  exit 1
}

free_port() {
  python3 - <<'PY'
import socket

with socket.socket() as listener:
    listener.bind(("127.0.0.1", 0))
    print(listener.getsockname()[1])
PY
}

wait_for_url() {
  local url=$1
  local timeout_seconds=${2:-120}
  local deadline=$((SECONDS + timeout_seconds))
  until curl --fail --silent --show-error --max-time 2 "$url" >/dev/null 2>&1; do
    if [[ -n "$watched_process" ]] && ! kill -0 "$watched_process" 2>/dev/null; then
      return 1
    fi
    if [[ -n "$watched_container" ]] \
      && [[ "$(docker inspect --format '{{.State.Running}}' "$watched_container" 2>/dev/null)" != "true" ]]; then
      return 1
    fi
    if (( SECONDS >= deadline )); then
      return 1
    fi
    sleep 1
  done
}

forget_process() {
  local completed=$1
  local process
  local -a active=()
  for process in "${processes[@]:-}"; do
    if [[ -n "$process" && "$process" != "$completed" ]]; then
      active+=("$process")
    fi
  done
  processes=("${active[@]}")
}

hold_incomplete_request() {
  local port=$1
  local marker=$2
  python3 - "$port" "$marker" <<'PY' &
import pathlib
import socket
import sys
import time

connection = socket.create_connection(("127.0.0.1", int(sys.argv[1])), timeout=5)
connection.sendall(b"GET /actuator/info HTTP/1.1\r\nHost: 127.0.0.1\r\nX-Cardo-Smoke:")
pathlib.Path(sys.argv[2]).touch()
time.sleep(30)
connection.close()
PY
  held_request_pid=$!
  processes+=("$held_request_pid")
  local deadline=$((SECONDS + 10))
  until [[ -f "$marker" ]]; do
    if ! kill -0 "$held_request_pid" 2>/dev/null || (( SECONDS >= deadline )); then
      fail "could not establish the controlled in-flight HTTP request"
    fi
    sleep 1
  done
}

start_provider_stub() {
  provider_port=$(free_port)
  python3 - "$provider_port" >"$temporary_directory/provider.log" 2>&1 <<'PY' &
import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

RSA_MODULUS = (
    "oEz1YiQqKNThOSAgGSjtYouxYSAlxJPX2BMvM7LbTFLIx9QuCf-iuq6LSYjF_sqUqgPqYBvrzqoUewDqjT7"
    "p9yoSqMqmzi0tORQ89JM4UAoCJlgRpWdrV0dvaockEJCATgXwfPks_Om-sGBX-aWc1i0sWSGNalCZ2CXtFfx"
    "56R4ez_JhzyGsSjl9u0za00Frem3375_VNYNwcqC1ySlTk0IvJs3_ogzrXR30CFEigoicW_uz2P4bjCmH_iv"
    "dXNBPMHGVt1V1X86GFOg4sUdLQeTrkOgNb_swbX8foW4MbUhnCmEIlMRIhKyrOZAlDiPk7g185xOd0i84oEI"
    "pey8PGw"
)


class Handler(BaseHTTPRequestHandler):
    def send_json(self, status, body):
        payload = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self):
        request = urlparse(self.path)
        host = self.headers.get("Host")
        issuer = f"http://{host}/realms/cardo"
        if ".well-known/openid-configuration" in request.path:
            self.send_json(
                200,
                {
                    "issuer": issuer,
                    "authorization_endpoint": issuer + "/protocol/openid-connect/auth",
                    "token_endpoint": issuer + "/protocol/openid-connect/token",
                    "jwks_uri": issuer + "/protocol/openid-connect/certs",
                },
            )
        elif request.path.endswith("/protocol/openid-connect/certs"):
            self.send_json(
                200,
                {
                    "keys": [
                        {
                            "kty": "RSA",
                            "kid": "cardo-smoke",
                            "use": "sig",
                            "alg": "RS256",
                            "e": "AQAB",
                            "n": RSA_MODULUS,
                        }
                    ]
                },
            )
        elif request.path == "/admin/realms/cardo/clients":
            client_id = parse_qs(request.query).get("clientId", ["cardo-smoke"])[0]
            self.send_json(200, [{"id": "cardo-smoke", "clientId": client_id}])
        elif request.path.endswith("/protocol-mappers/models"):
            self.send_json(200, [])
        else:
            self.send_json(404, {"error": "not_found"})

    def do_POST(self):
        self.rfile.read(int(self.headers.get("Content-Length", "0")))
        if self.path.endswith("/protocol/openid-connect/token"):
            self.send_json(200, {"access_token": "cardo-smoke", "expires_in": 3600})
        else:
            self.send_response(204)
            self.end_headers()

    def do_PUT(self):
        self.rfile.read(int(self.headers.get("Content-Length", "0")))
        self.send_response(204)
        self.end_headers()

    def log_message(self, message, *args):
        return


ThreadingHTTPServer(("0.0.0.0", int(sys.argv[1])), Handler).serve_forever()
PY
  provider_pid=$!
  processes+=("$provider_pid")
  wait_for_url "http://127.0.0.1:$provider_port/realms/cardo/.well-known/openid-configuration" 20 \
    || fail "provider stub did not start"
}

start_postgresql() {
  local service=$1
  postgres_container="cardo-runtime-smoke-${service}-postgres-${run_id}"
  containers+=("$postgres_container")
  docker run --detach --rm \
    --name "$postgres_container" \
    --env POSTGRES_DB=cardo \
    --env POSTGRES_USER=cardo \
    --env POSTGRES_PASSWORD=cardo-smoke \
    --network "$network" \
    --publish 127.0.0.1::5432 \
    postgres:17.5-alpine >/dev/null

  local deadline=$((SECONDS + 60))
  until docker exec "$postgres_container" pg_isready --username cardo --dbname cardo >/dev/null 2>&1; do
    if (( SECONDS >= deadline )); then
      docker logs "$postgres_container" >&2 || true
      fail "$service PostgreSQL did not become ready"
    fi
    sleep 1
  done
  postgres_port=$(docker inspect --format '{{(index (index .NetworkSettings.Ports "5432/tcp") 0).HostPort}}' "$postgres_container")
}

stop_postgresql() {
  docker rm --force "$postgres_container" >/dev/null
}

assert_jar_metadata() {
  local service=$1
  local jar=$2
  local manifest build_info
  manifest=$(unzip -p "$jar" META-INF/MANIFEST.MF)
  build_info=$(unzip -p "$jar" META-INF/build-info.properties)
  grep -q '^Main-Class: org.springframework.boot.loader.launch.JarLauncher' <<<"$manifest" \
    || fail "$service JAR is not executable"
  grep -q "^build.version=$project_version$" <<<"$build_info" \
    || fail "$service JAR does not report version $project_version"
  grep -q "^build.sourceRevision=$source_revision$" <<<"$build_info" \
    || fail "$service JAR does not report revision $source_revision"
}

assert_runtime_contract() {
  local service=$1
  local base_url=$2
  local info
  wait_for_url "$base_url/actuator/health/readiness" 180 || return 1
  curl --fail --silent "$base_url/actuator/health/liveness" \
    | jq --exit-status '.status == "UP"' >/dev/null \
    || fail "$service liveness is not UP"
  curl --fail --silent "$base_url/actuator/health/readiness" \
    | jq --exit-status '.status == "UP"' >/dev/null \
    || fail "$service readiness is not UP"
  info=$(curl --fail --silent "$base_url/actuator/info")
  jq --exit-status \
    --arg version "$project_version" \
    --arg revision "$source_revision" \
    '.build.version == $version and .build.sourceRevision == $revision' \
    <<<"$info" >/dev/null \
    || fail "$service runtime info does not identify its version and revision"

  local unexposed_status
  unexposed_status=$(curl --silent --output /dev/null --write-out '%{http_code}' "$base_url/actuator/env")
  [[ "$unexposed_status" == "401" || "$unexposed_status" == "404" ]] \
    || fail "$service unexpectedly permits the Actuator env endpoint ($unexposed_status)"
}

wait_for_process_exit_by() {
  local process=$1
  local deadline=$2
  while kill -0 "$process" 2>/dev/null; do
    if (( SECONDS >= deadline )); then
      return 1
    fi
    sleep 1
  done
}

assert_jar_readiness_withdrawn() {
  local service=$1
  local process=$2
  local readiness_url=$3
  local deadline=$((SECONDS + 5))
  local status
  while kill -0 "$process" 2>/dev/null; do
    status=$(curl --silent --max-time 1 --output /dev/null --write-out '%{http_code}' "$readiness_url" || true)
    if [[ "$status" != "200" ]] && kill -0 "$process" 2>/dev/null; then
      return
    fi
    if (( SECONDS >= deadline )); then
      break
    fi
    sleep 1
  done
  fail "$service did not withdraw readiness while its graceful shutdown was in progress"
}

assert_container_readiness_withdrawn() {
  local service=$1
  local container=$2
  local readiness_url=$3
  local deadline=$((SECONDS + 5))
  local status
  while [[ "$(docker inspect --format '{{.State.Running}}' "$container")" == "true" ]]; do
    status=$(curl --silent --max-time 1 --output /dev/null --write-out '%{http_code}' "$readiness_url" || true)
    if [[ "$status" != "200" ]] \
      && [[ "$(docker inspect --format '{{.State.Running}}' "$container")" == "true" ]]; then
      return
    fi
    if (( SECONDS >= deadline )); then
      break
    fi
    sleep 1
  done
  fail "$service did not withdraw readiness while its graceful shutdown was in progress"
}

smoke_jar() {
  local service=$1
  local jar="$service/target/$service-$project_version.jar"
  local port log process exit_code marker shutdown_deadline
  port=$(free_port)
  log="$temporary_directory/$service-jar.log"

  assert_jar_metadata "$service" "$jar"
  start_postgresql "$service-jar"
  env \
    SERVER_PORT="$port" \
    SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:$postgres_port/cardo" \
    SPRING_DATASOURCE_USERNAME=cardo \
    SPRING_DATASOURCE_PASSWORD=cardo-smoke \
    KEYCLOAK_BASE_URL="http://127.0.0.1:$provider_port" \
    KEYCLOAK_ISSUER_URI="http://127.0.0.1:$provider_port/realms/cardo" \
    KEYCLOAK_CLIENT_SECRET=cardo-smoke \
    java -jar "$jar" >"$log" 2>&1 &
  process=$!
  processes+=("$process")
  watched_process=$process

  if ! assert_runtime_contract "$service JAR" "http://127.0.0.1:$port"; then
    sed -n '1,260p' "$log" >&2
    fail "$service JAR did not become ready"
  fi
  watched_process=""

  marker="$temporary_directory/$service-jar-held"
  hold_incomplete_request "$port" "$marker"
  shutdown_deadline=$((SECONDS + 25))
  kill -TERM "$process"
  assert_jar_readiness_withdrawn \
    "$service JAR" "$process" "http://127.0.0.1:$port/actuator/health/readiness"
  wait_for_process_exit_by "$process" "$shutdown_deadline" \
    || fail "$service JAR exceeded the 20s shutdown bound"
  set +e
  wait "$process"
  exit_code=$?
  set -e
  forget_process "$process"
  kill -TERM "$held_request_pid" 2>/dev/null || true
  wait "$held_request_pid" 2>/dev/null || true
  forget_process "$held_request_pid"
  [[ "$exit_code" == "0" || "$exit_code" == "143" ]] \
    || fail "$service JAR exited unexpectedly with $exit_code"
  stop_postgresql
  echo "smoked executable $service JAR"
}

assert_image_metadata() {
  local service=$1
  local image=$2
  local configured_user embedded_environment
  configured_user=$(docker image inspect --format '{{.Config.User}}' "$image")
  [[ -n "$configured_user" && "$configured_user" != "root" && "$configured_user" != "0" ]] \
    || fail "$service image does not declare a non-root user"
  [[ "$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.version"}}' "$image")" == "$project_version" ]] \
    || fail "$service image version label is missing"
  [[ "$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$image")" == "$source_revision" ]] \
    || fail "$service image revision label is missing"
  [[ "$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.source"}}' "$image")" == "https://github.com/lutzseverino/cardo" ]] \
    || fail "$service image source label is missing"
  embedded_environment=$(docker image inspect --format '{{json .Config.Env}}' "$image")
  [[ "$embedded_environment" != *"cardo-smoke"* ]] \
    || fail "$service image contains smoke credentials"
}

wait_for_container_exit_by() {
  local container=$1
  local deadline=$2
  while [[ "$(docker inspect --format '{{.State.Running}}' "$container")" == "true" ]]; do
    if (( SECONDS >= deadline )); then
      return 1
    fi
    sleep 1
  done
}

smoke_image() {
  local service=$1
  local image="cardo-runtime-smoke/$service:$run_id"
  local container="cardo-runtime-smoke-${service}-image-${run_id}"
  local build_cache="cardo-runtime-smoke-${service}-build-${run_id}"
  local launch_cache="cardo-runtime-smoke-${service}-launch-${run_id}"
  local port exit_code marker shutdown_deadline
  images+=("$image")
  containers+=("$container")
  volumes+=("$build_cache" "$launch_cache")

  ./mvnw --batch-mode --no-transfer-progress -pl "$service" \
    -DskipTests \
    -Dcardo.image.build-cache="$build_cache" \
    -Dcardo.image.launch-cache="$launch_cache" \
    -Dcardo.image.name="$image" \
    package spring-boot:build-image-no-fork
  assert_image_metadata "$service" "$image"
  start_postgresql "$service-image"

  docker run --detach \
    --name "$container" \
    --add-host host.docker.internal:host-gateway \
    --network "$network" \
    --publish 127.0.0.1::8080 \
    --env SERVER_PORT=8080 \
    --env SPRING_DATASOURCE_URL="jdbc:postgresql://$postgres_container:5432/cardo" \
    --env SPRING_DATASOURCE_USERNAME=cardo \
    --env SPRING_DATASOURCE_PASSWORD=cardo-smoke \
    --env KEYCLOAK_BASE_URL="http://host.docker.internal:$provider_port" \
    --env KEYCLOAK_ISSUER_URI="http://host.docker.internal:$provider_port/realms/cardo" \
    --env KEYCLOAK_CLIENT_SECRET=cardo-smoke \
    "$image" >/dev/null
  watched_container=$container
  port=$(docker inspect --format '{{(index (index .NetworkSettings.Ports "8080/tcp") 0).HostPort}}' "$container")

  if ! assert_runtime_contract "$service image" "http://127.0.0.1:$port"; then
    docker logs "$container" >&2 || true
    fail "$service image did not become ready"
  fi
  watched_container=""
  marker="$temporary_directory/$service-image-held"
  hold_incomplete_request "$port" "$marker"
  shutdown_deadline=$((SECONDS + 25))
  docker kill --signal TERM "$container" >/dev/null
  assert_container_readiness_withdrawn \
    "$service image" "$container" "http://127.0.0.1:$port/actuator/health/readiness"
  wait_for_container_exit_by "$container" "$shutdown_deadline" \
    || fail "$service image exceeded the 20s shutdown bound"
  kill -TERM "$held_request_pid" 2>/dev/null || true
  wait "$held_request_pid" 2>/dev/null || true
  forget_process "$held_request_pid"
  exit_code=$(docker inspect --format '{{.State.ExitCode}}' "$container")
  [[ "$exit_code" == "0" || "$exit_code" == "143" ]] \
    || fail "$service image exited unexpectedly with $exit_code"
  docker rm "$container" >/dev/null
  stop_postgresql
  echo "smoked non-root $service image"
}

command -v curl >/dev/null || fail "curl is required"
command -v docker >/dev/null || fail "docker is required"
command -v jq >/dev/null || fail "jq is required"
command -v python3 >/dev/null || fail "python3 is required"
command -v unzip >/dev/null || fail "unzip is required"
docker info >/dev/null 2>&1 || fail "a Docker-compatible daemon is required"
docker network create "$network" >/dev/null

project_version=$(./mvnw --quiet --no-transfer-progress help:evaluate \
  -Dexpression=project.version -DforceStdout)
source_revision=$(git rev-parse HEAD)
start_provider_stub

./mvnw --batch-mode --no-transfer-progress -DskipTests install

for service in identity invite billing; do
  smoke_jar "$service"
  smoke_image "$service"
done

echo "all Cardo runtime artifacts passed smoke validation"
