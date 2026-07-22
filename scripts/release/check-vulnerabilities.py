#!/usr/bin/env python3

import argparse
import datetime as dt
import json
import pathlib
import re


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("alerts", type=pathlib.Path)
    parser.add_argument("exceptions", type=pathlib.Path)
    parser.add_argument("output", type=pathlib.Path)
    args = parser.parse_args()
    alerts = json.loads(args.alerts.read_text())
    exceptions = json.loads(args.exceptions.read_text())
    if not isinstance(alerts, list):
        raise SystemExit("Dependabot alerts must be a JSON array")
    if (
        not isinstance(exceptions, dict)
        or type(exceptions.get("schemaVersion")) is not int
        or exceptions["schemaVersion"] != 1
    ):
        raise SystemExit("vulnerability exceptions must use schemaVersion 1")
    exception_entries = exceptions.get("exceptions")
    if not isinstance(exception_entries, list):
        raise SystemExit("vulnerability exceptions must be a JSON array")
    today = dt.date.today()
    active_exceptions = {}
    seen_alert_numbers = set()
    for exception in exception_entries:
        if not isinstance(exception, dict):
            raise SystemExit("each vulnerability exception must be a JSON object")
        required = {"alertNumber", "owner", "reason", "trackingIssue", "expires"}
        missing = required - exception.keys()
        if missing:
            raise SystemExit(f"vulnerability exception lacks {sorted(missing)}")
        alert_number = exception["alertNumber"]
        if not isinstance(alert_number, int) or isinstance(alert_number, bool) or alert_number < 1:
            raise SystemExit("vulnerability exception alertNumber must be a positive integer")
        if alert_number in seen_alert_numbers:
            raise SystemExit(f"duplicate vulnerability exception for alert {alert_number}")
        seen_alert_numbers.add(alert_number)
        for field in ("owner", "reason"):
            if not isinstance(exception[field], str) or not exception[field].strip():
                raise SystemExit(f"vulnerability exception {field} must be a nonempty string")
        tracking_issue = exception["trackingIssue"]
        if not isinstance(tracking_issue, str) or not re.fullmatch(r"#[1-9][0-9]*", tracking_issue):
            raise SystemExit("vulnerability exception trackingIssue must have format #N")
        if not isinstance(exception["expires"], str):
            raise SystemExit("vulnerability exception expires must be an ISO date string")
        try:
            expires = dt.date.fromisoformat(exception["expires"])
        except ValueError as error:
            raise SystemExit("vulnerability exception expires must be an ISO date") from error
        if expires > today + dt.timedelta(days=30):
            raise SystemExit(
                f"vulnerability exception {exception['alertNumber']} exceeds the 30-day review bound"
            )
        if expires >= today:
            active_exceptions[alert_number] = exception
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
