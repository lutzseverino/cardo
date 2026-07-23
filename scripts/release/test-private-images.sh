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
        echo "sha256:$service-local"
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
        touch "$FIXTURE_REMOTE/$service"
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
      PATH="$fixture_bin:$PATH" GH_TOKEN=fixture \
        FIXTURE_VISIBILITY="$visibility" FIXTURE_REMOTE="$remote" \
        scripts/release/publish-images.sh "$version" \
          "$temporary_directory/images.json" "$output"
    }

    run_publish private "$temporary_directory/remote" "$temporary_directory/digests.json"
    jq --exit-status '
      keys == ["billing", "identity", "invite"] and
      all(.[]; test("^sha256:[0-9]{64}$"))
    ' "$temporary_directory/digests.json" >/dev/null

    for visibility in public error; do
      if run_publish "$visibility" "$temporary_directory/remote" \
        "$temporary_directory/$visibility.json" >/dev/null 2>&1; then
        echo "$visibility package visibility was accepted" >&2
        exit 1
      fi
    done

    after_push="$temporary_directory/after-push"
    mkdir -p "$after_push"
    if run_publish after-push-public "$after_push" \
      "$temporary_directory/after-push.json" >/dev/null 2>&1; then
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
    PATH="$fixture_bin:$PATH" GHCR_PULL_TOKEN=fixture \
      FIXTURE_AUTH="$temporary_directory/docker-auth" \
      "$verify_fixture/scripts/release/verify-private-release.sh" \
        "$verify_fixture/manifest.json" deployment-fixture
    if PATH="$fixture_bin:$PATH" GHCR_PULL_TOKEN=fixture \
      FIXTURE_AUTH="$temporary_directory/docker-auth" FIXTURE_ANONYMOUS_SUCCEEDS=true \
      "$verify_fixture/scripts/release/verify-private-release.sh" \
        "$verify_fixture/manifest.json" deployment-fixture >/dev/null 2>&1; then
      echo "anonymous private-image access was accepted" >&2
      exit 1
    fi

    echo "private image publication fixtures passed"
    ;;
esac
