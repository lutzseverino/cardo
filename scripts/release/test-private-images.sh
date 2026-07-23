#!/usr/bin/env bash

set -Eeuo pipefail

case ${0##*/} in
  gh)
    [[ $1 == api && $# -eq 2 ]] \
      || { echo "fixture received an unexpected gh command" >&2; exit 1; }
    case $2 in
      /user/packages/container/cardo%2Fidentity) service=identity ;;
      /user/packages/container/cardo%2Finvite) service=invite ;;
      /user/packages/container/cardo%2Fbilling) service=billing ;;
      *)
        echo "fixture received an unexpected package endpoint: $2" >&2
        exit 1
        ;;
    esac
    case ${FIXTURE_PACKAGE_STATE:-after-push-private} in
      absent)
        echo "state:$service:absent" >>"${FIXTURE_LOG:?}"
        echo 'gh: Not Found (HTTP 404)' >&2
        exit 1
        ;;
      public)
        echo "state:$service:public" >>"${FIXTURE_LOG:?}"
        printf '%s\n' '{"visibility":"public","repository":null}'
        ;;
      private)
        echo "state:$service:private" >>"${FIXTURE_LOG:?}"
        printf '%s\n' '{"visibility":"private","repository":null}'
        ;;
      private-omitted)
        echo "state:$service:private" >>"${FIXTURE_LOG:?}"
        printf '%s\n' '{"visibility":"private"}'
        ;;
      linked)
        echo "state:$service:linked" >>"${FIXTURE_LOG:?}"
        printf '%s\n' \
          '{"visibility":"private","repository":{"full_name":"lutzseverino/cardo"}}'
        ;;
      linked-other)
        echo "state:$service:linked" >>"${FIXTURE_LOG:?}"
        printf '%s\n' \
          '{"visibility":"private","repository":{"full_name":"lutzseverino/deployment"}}'
        ;;
      linked-empty)
        echo "state:$service:linked-empty" >>"${FIXTURE_LOG:?}"
        printf '%s\n' '{"visibility":"private","repository":{"full_name":""}}'
        ;;
      error)
        echo "state:$service:error" >>"${FIXTURE_LOG:?}"
        echo 'gh: forbidden (HTTP 403)' >&2
        exit 1
        ;;
      unknown)
        echo "state:$service:unknown" >>"${FIXTURE_LOG:?}"
        printf '%s\n' '{"repository":null}'
        ;;
      after-push-public)
        if [[ -f $FIXTURE_REMOTE/$service ]]; then
          echo "state:$service:public" >>"${FIXTURE_LOG:?}"
          printf '%s\n' '{"visibility":"public","repository":null}'
        else
          echo "state:$service:absent" >>"${FIXTURE_LOG:?}"
          echo 'gh: Not Found (HTTP 404)' >&2
          exit 1
        fi
        ;;
      after-push-private)
        if [[ -f $FIXTURE_REMOTE/$service ]]; then
          echo "state:$service:private" >>"${FIXTURE_LOG:?}"
          printf '%s\n' '{"visibility":"private","repository":null}'
        else
          echo "state:$service:absent" >>"${FIXTURE_LOG:?}"
          echo 'gh: Not Found (HTTP 404)' >&2
          exit 1
        fi
        ;;
    esac
    ;;
  docker)
    case "$1 $2" in
      'image inspect')
        reference=$5
        service=${reference#ghcr.io/lutzseverino/cardo/}
        service=${service%%:*}
        if [[ -n ${FIXTURE_LOCAL_STATE:-} && -f $FIXTURE_LOCAL_STATE/$service ]]; then
          cat "$FIXTURE_LOCAL_STATE/$service"
        else
          echo "sha256:$service-local"
        fi
        ;;
      'manifest inspect')
        reference=$4
        service=${reference#ghcr.io/lutzseverino/cardo/}
        service=${service%%:*}
        if [[ ! -f $FIXTURE_REMOTE/$service ]]; then
          echo 'manifest unknown' >&2
          exit 1
        fi
        case $service in identity) digit=1 ;; invite) digit=2 ;; billing) digit=3 ;; esac
        printf '{"Descriptor":{"digest":"sha256:%064d"}}\n' "$digit"
        ;;
      'push '*)
        reference=$2
        service=${reference#ghcr.io/lutzseverino/cardo/}
        service=${service%%:*}
        echo "push:$service" >>"${FIXTURE_LOG:?}"
        printf 'sha256:%s-local\n' "$service" >"$FIXTURE_REMOTE/$service"
        ;;
      'logout ghcr.io')
        echo "logout" >>"${FIXTURE_LOG:?}"
        rm -f "${FIXTURE_AUTH:?}"
        ;;
      'login ghcr.io')
        [[ $3 == --username && -n $4 && $5 == --password-stdin ]] \
          || { echo "fixture received malformed Docker login" >&2; exit 1; }
        IFS= read -r token || [[ -n $token ]]
        [[ -n $token ]] || { echo "fixture login token is empty" >&2; exit 1; }
        echo "login" >>"${FIXTURE_LOG:?}"
        touch "${FIXTURE_AUTH:?}"
        ;;
      'pull '*)
        reference=$2
        service=${reference#ghcr.io/lutzseverino/cardo/}
        service=${service%%[@:]*}
        [[ -f $FIXTURE_REMOTE/$service ]] \
          || { echo 'manifest unknown' >&2; exit 1; }
        if [[ -f ${FIXTURE_AUTH:?} || ${FIXTURE_ANONYMOUS_SUCCEEDS:-false} == true ]]; then
          echo "pull:$service:allowed" >>"${FIXTURE_LOG:?}"
          mkdir -p "${FIXTURE_LOCAL_STATE:?}"
          if [[ $reference != *@* ]]; then
            cp "$FIXTURE_REMOTE/$service" "$FIXTURE_LOCAL_STATE/$service"
          fi
          exit 0
        fi
        echo "pull:$service:denied" >>"${FIXTURE_LOG:?}"
        echo 'denied: requested access to the resource is denied' >&2
        exit 1
        ;;
      *)
        echo "fixture received an unexpected Docker command: $*" >&2
        exit 1
        ;;
    esac
    ;;
  *)
    root_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
    cd "$root_directory"
    temporary_directory=$(mktemp -d "${TMPDIR:-/tmp}/cardo-private-images.XXXXXX")
    trap 'rm -rf "$temporary_directory"' EXIT
    fixture_bin="$temporary_directory/bin"
    mkdir -p "$fixture_bin" "$temporary_directory/remote"
    ln -s "$root_directory/scripts/release/test-private-images.sh" "$fixture_bin/docker"
    ln -s "$root_directory/scripts/release/test-private-images.sh" "$fixture_bin/gh"
    version=1.2.3
    cat >"$temporary_directory/images.json" <<JSON
{"images":[
  {"service":"identity","localContentId":"sha256:identity-local"},
  {"service":"invite","localContentId":"sha256:invite-local"},
  {"service":"billing","localContentId":"sha256:billing-local"}
]}
JSON

    run_publish() {
      local package_state=$1
      local remote=$2
      local output=$3
      local local_state=$4
      shift 4
      mkdir -p "$local_state"
      : >"$remote/events"
      PATH="$fixture_bin:$PATH" GHCR_PUBLISH_TOKEN=fixture \
        FIXTURE_PACKAGE_STATE="$package_state" FIXTURE_REMOTE="$remote" \
        FIXTURE_LOCAL_STATE="$local_state" FIXTURE_AUTH="$remote/auth" \
        FIXTURE_LOG="$remote/events" \
        scripts/release/publish-images.sh "$version" \
          "$temporary_directory/images.json" "$output" "$@"
    }

    state_remote="$temporary_directory/remote/state"
    mkdir -p "$state_remote"
    for state in absent private private-omitted public linked linked-other linked-empty error unknown; do
      if [[ $state == absent || $state == private || $state == private-omitted ]]; then
        expected=success
      else
        expected=failure
      fi
      if PATH="$fixture_bin:$PATH" GHCR_PUBLISH_TOKEN=fixture \
        FIXTURE_PACKAGE_STATE="$state" FIXTURE_REMOTE="$state_remote" \
        FIXTURE_LOG="$state_remote/events" \
        scripts/release/check-ghcr-package-state.sh >/dev/null 2>&1; then
        actual=success
      else
        actual=failure
      fi
      [[ $actual == "$expected" ]] || {
        echo "$state GHCR preflight state unexpectedly returned $actual" >&2
        exit 1
      }
    done
    touch "$state_remote/identity" "$state_remote/invite" "$state_remote/billing"
    PATH="$fixture_bin:$PATH" GHCR_PUBLISH_TOKEN=fixture \
      FIXTURE_PACKAGE_STATE=after-push-private FIXTURE_REMOTE="$state_remote" \
      FIXTURE_LOG="$state_remote/events" \
      scripts/release/check-ghcr-package-state.sh >/dev/null

    fresh_remote="$temporary_directory/remote/fresh"
    mkdir -p "$fresh_remote"
    run_publish after-push-private "$fresh_remote" "$temporary_directory/digests.json" \
      "$temporary_directory/local/fresh"
    jq --exit-status '
      keys == ["billing", "identity", "invite"] and
      all(.[]; test("^sha256:[0-9]{64}$"))
    ' "$temporary_directory/digests.json" >/dev/null
    python3 - "$fresh_remote/events" <<'PY'
import pathlib
import sys

events = pathlib.Path(sys.argv[1]).read_text().splitlines()
expected = []
for service in ("identity", "invite", "billing"):
    expected.extend(
        [
            "login",
            f"state:{service}:absent",
            f"push:{service}",
            f"state:{service}:private",
            "logout",
        ]
    )
if events != expected:
    if events != [*expected, "logout"]:
        raise SystemExit(f"per-service private publication sequence differs: {events!r}")
PY

    run_publish after-push-private "$fresh_remote" "$temporary_directory/unrecorded.json" \
      "$temporary_directory/local/unrecorded"

    different_remote="$temporary_directory/remote/different"
    mkdir -p "$different_remote"
    printf 'sha256:different\n' >"$different_remote/identity"
    if run_publish after-push-private "$different_remote" "$temporary_directory/different.json" \
      "$temporary_directory/local/different" >/dev/null 2>&1; then
      echo "existing tag with different unrecorded content was accepted" >&2
      exit 1
    fi

    recorded_remote="$temporary_directory/remote/recorded"
    mkdir -p "$recorded_remote"
    printf 'sha256:identity-local\n' >"$recorded_remote/identity"
    cat >"$temporary_directory/recorded-manifest.json" <<JSON
{"images":[
  {"service":"identity","digest":"sha256:$(printf '%064d' 9)"},
  {"service":"invite","digest":null},
  {"service":"billing","digest":null}
]}
JSON
    if run_publish after-push-private "$recorded_remote" "$temporary_directory/recorded.json" \
      "$temporary_directory/local/recorded" \
      "$temporary_directory/recorded-manifest.json" >/dev/null 2>&1; then
      echo "existing tag with a digest differing from the release manifest was accepted" >&2
      exit 1
    fi

    partial_remote="$temporary_directory/remote/partial"
    mkdir -p "$partial_remote"
    printf 'sha256:identity-local\n' >"$partial_remote/identity"
    printf 'sha256:invite-local\n' >"$partial_remote/invite"
    run_publish after-push-private "$partial_remote" "$temporary_directory/partial.json" \
      "$temporary_directory/local/partial"
    [[ -f $partial_remote/billing ]] \
      || { echo "partial image state did not resume the missing push" >&2; exit 1; }
    jq --exit-status 'keys == ["billing", "identity", "invite"]' \
      "$temporary_directory/partial.json" >/dev/null

    after_push="$temporary_directory/after-push"
    mkdir -p "$after_push"
    if run_publish after-push-public "$after_push" \
      "$temporary_directory/after-push.json" \
      "$temporary_directory/local/after-push" \
      >"$temporary_directory/after-push.log" 2>&1; then
      echo "public visibility after a first push was accepted" >&2
      exit 1
    fi
    jq --exit-status 'keys == ["identity"]' \
      "$temporary_directory/after-push.json" >/dev/null
    identity_digest=$(jq --raw-output .identity "$temporary_directory/after-push.json")
    grep --fixed-strings "recorded identity GHCR digest $identity_digest" \
      "$temporary_directory/after-push.log" >/dev/null \
      || { echo "Identity failure log omitted its recorded digest" >&2; exit 1; }
    [[ -f $after_push/identity && ! -f $after_push/invite && ! -f $after_push/billing ]] \
      || { echo "Identity visibility failure touched a later service" >&2; exit 1; }

    verify_fixture="$temporary_directory/verify-fixture"
    mkdir -p "$verify_fixture/scripts/release"
    cp scripts/release/verify-private-release.sh "$verify_fixture/scripts/release/"
    printf '%s\n' '#!/usr/bin/env bash' 'exit 0' \
      >"$verify_fixture/scripts/release/verify-consumer.sh"
    chmod +x "$verify_fixture/scripts/release/"*.sh
    cat >"$verify_fixture/manifest.json" <<JSON
{"version":"$version","images":[
  {"service":"identity","reference":"ghcr.io/lutzseverino/cardo/identity@sha256:$(printf '%064d' 1)"},
  {"service":"invite","reference":"ghcr.io/lutzseverino/cardo/invite@sha256:$(printf '%064d' 2)"},
  {"service":"billing","reference":"ghcr.io/lutzseverino/cardo/billing@sha256:$(printf '%064d' 3)"}
]}
JSON
    if env -u GHCR_PULL_TOKEN PATH="$fixture_bin:$PATH" \
      FIXTURE_AUTH="$temporary_directory/docker-auth" \
      "$verify_fixture/scripts/release/verify-private-release.sh" \
        "$verify_fixture/manifest.json" lutzseverino >/dev/null 2>&1; then
      echo "private verification accepted a missing external pull token" >&2
      exit 1
    fi
    PATH="$fixture_bin:$PATH" GHCR_PULL_TOKEN=fixture \
      FIXTURE_AUTH="$temporary_directory/docker-auth" FIXTURE_REMOTE="$fresh_remote" \
      FIXTURE_LOCAL_STATE="$temporary_directory/local/verify" \
      FIXTURE_LOG="$temporary_directory/verify-events" \
      "$verify_fixture/scripts/release/verify-private-release.sh" \
        "$verify_fixture/manifest.json" lutzseverino
    if PATH="$fixture_bin:$PATH" GHCR_PULL_TOKEN=fixture \
      FIXTURE_AUTH="$temporary_directory/docker-auth" FIXTURE_ANONYMOUS_SUCCEEDS=true \
      FIXTURE_REMOTE="$fresh_remote" FIXTURE_LOCAL_STATE="$temporary_directory/local/verify" \
      FIXTURE_LOG="$temporary_directory/verify-events" \
      "$verify_fixture/scripts/release/verify-private-release.sh" \
        "$verify_fixture/manifest.json" deployment-fixture >/dev/null 2>&1; then
      echo "anonymous private-image access was accepted" >&2
      exit 1
    fi

    python3 - <<'PY'
import os
import subprocess
import textwrap
from pathlib import Path

workflow = Path(".github/workflows/release.yml").read_text()
manual_workflow = Path(
    ".github/workflows/verify-published-private-runtime.yml"
).read_text()
candidate = workflow.split("  candidate:", 1)[1].split("\n  publish:", 1)[0]
publish = workflow.split("  publish:", 1)[1].split("\n  verify-private-runtime:", 1)[0]
job = workflow.split("  verify-private-runtime:", 1)[1].split("\n  finalize:", 1)[0]
checker = Path("scripts/release/check-ghcr-package-state.sh").read_text()
runtime_smoke = Path("scripts/smoke-runtime-artifacts.sh").read_text()
runtime_docs = Path("docs/reference/runtime-artifacts.md").read_text()
incident_guard = "- name: Reject non-resumable partial prereleases"
candidate_checkout = "- name: Check out exact revision"
if candidate.index(incident_guard) > candidate.index(candidate_checkout):
    raise SystemExit("partial-prerelease guard does not precede candidate checkout")
guard_step = candidate.split(incident_guard, 1)[1].split(candidate_checkout, 1)[0]
guard = textwrap.dedent(guard_step.split("        run: |\n", 1)[1])
for value in [
    'case "$RELEASE_VERSION" in',
    "0.1.0-rc.1)",
    "0.1.0-rc.2)",
    "publish 0.1.0-rc.3 instead",
]:
    if value not in guard:
        raise SystemExit(f"pre-checkout partial-prerelease guard lacks: {value}")
for version, accepted in [
    ("0.1.0-rc.1", False),
    ("0.1.0-rc.2", False),
    ("0.1.0-rc.3", True),
]:
    result = subprocess.run(
        ["bash", "-Eeuo", "pipefail", "-c", guard],
        env={**os.environ, "RELEASE_VERSION": version},
        capture_output=True,
        text=True,
    )
    if (result.returncode == 0) != accepted:
        raise SystemExit(
            f"pre-checkout partial-prerelease guard returned "
            f"{result.returncode} for {version}"
        )
preflight = "run: scripts/release/check-ghcr-package-state.sh"
central_staging = "- name: Rebuild signed Central bundle"
central_upload = "- name: Upload Central bundle for manual publication"
if not (publish.index(preflight) < publish.index(central_staging) < publish.index(central_upload)):
    raise SystemExit("GHCR package preflight does not run before Central staging and upload")
required_publish = [
    "GH_TOKEN: ${{ github.token }}",
    "GHCR_PUBLISH_TOKEN: ${{ secrets.GHCR_PUBLISH_TOKEN }}",
    'scripts/release/publish-images.sh "$RELEASE_VERSION"',
    "- name: Preserve partial image digest evidence",
    "if: failure() && steps.central.outputs.state == 'published'",
    "path: ${{ runner.temp }}/image-digests.json",
]
for value in required_publish:
    if value not in publish:
        raise SystemExit(f"publish job lacks protected GHCR credential policy: {value}")
authenticated_package_read = (
    'GH_TOKEN="$GHCR_PUBLISH_TOKEN" gh api \\\n'
    '    "/user/packages/container/cardo%2F$service"'
)
if authenticated_package_read not in checker:
    raise SystemExit(
        "package API calls do not use the protected token with the authenticated "
        "current-user endpoint"
    )
if "/users/lutzseverino/packages/container/" in checker:
    raise SystemExit("package API calls retain the public-owner endpoint")
for forbidden in [
    "packages: write",
    "password: ${{ github.token }}",
    "GHCR_PUBLISH_TOKEN: ${{ github.token }}",
    "push-to-registry: true",
]:
    if forbidden in workflow:
        raise SystemExit(f"workflow retains unsafe package publication authority: {forbidden}")
for service in ["identity", "invite", "billing"]:
    attestation = publish.split(f"- name: Attest {service.title()} image", 1)[1].split(
        "\n      - name:", 1
    )[0]
    for value in [
        f"subject-name: ghcr.io/lutzseverino/cardo/{service}",
        "subject-digest:",
        "push-to-registry: false",
    ]:
        if value not in attestation:
            raise SystemExit(
                f"{service} lacks a non-registry digest attestation: {value}"
            )
required = [
    "    environment: release",
    "      contents: read",
    "GHCR_PULL_TOKEN: ${{ secrets.GHCR_PULL_TOKEN }}",
    '"$RUNNER_TEMP/release/release-manifest.json" lutzseverino',
]
for value in required:
    if value not in job:
        raise SystemExit(f"private verification job lacks required external pull-token policy: {value}")
if "packages:" in job or "GHCR_PULL_TOKEN: ${{ github.token }}" in job:
    raise SystemExit("private verification job grants package access to its automatic token")
java_setup = (
    "uses: actions/setup-java@03ad4de0992f5dab5e18fcb136590ce7c4a0ac95 "
    "# v5.6.0"
)
verifier = "scripts/release/verify-private-release.sh"
for name, verifier_job in [
    ("release", job),
    ("published-release", manual_workflow),
]:
    for value in [java_setup, "distribution: temurin", "java-version: 21"]:
        if value not in verifier_job:
            raise SystemExit(f"{name} private verifier lacks Java 21 setup: {value}")
    if verifier_job.index(java_setup) > verifier_job.index(verifier):
        raise SystemExit(f"{name} private verifier sets up Java after verification")

manual_required = [
    "name: Verify published private runtime",
    "      version:",
    "      revision:",
    "        required: true",
    "  RELEASE_REVISION: ${{ inputs.revision }}",
    "  RELEASE_VERSION: ${{ inputs.version }}",
    "    environment: release",
    "          fetch-depth: 0",
    "          persist-credentials: false",
    "          ref: ${{ github.sha }}",
    'gh release view "v$RELEASE_VERSION" --json isDraft --jq .isDraft',
    '[[ $draft == false ]]',
    "--pattern release-manifest.json --pattern central-bundle.zip",
    "scripts/release/validate-request.sh --published-release",
    '"$RELEASE_VERSION" "$RELEASE_REVISION"',
    '"$RUNNER_TEMP/release/central-bundle.zip"',
    "GHCR_PULL_TOKEN: ${{ secrets.GHCR_PULL_TOKEN }}",
]
for value in manual_required:
    if value not in manual_workflow:
        raise SystemExit(f"published-release verifier lacks immutable policy: {value}")
manual_permissions = manual_workflow.split("\npermissions:\n", 1)[1].split(
    "\nenv:\n", 1
)[0]
if manual_permissions.strip() != "contents: read" or "\n    permissions:" in manual_workflow:
    raise SystemExit("published-release verifier grants more than contents read")
manual_checkout = manual_workflow.split(
    "      - name: Check out trusted verifier", 1
)[1].split("      - name: Set up Java", 1)[0]
if "inputs.revision" in manual_checkout or "RELEASE_REVISION" in manual_checkout:
    raise SystemExit("published-release verifier checks out input-selected code")
manual_verifier_step = manual_workflow.split(
    "      - name: Prove private image access boundary", 1
)[1]
if "GH_TOKEN:" in manual_verifier_step or "GITHUB_TOKEN:" in manual_verifier_step:
    raise SystemExit("published-release verifier mixes the release and GHCR tokens")
for forbidden in [
    "packages:",
    "contents: write",
    "GHCR_PUBLISH_TOKEN",
    "gh release create",
    "gh release edit",
    "gh release upload",
    "git push",
    "docker push",
]:
    if forbidden in manual_workflow:
        raise SystemExit(
            f"published-release verifier retains mutation authority: {forbidden}"
        )
if "BP_OCI_SOURCE" in Path("pom.xml").read_text():
    raise SystemExit("runtime images retain the public source-repository link label")
if 'has("org.opencontainers.image.source") | not' not in runtime_smoke:
    raise SystemExit("runtime smoke does not reject the source-repository label")
if "- `org.opencontainers.image.source`" in runtime_docs:
    raise SystemExit("runtime artifact reference still promises the forbidden source label")
PY

    echo "private image publication fixtures passed"
    ;;
esac
