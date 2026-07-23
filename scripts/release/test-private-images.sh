#!/usr/bin/env bash

set -Eeuo pipefail

case ${0##*/} in
  gh)
    [[ $1 == api && $3 == --jq && $4 == .visibility ]] \
      || { echo "fixture received an unexpected gh command" >&2; exit 1; }
    service=${2##*%2F}
    case ${FIXTURE_VISIBILITY:-private} in
      public)
        echo public
        ;;
      error)
        echo 'gh: forbidden (HTTP 403)' >&2
        exit 1
        ;;
      after-push-public)
        if [[ -f $FIXTURE_REMOTE/$service ]]; then
          echo public
        else
          echo 'gh: Not Found (HTTP 404)' >&2
          exit 1
        fi
        ;;
      private)
        if [[ -f $FIXTURE_REMOTE/$service ]]; then
          echo private
        else
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
        printf 'sha256:%s-local\n' "$service" >"$FIXTURE_REMOTE/$service"
        ;;
      'logout ghcr.io')
        rm -f "${FIXTURE_AUTH:?}"
        ;;
      'login ghcr.io')
        [[ $3 == --username && -n $4 && $5 == --password-stdin ]] \
          || { echo "fixture received malformed Docker login" >&2; exit 1; }
        IFS= read -r token || [[ -n $token ]]
        [[ -n $token ]] || { echo "fixture login token is empty" >&2; exit 1; }
        touch "${FIXTURE_AUTH:?}"
        ;;
      'pull '*)
        reference=$2
        service=${reference#ghcr.io/lutzseverino/cardo/}
        service=${service%%[@:]*}
        if [[ -n ${FIXTURE_REMOTE:-} ]]; then
          [[ -f $FIXTURE_REMOTE/$service ]] \
            || { echo 'manifest unknown' >&2; exit 1; }
          mkdir -p "${FIXTURE_LOCAL_STATE:?}"
          cp "$FIXTURE_REMOTE/$service" "$FIXTURE_LOCAL_STATE/$service"
          exit 0
        fi
        if [[ -f ${FIXTURE_AUTH:?} || ${FIXTURE_ANONYMOUS_SUCCEEDS:-false} == true ]]; then
          exit 0
        fi
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
      local visibility=$1
      local remote=$2
      local output=$3
      local local_state=$4
      shift 4
      mkdir -p "$local_state"
      PATH="$fixture_bin:$PATH" GH_TOKEN=fixture \
        FIXTURE_VISIBILITY="$visibility" FIXTURE_REMOTE="$remote" \
        FIXTURE_LOCAL_STATE="$local_state" \
        scripts/release/publish-images.sh "$version" \
          "$temporary_directory/images.json" "$output" "$@"
    }

    fresh_remote="$temporary_directory/remote/fresh"
    mkdir -p "$fresh_remote"
    run_publish private "$fresh_remote" "$temporary_directory/digests.json" \
      "$temporary_directory/local/fresh"
    jq --exit-status '
      keys == ["billing", "identity", "invite"] and
      all(.[]; test("^sha256:[0-9]{64}$"))
    ' "$temporary_directory/digests.json" >/dev/null

    run_publish private "$fresh_remote" "$temporary_directory/unrecorded.json" \
      "$temporary_directory/local/unrecorded"

    different_remote="$temporary_directory/remote/different"
    mkdir -p "$different_remote"
    printf 'sha256:different\n' >"$different_remote/identity"
    if run_publish private "$different_remote" "$temporary_directory/different.json" \
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
    if run_publish private "$recorded_remote" "$temporary_directory/recorded.json" \
      "$temporary_directory/local/recorded" \
      "$temporary_directory/recorded-manifest.json" >/dev/null 2>&1; then
      echo "existing tag with a digest differing from the release manifest was accepted" >&2
      exit 1
    fi

    partial_remote="$temporary_directory/remote/partial"
    mkdir -p "$partial_remote"
    printf 'sha256:identity-local\n' >"$partial_remote/identity"
    printf 'sha256:invite-local\n' >"$partial_remote/invite"
    run_publish private "$partial_remote" "$temporary_directory/partial.json" \
      "$temporary_directory/local/partial"
    [[ -f $partial_remote/billing ]] \
      || { echo "partial image state did not resume the missing push" >&2; exit 1; }
    jq --exit-status 'keys == ["billing", "identity", "invite"]' \
      "$temporary_directory/partial.json" >/dev/null

    for visibility in public error; do
      if run_publish "$visibility" "$fresh_remote" \
        "$temporary_directory/$visibility.json" \
        "$temporary_directory/local/$visibility" >/dev/null 2>&1; then
        echo "$visibility package visibility was accepted" >&2
        exit 1
      fi
    done

    after_push="$temporary_directory/after-push"
    mkdir -p "$after_push"
    if run_publish after-push-public "$after_push" \
      "$temporary_directory/after-push.json" \
      "$temporary_directory/local/after-push" >/dev/null 2>&1; then
      echo "public visibility after a first push was accepted" >&2
      exit 1
    fi

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
      FIXTURE_AUTH="$temporary_directory/docker-auth" \
      "$verify_fixture/scripts/release/verify-private-release.sh" \
        "$verify_fixture/manifest.json" lutzseverino
    if PATH="$fixture_bin:$PATH" GHCR_PULL_TOKEN=fixture \
      FIXTURE_AUTH="$temporary_directory/docker-auth" FIXTURE_ANONYMOUS_SUCCEEDS=true \
      "$verify_fixture/scripts/release/verify-private-release.sh" \
        "$verify_fixture/manifest.json" deployment-fixture >/dev/null 2>&1; then
      echo "anonymous private-image access was accepted" >&2
      exit 1
    fi

    python3 - <<'PY'
from pathlib import Path

workflow = Path(".github/workflows/release.yml").read_text()
job = workflow.split("  verify-private-runtime:", 1)[1].split("\n  finalize:", 1)[0]
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
PY

    echo "private image publication fixtures passed"
    ;;
esac
