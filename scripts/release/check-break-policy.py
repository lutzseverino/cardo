#!/usr/bin/env python3

import argparse
import re
import subprocess


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("marker", choices=("Java-Migration", "OpenAPI-Migration"))
    parser.add_argument("previous_revision")
    parser.add_argument("current_revision")
    args = parser.parse_args()
    revision_range = f"{args.previous_revision}..{args.current_revision}"
    log = subprocess.run(
        ["git", "log", revision_range, "--format=%H%x1f%s%x1f%b%x1e"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    marker_pattern = re.compile(rf"(?m)^{re.escape(args.marker)}:\s*#[1-9][0-9]*\s*$")
    for record in log.split("\x1e"):
        fields = record.strip().split("\x1f")
        if len(fields) != 3:
            continue
        commit, subject, body = fields
        breaking_title = re.match(r"^[a-z]+(?:\([^)]+\))?!:", subject) is not None
        breaking_footer = re.search(r"(?m)^BREAKING CHANGE:\s*\S", body) is not None
        if breaking_title and breaking_footer and marker_pattern.search(body):
            print(f"compatibility break authorized by {commit} with {args.marker}")
            return 0
    print(
        f"incompatible change requires one commit with !, BREAKING CHANGE:, and {args.marker}: #N",
    )
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
