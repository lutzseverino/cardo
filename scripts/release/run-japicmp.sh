#!/usr/bin/env bash

set -Eeuo pipefail

root_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root_directory"

if [[ $# -ne 3 ]]; then
  echo "usage: $0 <old-jar> <new-jar> <report-directory>" >&2
  exit 2
fi

old_jar=$(cd "$(dirname "$1")" && pwd)/$(basename "$1")
new_jar=$(cd "$(dirname "$2")" && pwd)/$(basename "$2")
report_directory=$(mkdir -p "$3" && cd "$3" && pwd)

[[ -f $old_jar ]] || { echo "old JAR not found: $old_jar" >&2; exit 1; }
[[ -f $new_jar ]] || { echo "new JAR not found: $new_jar" >&2; exit 1; }

version=$(./mvnw -N -q -Dexpression=japicmp-maven-plugin.version -DforceStdout help:evaluate)
local_repository=$(./mvnw -N -q -Dexpression=settings.localRepository -DforceStdout help:evaluate)
./mvnw -N --batch-mode --no-transfer-progress dependency:get \
  -Dartifact="com.github.siom79.japicmp:japicmp:$version:jar:jar-with-dependencies" \
  -Dtransitive=false >/dev/null
japicmp="$local_repository/com/github/siom79/japicmp/japicmp/$version/japicmp-$version-jar-with-dependencies.jar"
report="$report_directory/japicmp.xml"

java -jar "$japicmp" \
  --old "$old_jar" \
  --new "$new_jar" \
  -a public \
  -m \
  --ignore-missing-classes \
  --exclude 'io.github.lutzseverino.cardo.identity.client.http.generated.*;io.github.lutzseverino.cardo.invite.client.http.generated.*;io.github.lutzseverino.cardo.billing.client.http.generated.*' \
  --xml-file "$report" \
  >"$report_directory/japicmp.diff"

[[ -f $report ]] || { echo "japicmp did not produce an XML report" >&2; exit 1; }

python3 - "$report" <<'PY'
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
breaking = []
for element in root.iter():
    binary = element.attrib.get("binaryCompatible", "true").lower()
    source = element.attrib.get("sourceCompatible", "true").lower()
    if binary == "false" or source == "false":
        breaking.append(element.attrib.get("fullyQualifiedName") or element.attrib.get("name") or element.tag)
if breaking:
    print("incompatible public Java changes: " + ", ".join(sorted(set(breaking))[:20]), file=sys.stderr)
    raise SystemExit(10)
PY
