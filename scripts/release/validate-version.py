#!/usr/bin/env python3

import re
import sys


CORE = r"(?:0|[1-9][0-9]*)"
IDENTIFIER = r"(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)"
SEMVER = re.compile(rf"{CORE}\.{CORE}\.{CORE}(?:-{IDENTIFIER}(?:\.{IDENTIFIER})*)?")

if len(sys.argv) != 2:
    print(f"usage: {sys.argv[0]} <version>", file=sys.stderr)
    raise SystemExit(2)
version = sys.argv[1]
prerelease_identifiers = version.partition("-")[2].split(".")
if not SEMVER.fullmatch(version) or "snapshot" in {
    identifier.lower() for identifier in prerelease_identifiers
}:
    print(
        f"release version must be exact non-SNAPSHOT SemVer without build metadata: {version}",
        file=sys.stderr,
    )
    raise SystemExit(1)
