#!/usr/bin/env bash

set -Eeuo pipefail

case ${0##*/} in
  gh)
    [[ $1 == api && $# -eq 2 ]] \
      || { echo "fixture received an unexpected gh command" >&2; exit 1; }
    service=${2##*%2F}
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
    for state in absent private private-omitted public linked linked-other error unknown; do
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
            f"pull:{service}:denied",
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

    anonymous_public="$temporary_directory/anonymous-public"
    mkdir -p "$anonymous_public"
    if FIXTURE_ANONYMOUS_SUCCEEDS=true \
      run_publish after-push-private "$anonymous_public" \
        "$temporary_directory/anonymous-public.json" \
        "$temporary_directory/local/anonymous-public" >/dev/null 2>&1; then
      echo "anonymous digest access after a first push was accepted" >&2
      exit 1
    fi
    jq --exit-status 'keys == ["identity"]' \
      "$temporary_directory/anonymous-public.json" >/dev/null
    [[ -f $anonymous_public/identity && ! -f $anonymous_public/invite && ! -f $anonymous_public/billing ]] \
      || { echo "Identity anonymous-access failure touched a later service" >&2; exit 1; }

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
from pathlib import Path

workflow = Path(".github/workflows/release.yml").read_text()
publish = workflow.split("  publish:", 1)[1].split("\n  verify-private-runtime:", 1)[0]
job = workflow.split("  verify-private-runtime:", 1)[1].split("\n  finalize:", 1)[0]
checker = Path("scripts/release/check-ghcr-package-state.sh").read_text()
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
if 'GH_TOKEN="$GHCR_PUBLISH_TOKEN" gh api' not in checker:
    raise SystemExit("package API calls do not explicitly isolate the protected publish token")
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
if "BP_OCI_SOURCE" in Path("pom.xml").read_text():
    raise SystemExit("runtime images retain the public source-repository link label")
PY

    echo "private image publication fixtures passed"
    ;;
esac
