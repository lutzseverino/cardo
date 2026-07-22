#!/usr/bin/env python3

import argparse
import hashlib
import json
import pathlib


GROUP_PATH = pathlib.Path("io/github/lutzseverino/cardo")


def sha256(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("version")
    parser.add_argument("source_revision")
    parser.add_argument("candidate_directory", type=pathlib.Path)
    parser.add_argument("--previous-version")
    parser.add_argument("--previous-revision")
    parser.add_argument("--image-digests", type=pathlib.Path)
    args = parser.parse_args()
    candidate = args.candidate_directory.resolve()
    supported = pathlib.Path("release/supported-artifacts.txt").read_text().splitlines()
    repository = candidate / "maven-repository"
    images = json.loads((candidate / "images/images.json").read_text())["images"]
    digest_by_service = {}
    if args.image_digests:
        digest_by_service = json.loads(args.image_digests.read_text())
    for image in images:
        image["digest"] = digest_by_service.get(image["service"])
        image["reference"] = f'{image["name"]}@{image["digest"]}' if image["digest"] else None

    artifacts = []
    for artifact in supported:
        relative = GROUP_PATH / artifact / args.version / f"{artifact}-{args.version}.jar"
        path = repository / relative
        artifacts.append(
            {
                "artifactId": artifact,
                "coordinate": f"io.github.lutzseverino.cardo:{artifact}:{args.version}",
                "path": relative.as_posix(),
                "sha256": sha256(path),
                "url": f"https://repo1.maven.org/maven2/{relative.as_posix()}",
            }
        )

    openapi = []
    for service in ("identity", "invite", "billing"):
        path = candidate / "openapi" / f"{service}.yaml"
        openapi.append(
            {
                "service": service,
                "asset": f"{service}-openapi.yaml",
                "sha256": sha256(path),
                "url": (
                    f"https://github.com/lutzseverino/cardo/releases/download/"
                    f"v{args.version}/{service}-openapi.yaml"
                ),
            }
        )

    bundle = candidate / "central-bundle.zip"
    manifest = {
        "schemaVersion": 1,
        "version": args.version,
        "sourceRevision": args.source_revision,
        "tag": f"v{args.version}",
        "previousStable": (
            {"version": args.previous_version, "sourceRevision": args.previous_revision}
            if args.previous_version
            else None
        ),
        "maven": {
            "groupId": "io.github.lutzseverino.cardo",
            "bom": f"io.github.lutzseverino.cardo:cardo-bom:{args.version}",
            "repository": "https://repo1.maven.org/maven2",
            "centralBundle": {"asset": bundle.name, "sha256": sha256(bundle)},
            "artifacts": artifacts,
        },
        "openapiBundles": openapi,
        "images": images,
        "vulnerabilityReport": "vulnerability-report.json",
    }
    (candidate / "release-manifest.json").write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
