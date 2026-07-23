#!/usr/bin/env python3

import re
import sys


CORE = r"(?:0|[1-9][0-9]*)"
IDENTIFIER = r"(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)"
SEMVER = re.compile(
    rf"{CORE}\.{CORE}\.{CORE}(?:-(?P<prerelease>{IDENTIFIER}(?:\.{IDENTIFIER})*))?"
)

if len(sys.argv) not in {2, 3} or (
    len(sys.argv) == 3 and sys.argv[2] != "--github-prerelease"
):
    print(
        f"usage: {sys.argv[0]} <version> [--github-prerelease]",
        file=sys.stderr,
    )
    raise SystemExit(2)
version = sys.argv[1]
match = SEMVER.fullmatch(version)
prerelease = match.group("prerelease") if match else None
if match is None or (
    prerelease is not None
    and "snapshot" in {identifier.lower() for identifier in prerelease.split(".")}
):
    print(
        f"release version must be exact non-SNAPSHOT SemVer without build metadata: {version}",
        file=sys.stderr,
    )
    raise SystemExit(1)

if len(sys.argv) == 3:
    print("true" if prerelease else "false")
