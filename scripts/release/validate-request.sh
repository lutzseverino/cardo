#!/usr/bin/env bash

set -Eeuo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <version> <full-source-revision>" >&2
  exit 2
fi

root_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root_directory"
version=$1
source_revision=$2
scripts/release/validate-version.py "$version"
[[ $source_revision =~ ^[0-9a-f]{40}$ ]] \
  || { echo "revision must be a full lowercase Git SHA" >&2; exit 1; }
[[ $(git rev-parse HEAD) == "$source_revision" ]] \
  || { echo "checked-out revision differs from release request" >&2; exit 1; }
git fetch --no-tags origin main
[[ $(git rev-parse origin/main) == "$source_revision" ]] \
  || { echo "release revision is not the exact current origin/main" >&2; exit 1; }

if git ls-remote --exit-code --tags origin "refs/tags/v$version" >/dev/null 2>&1; then
  tag_revision=$(git ls-remote --tags origin "refs/tags/v$version^{}" | awk '{print $1}')
  [[ -n $tag_revision && $tag_revision == "$source_revision" ]] \
    || { echo "v$version already identifies a different source revision" >&2; exit 1; }
fi

echo "release request identifies exact main revision $source_revision"
