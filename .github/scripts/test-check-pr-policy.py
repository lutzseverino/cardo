#!/usr/bin/env python3
"""Focused tests for intentional OpenAPI break approval."""

from __future__ import annotations

import importlib.util
import pathlib
import unittest


POLICY_PATH = pathlib.Path(__file__).with_name("check-pr-policy.py")
SPEC = importlib.util.spec_from_file_location("check_pr_policy", POLICY_PATH)
assert SPEC and SPEC.loader
POLICY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(POLICY)

BODY = """## Summary
Summary.
## Motivation
Motivation.
## Impact
Impact.
## Validation
Validation.
Closes #36
"""


class OpenApiBreakingPolicyTest(unittest.TestCase):
    def test_additive_change_needs_no_breaking_marker(self) -> None:
        self.assertEqual(
            [],
            POLICY.validate(
                "refactor/36-openapi-contract-gate",
                "refactor(openapi): enforce contract compatibility",
                BODY,
                "contributor",
            ),
        )

    def test_break_requires_all_three_markers(self) -> None:
        errors = POLICY.validate(
            "refactor/36-openapi-contract-gate",
            "refactor(openapi): enforce contract compatibility",
            BODY,
            "contributor",
            True,
        )

        self.assertEqual(3, len(errors))

    def test_break_accepts_conventional_and_issue_backed_markers(self) -> None:
        body = BODY + "\nBREAKING CHANGE: clients must migrate before upgrading.\nOpenAPI-Migration: #36\n"
        self.assertEqual(
            [],
            POLICY.validate(
                "refactor/36-openapi-contract-gate",
                "refactor(openapi)!: enforce contract compatibility",
                body,
                "contributor",
                True,
            ),
        )

    def test_dependabot_remains_exempt_from_unrelated_policy(self) -> None:
        self.assertEqual([], POLICY.validate("", "", "", "dependabot[bot]"))

    def test_dependabot_cannot_bypass_breaking_approval(self) -> None:
        errors = POLICY.validate("", "build(deps): update", "", "dependabot[bot]", True)

        self.assertEqual(3, len(errors))

    def test_dependabot_can_supply_breaking_approval_without_unrelated_boilerplate(self) -> None:
        body = "BREAKING CHANGE: clients must migrate.\nOpenAPI-Migration: #36\n"
        self.assertEqual(
            [],
            POLICY.validate(
                "",
                "build(deps)!: update dependency",
                body,
                "dependabot[bot]",
                True,
            ),
        )


if __name__ == "__main__":
    unittest.main()
