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

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
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
from urllib.parse import parse_qs, unquote, urlparse

RSA_MODULUS = (
    "oEz1YiQqKNThOSAgGSjtYouxYSAlxJPX2BMvM7LbTFLIx9QuCf-iuq6LSYjF_sqUqgPqYBvrzqoUewDqjT7"
    "p9yoSqMqmzi0tORQ89JM4UAoCJlgRpWdrV0dvaockEJCATgXwfPks_Om-sGBX-aWc1i0sWSGNalCZ2CXtFfx"
    "56R4ez_JhzyGsSjl9u0za00Frem3375_VNYNwcqC1ySlTk0IvJs3_ogzrXR30CFEigoicW_uz2P4bjCmH_iv"
    "dXNBPMHGVt1V1X86GFOg4sUdLQeTrkOgNb_swbX8foW4MbUhnCmEIlMRIhKyrOZAlDiPk7g185xOd0i84oEI"
    "pey8PGw"
)

CLIENTS = {
    "cardo-identity": "uuid-cardo-identity",
    "cardo-web": "uuid-cardo-web",
    "identity": "uuid-identity",
    "billing": "uuid-billing",
}

CANONICAL_MAPPER = {
    "id": "mapper-cardo-user-id",
    "name": "cardo_user_id",
    "protocol": "openid-connect",
    "protocolMapper": "oidc-usermodel-attribute-mapper",
    "consentRequired": False,
    "config": {
        "user.attribute": "cardo_user_id",
        "claim.name": "cardo_user_id",
        "jsonType.label": "String",
        "access.token.claim": "true",
        "id.token.claim": "false",
        "userinfo.token.claim": "false",
        "multivalued": "false",
    },
}

IDENTITY_ROLES = {"profile:read", "profile:write", "user:provision"}


class Handler(BaseHTTPRequestHandler):
    def read_request_body(self):
        if "chunked" in self.headers.get("Transfer-Encoding", "").lower():
            chunks = []
            while True:
                size_line = self.rfile.readline()
                if not size_line:
                    raise ConnectionError("request ended before the terminal chunk")
                size = int(size_line.split(b";", 1)[0].strip(), 16)
                if size == 0:
                    while self.rfile.readline() not in (b"\r\n", b"\n", b""):
                        pass
                    break
                chunk = self.rfile.read(size)
                if len(chunk) != size or self.rfile.read(2) != b"\r\n":
                    raise ConnectionError("request ended inside a chunk")
                chunks.append(chunk)
            return b"".join(chunks)
        return self.rfile.read(int(self.headers.get("Content-Length", "0")))

    def send_json(self, status, body):
        payload = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def send_empty(self, status):
        self.send_response(status)
        self.send_header("Content-Length", "0")
        self.end_headers()

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
            client_id = parse_qs(request.query).get("clientId", [""])[0]
            client_uuid = CLIENTS.get(client_id)
            clients = [] if client_uuid is None else [{"id": client_uuid, "clientId": client_id}]
            self.send_json(200, clients)
        elif request.path.endswith("/protocol-mappers/models"):
            client_uuid = request.path.split("/")[-3]
            self.send_json(200, [CANONICAL_MAPPER] if client_uuid in CLIENTS.values() else [])
        elif request.path.startswith("/admin/realms/cardo/clients/uuid-identity/roles/"):
            role = unquote(request.path.rsplit("/", 1)[-1])
            self.send_json(
                200 if role in IDENTITY_ROLES else 404,
                {"id": "role-" + role, "name": role}
                if role in IDENTITY_ROLES
                else {"error": "not_found"},
            )
        elif request.path == "/admin/realms/cardo/users":
            self.send_json(200, [])
        elif request.path == "/realms/cardo/authz/protection/resource_set":
            self.send_json(200, [])
        else:
            self.send_json(404, {"error": "not_found"})

    def do_POST(self):
        body = self.read_request_body()
        if self.path == "/__cardo_smoke__/chunked-body":
            self.send_json(200, {"length": len(body)})
        elif self.path.endswith("/protocol/openid-connect/token"):
            self.send_json(200, {"access_token": "cardo-smoke", "expires_in": 3600})
        elif "/protocol-mappers/models" in self.path or self.path.endswith("/roles"):
            self.send_empty(403)
        else:
            self.send_json(404, {"error": "not_found"})

    def do_PUT(self):
        self.read_request_body()
        if "/protocol-mappers/models/" in self.path:
            self.send_empty(403)
        else:
            self.send_json(404, {"error": "not_found"})

    def do_DELETE(self):
        if "/protocol-mappers/models/" in self.path:
            self.send_empty(403)
        else:
            self.send_json(404, {"error": "not_found"})

    def log_message(self, message, *args):
        return


ThreadingHTTPServer(("0.0.0.0", int(sys.argv[1])), Handler).serve_forever()
PY
  provider_pid=$!
  processes+=("$provider_pid")
  wait_for_url "http://127.0.0.1:$provider_port/realms/cardo/.well-known/openid-configuration" 20 \
    || fail "provider stub did not start"
  python3 - "$provider_port" <<'PY' \
    || fail "provider stub did not consume a chunked request body"
import http.client
import json
import sys

chunks = [b"cardo-", b"chunked-", b"request"]
connection = http.client.HTTPConnection("127.0.0.1", int(sys.argv[1]), timeout=5)
connection.request(
    "POST",
    "/__cardo_smoke__/chunked-body",
    body=iter(chunks),
    encode_chunked=True,
)
response = connection.getresponse()
payload = json.loads(response.read())
connection.close()
assert response.status == 200
assert payload == {"length": sum(map(len, chunks))}
PY
  local mapper_write_status role_write_status unexpected_status
  mapper_write_status=$(curl --silent --output /dev/null --write-out '%{http_code}' \
    --request POST --data '{}' \
    "http://127.0.0.1:$provider_port/admin/realms/cardo/clients/uuid-identity/protocol-mappers/models")
  role_write_status=$(curl --silent --output /dev/null --write-out '%{http_code}' \
    --request POST --data '{}' \
    "http://127.0.0.1:$provider_port/admin/realms/cardo/clients/uuid-identity/roles")
  unexpected_status=$(curl --silent --output /dev/null --write-out '%{http_code}' \
    --request POST --data '{}' "http://127.0.0.1:$provider_port/unexpected")
  [[ "$mapper_write_status" == "403" && "$role_write_status" == "403" ]] \
    || fail "provider stub does not reject mapper and role definition writes"
  [[ "$unexpected_status" == "404" ]] \
    || fail "provider stub does not deny unexpected requests by default"
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

  local component_status
  component_status=$(curl --silent --output /dev/null --write-out '%{http_code}' \
    "$base_url/actuator/health/db")
  [[ "$component_status" == "401" ]] \
    || fail "$service unexpectedly permits an Actuator health component path ($component_status)"
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
    MANAGEMENT_ENDPOINT_HEALTH_SHOW_COMPONENTS=always \
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
  [[ "$(docker image inspect --format '{{.Created}}' "$image")" == "1980-01-01T00:00:02Z" ]] \
    || fail "$service image creation timestamp is not deterministic"
  embedded_environment=$(docker image inspect --format '{{json .Config.Env}}' "$image")
  [[ "$embedded_environment" != *"cardo-smoke"* ]] \
    || fail "$service image contains smoke credentials"
}

assert_repeatable_image() {
  local service=$1
  local first_image=$2
  local repeated_image="cardo-runtime-smoke/$service:${run_id}-repeat"
  local build_cache="cardo-runtime-smoke-${service}-repeat-build-${run_id}"
  local launch_cache="cardo-runtime-smoke-${service}-repeat-launch-${run_id}"
  local first_id repeated_id
  images+=("$repeated_image")
  volumes+=("$build_cache" "$launch_cache")

  maven_with_revision --batch-mode --no-transfer-progress -pl "$service" \
    -DskipTests \
    -Dcardo.image.build-cache="$build_cache" \
    -Dcardo.image.launch-cache="$launch_cache" \
    -Dcardo.image.name="$repeated_image" \
    clean package spring-boot:build-image-no-fork
  assert_image_metadata "$service repeat" "$repeated_image"
  first_id=$(docker image inspect --format '{{.Id}}' "$first_image")
  repeated_id=$(docker image inspect --format '{{.Id}}' "$repeated_image")
  [[ "$first_id" == "$repeated_id" ]] \
    || fail "$service image is not reproducible ($first_id != $repeated_id)"
  echo "reproduced $service image content $first_id"
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

  maven_with_revision --batch-mode --no-transfer-progress -pl "$service" \
    -DskipTests \
    -Dcardo.image.build-cache="$build_cache" \
    -Dcardo.image.launch-cache="$launch_cache" \
    -Dcardo.image.name="$image" \
    clean package spring-boot:build-image-no-fork
  [[ "$(sha256_file "$service/target/$service-$project_version.jar")" \
      == "$(cat "$temporary_directory/$service-jar.sha256")" ]] \
    || fail "$service JAR is not reproducible"
  assert_image_metadata "$service" "$image"
  if [[ "$service" == "identity" ]]; then
    assert_repeatable_image "$service" "$image"
  fi
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
    --env MANAGEMENT_ENDPOINT_HEALTH_SHOW_COMPONENTS=always \
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
if [[ "${GITHUB_EVENT_NAME:-}" == "pull_request" ]]; then
  [[ -n "${GITHUB_EVENT_PATH:-}" && -f "$GITHUB_EVENT_PATH" ]] \
    || fail "GITHUB_EVENT_PATH must identify the pull-request event payload"
  source_revision=$(jq --raw-output '.pull_request.head.sha // empty' "$GITHUB_EVENT_PATH") \
    || fail "could not read the pull-request head revision from $GITHUB_EVENT_PATH"
  checkout_revision=$(git rev-parse HEAD)
  if [[ "$source_revision" == "$checkout_revision" ]]; then
    checkout_source_revision=$checkout_revision
  else
    checkout_source_revision=$(git rev-parse --verify 'HEAD^2^{commit}' 2>/dev/null) \
      || fail "checked-out pull-request merge does not expose its source parent"
  fi
else
  source_revision=$(git rev-parse HEAD)
fi
[[ "$source_revision" =~ ^[0-9a-f]{40}$ ]] \
  || fail "expected source revision is not a full lowercase Git SHA: $source_revision"
if [[ "${GITHUB_EVENT_NAME:-}" == "pull_request" ]]; then
  [[ "$checkout_source_revision" =~ ^[0-9a-f]{40}$ ]] \
    || fail "checked-out pull-request source is not a full lowercase Git SHA: $checkout_source_revision"
  [[ "$source_revision" == "$checkout_source_revision" ]] \
    || fail "pull-request event head $source_revision does not match checked-out source $checkout_source_revision"
fi
maven_with_revision() {
  ./mvnw -DbuildNumber="$source_revision" "$@"
}
maven_source_revision=$(maven_with_revision --quiet --no-transfer-progress initialize help:evaluate \
  -Dexpression=buildNumber -DforceStdout)
[[ "$maven_source_revision" =~ ^[0-9a-f]{40}$ ]] \
  || fail "Maven buildNumber is not a full lowercase Git SHA: $maven_source_revision"
[[ "$maven_source_revision" == "$source_revision" ]] \
  || fail "Maven buildNumber $maven_source_revision does not match expected source revision $source_revision"
start_provider_stub

maven_with_revision --batch-mode --no-transfer-progress -DskipTests install

for service in identity invite billing; do
  maven_with_revision --batch-mode --no-transfer-progress -pl "$service" -DskipTests clean package
  sha256_file "$service/target/$service-$project_version.jar" \
    >"$temporary_directory/$service-jar.sha256"
done

for service in identity invite billing; do
  smoke_jar "$service"
  smoke_image "$service"
done

echo "all Cardo runtime artifacts passed smoke validation"
