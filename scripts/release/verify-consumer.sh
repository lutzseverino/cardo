#!/usr/bin/env bash

set -Eeuo pipefail

root_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root_directory"

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <version> <Maven-repository-path-or-url>" >&2
  exit 2
fi

version=$1
if [[ $2 == *://* ]]; then
  repository_url=$2
else
  staged_repository=$(cd "$2" && pwd)
  repository_url=$(python3 - "$staged_repository" <<'PY'
import pathlib
import sys
print(pathlib.Path(sys.argv[1]).as_uri())
PY
)
fi
temporary_directory=$(mktemp -d "${TMPDIR:-/tmp}/cardo-consumer.XXXXXX")
trap 'rm -rf "$temporary_directory"' EXIT
local_repository="$temporary_directory/repository"
mkdir -p "$temporary_directory/src/main/java/example" "$local_repository"

python3 - "$version" "$repository_url" "$temporary_directory/pom.xml" <<'PY'
import pathlib
import sys

version, repository, output = sys.argv[1:]
artifacts = pathlib.Path("release/supported-artifacts.txt").read_text().splitlines()
dependencies = "\n".join(
    f"""    <dependency>
      <groupId>io.github.lutzseverino.cardo</groupId>
      <artifactId>{artifact}</artifactId>
    </dependency>"""
    for artifact in artifacts
)
pathlib.Path(output).write_text(f"""<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<project xmlns=\"http://maven.apache.org/POM/4.0.0\">
  <modelVersion>4.0.0</modelVersion>
  <groupId>example</groupId>
  <artifactId>cardo-release-consumer</artifactId>
  <version>1</version>
  <properties><maven.compiler.release>21</maven.compiler.release></properties>
  <repositories>
    <repository><id>cardo-release</id><url>{repository}</url></repository>
  </repositories>
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>io.github.lutzseverino.cardo</groupId>
        <artifactId>cardo-bom</artifactId>
        <version>{version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
  <dependencies>
{dependencies}
  </dependencies>
</project>
""")
PY

printf '%s\n' 'package example;' 'final class Consumer { }' \
  >"$temporary_directory/src/main/java/example/Consumer.java"

./mvnw --batch-mode --no-transfer-progress \
  -f "$temporary_directory/pom.xml" \
  -Dmaven.repo.local="$local_repository" \
  compile

while IFS= read -r artifact; do
  [[ -f "$local_repository/io/github/lutzseverino/cardo/$artifact/$version/$artifact-$version.jar" ]] \
    || { echo "fresh consumer did not resolve $artifact:$version" >&2; exit 1; }
done <release/supported-artifacts.txt

echo "fresh standalone consumer resolved and compiled against Cardo $version"
