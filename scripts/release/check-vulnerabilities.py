#!/usr/bin/env python3

import argparse
import datetime as dt
import json
import pathlib


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("alerts", type=pathlib.Path)
    parser.add_argument("exceptions", type=pathlib.Path)
    parser.add_argument("output", type=pathlib.Path)
    args = parser.parse_args()
    alerts = json.loads(args.alerts.read_text())
    exceptions = json.loads(args.exceptions.read_text())
    today = dt.date.today()
    active_exceptions = {}
    for exception in exceptions.get("exceptions", []):
        required = {"alertNumber", "owner", "reason", "trackingIssue", "expires"}
        missing = required - exception.keys()
        if missing:
            raise SystemExit(f"vulnerability exception lacks {sorted(missing)}")
        expires = dt.date.fromisoformat(exception["expires"])
        if expires > today + dt.timedelta(days=30):
            raise SystemExit(
                f"vulnerability exception {exception['alertNumber']} exceeds the 30-day review bound"
            )
        if expires >= today:
            active_exceptions[exception["alertNumber"]] = exception
    blocking = []
    for alert in alerts:
        severity = alert.get("security_advisory", {}).get("severity", "").lower()
        if alert.get("state") == "open" and severity in {"high", "critical"}:
            if alert.get("number") not in active_exceptions:
                blocking.append(alert.get("number"))
    report = {
        "schemaVersion": 1,
        "generatedAt": dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat(),
        "openAlertCount": sum(alert.get("state") == "open" for alert in alerts),
        "blockingAlertNumbers": blocking,
        "activeExceptions": list(active_exceptions.values()),
    }
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    if blocking:
        print(f"untriaged high/critical Dependabot alerts block release: {blocking}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
