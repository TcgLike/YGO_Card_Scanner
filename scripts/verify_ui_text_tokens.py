#!/usr/bin/env python3
"""Reject direct visible strings in Compose UI outside the localization package."""
from pathlib import Path
import re
import sys

UI_ROOT = Path("app/src/main/java/com/ygocardscanner/ui")
LOCALIZATION_DIR = UI_ROOT / "localization"
DIRECT_TEXT = re.compile(r'\bText\s*\(\s*"(?P<value>(?:[^"\\]|\\.)*)"')
DIRECT_DESCRIPTION = re.compile(r'\bcontentDescription\s*=\s*"(?P<value>(?:[^"\\]|\\.)*)"')

violations: list[str] = []
for path in UI_ROOT.rglob("*.kt"):
    if LOCALIZATION_DIR in path.parents:
        continue
    source = path.read_text(encoding="utf-8")
    for pattern in (DIRECT_TEXT, DIRECT_DESCRIPTION):
        for match in pattern.finditer(source):
            line = source.count("\n", 0, match.start()) + 1
            violations.append(f"{path}:{line}: direct UI text `{match.group('value')}`; use appText(...) or UiTextToken")

if violations:
    print("UI localization token check failed:", file=sys.stderr)
    print("\n".join(violations), file=sys.stderr)
    sys.exit(1)
print("UI localization token check passed.")
