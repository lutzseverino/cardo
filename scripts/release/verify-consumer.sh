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

printf '%s\n' \
  'package example;' \
  '' \
  'import io.github.lutzseverino.cardo.authorization.AuthorizationAdminClient;' \
  'import io.github.lutzseverino.cardo.authorization.keycloak.KeycloakRequestingPartyTokenClient;' \
  'import io.github.lutzseverino.cardo.authorization.spring.AuthenticatedUserReader;' \
  'import io.github.lutzseverino.cardo.billing.client.BillingEntitlementsClient;' \
  'import io.github.lutzseverino.cardo.billing.client.http.BillingClientAutoConfiguration;' \
  'import io.github.lutzseverino.cardo.common.api.ApiError;' \
  'import io.github.lutzseverino.cardo.common.model.EmailAddress;' \
  'import io.github.lutzseverino.cardo.identity.client.IdentityUsersClient;' \
  'import io.github.lutzseverino.cardo.identity.client.http.IdentityClientAutoConfiguration;' \
  'import io.github.lutzseverino.cardo.identity.productauth.IdentityProductAuthAutoConfiguration;' \
  'import io.github.lutzseverino.cardo.invite.client.InvitationsClient;' \
  'import io.github.lutzseverino.cardo.invite.client.http.InviteClientAutoConfiguration;' \
  '' \
  'final class Consumer {' \
  '  static final Class<?>[] PUBLIC_INTEGRATION = {' \
  '    ApiError.class, EmailAddress.class, AuthorizationAdminClient.class,' \
  '    KeycloakRequestingPartyTokenClient.class, AuthenticatedUserReader.class,' \
  '    IdentityUsersClient.class, IdentityClientAutoConfiguration.class,' \
  '    IdentityProductAuthAutoConfiguration.class, InvitationsClient.class,' \
  '    InviteClientAutoConfiguration.class, BillingEntitlementsClient.class,' \
  '    BillingClientAutoConfiguration.class' \
  '  };' \
  '}' >"$temporary_directory/src/main/java/example/Consumer.java"

./mvnw --batch-mode --no-transfer-progress \
  -f "$temporary_directory/pom.xml" \
  -Dmaven.repo.local="$local_repository" \
  -DoutputFile="$temporary_directory/dependency-tree.txt" \
  compile org.apache.maven.plugins:maven-dependency-plugin:3.9.0:tree

while IFS= read -r artifact; do
  [[ -f "$local_repository/io/github/lutzseverino/cardo/$artifact/$version/$artifact-$version.jar" ]] \
    || { echo "fresh consumer did not resolve $artifact:$version" >&2; exit 1; }
done <release/supported-artifacts.txt

while IFS= read -r artifact; do
  if grep --fixed-strings "io.github.lutzseverino.cardo:$artifact:" \
    "$temporary_directory/dependency-tree.txt" >/dev/null; then
    echo "fresh consumer resolved private artifact $artifact" >&2
    exit 1
  fi
done <release/private-artifacts.txt

contract_jar="$local_repository/io/github/lutzseverino/cardo/cardo-openapi-contracts/$version/cardo-openapi-contracts-$version.jar"
for entry in \
  META-INF/cardo/openapi/common/openapi/errors.yaml \
  META-INF/cardo/openapi/identity/openapi/identity.yaml \
  META-INF/cardo/openapi/invite/openapi/invite.yaml \
  META-INF/cardo/openapi/billing/openapi/billing.yaml; do
  unzip -p "$contract_jar" "$entry" >/dev/null \
    || { echo "public contracts artifact lacks $entry" >&2; exit 1; }
done

echo "fresh standalone consumer compiled against the public Cardo $version surface"
