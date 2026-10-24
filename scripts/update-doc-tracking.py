#!/usr/bin/env python3
"""Reconcile first-party Markdown navigation and classify all tracked documents.

No runtime/feature acceptance is inferred. Historical evidence, legal mirrors,
third-party texts and templates are indexed without changing their contents.
"""
import argparse
import collections
import hashlib
import json
import os
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INDEX = "docs/DOCUMENTATION_STATUS.md"
MANIFEST = "docs/documentation-status.json"
START = "<!-- meshlit-document-tracking:start -->"
END = "<!-- meshlit-document-tracking:end -->"
PATTERN = re.compile(r"\n" + re.escape(START) + r"\n.*?" + re.escape(END) + r"\n", re.S)


def classify(name):
    if "/node_modules/" in name or name.startswith("vendored/licenses/"):
        return "third-party", "dependency text"
    if ("LICENSE_HISTORY" in name or "THIRD_PARTY_NOTICES" in name
            or name in {"AUTHORS.md", "core-hyperl/MODIFICATIONS.md", "docs/TERMS_OF_USE.md", "docs/PRIVACY_POLICY.md"}):
        return "protected", "licence, attribution or versioned agreement"
    if name.startswith(("docs/history/", "docs/journal/", "docs/decisions/")) or any(
            part in name for part in ("VALIDATION_", "PHASE1_VALIDATION", "DEVICE_TESTING_", "ODYSSEUS_REVIEW_", "EVALUATION_REVIEW_", "dirty-tree-inventory")):
        return "historical", "dated design/evidence snapshot"
    if name == ".github/pull_request_template.md":
        return "template", "pull-request template"
    if name in {"PLAN.md", "PROGRESS.md", "TODO.md", "BUGS.md", "REQUESTS.md", "README.md", "BUILD.md", "BUILD_MILESTONES.md", "FEATURE_MAP.md", "docs/RELEASE_EVIDENCE.md", "docs/SESSION_HANDOFF.md"}:
        return "active", "cross-platform tracking; feature and device gates remain separate"
    if name.startswith("docs/desktop/") or "DESKTOP_" in name or "MACOS_" in name:
        return "active", "desktop/server; individual acceptance gates apply"
    if name.startswith("ohosApp/") or name.endswith("MULTIPLATFORM_NEXT.md"):
        return "active", "platform preview; NEXT device/build acceptance remains open"
    if name.startswith(("companions/", "examples/", "configuration/", "vendored/")):
        return "active", "optional host/schema/reference; not a bundled qualified service"
    if name.startswith((".claude/", "app/SKILL", "app/CLAUDE")) or name in {"AGENTS.md", "AGENT_BUILD.md", "CLAUDE.md"}:
        return "active", "workflow guidance; current owner instructions and scoped evidence govern"
    return "active", "Android/shared reference; desktop ports have separate acceptance"


def link_from(name, target):
    return os.path.relpath(ROOT / target, (ROOT / name).parent)


def tracked_markdown():
    result = subprocess.check_output(["git", "ls-files", "--cached", "--others", "--exclude-standard", "--", "*.md"], cwd=ROOT, text=True)
    return sorted(set(result.splitlines()) | {INDEX})


def header(name, scope, updated):
    links = " · ".join(f"[{label}]({link_from(name, target)})" for label, target in
                       (("Plan", "PLAN.md"), ("Progress", "PROGRESS.md"), ("Document status", INDEX)))
    return f"\n{START}\nTracking reconciled {updated}: {links}.\nScope: {scope}. Phase labels in older sections retain their original scope.\n{END}\n"


def build(write):
    phases = json.loads((ROOT / "docs/phase-progress.json").read_text())
    records = []
    stale = []
    for name in tracked_markdown():
        if name == INDEX:
            continue
        path = ROOT / name
        original = path.read_text(encoding="utf-8")
        content = PATTERN.sub("\n", original)
        kind, scope = classify(name)
        if kind == "active":
            heading = re.search(r"^# .+$", content, re.M)
            position = heading.end() if heading else 0
            tail = content[position:]
            expected = content[:position] + header(name, scope, phases["updated"]) + (tail[1:] if tail.startswith("\n") else tail)
            if expected != original:
                stale.append(name)
                if write:
                    path.write_text(expected, encoding="utf-8")
        heading = re.search(r"^# (.+)$", content, re.M)
        records.append({"path": name, "class": kind, "scope": scope,
                        "title": heading.group(1) if heading else name,
                        "contentSha256WithoutTrackingBlock": hashlib.sha256(content.encode()).hexdigest()})
    counts = dict(sorted(collections.Counter(r["class"] for r in records).items()))
    lines = ["# Markdown documentation status and workstream tracking", "",
             f"Reconciled {phases['updated']}. This index covers every tracked first-party, historical, protected and third-party Markdown file. The index itself is generated; counts below exclude this index to avoid self-hashing.", "",
             "[Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Milestone gates](../BUILD_MILESTONES.md) · [Desktop feature tracker](DESKTOP_FEATURE_TRACKER.md) · [Release evidence](RELEASE_EVIDENCE.md)", "",
             "## Workstream phases", "",
             "Phase names are independent workstreams, not a unified numbered stage or an overall completion percentage. Source, contracts, host/device qualification, release and production admission are separate states. Historical phase numbers remain dated design labels.", "",
             "| Workstream | Current qualification | Next gate |", "| --- | --- | --- |"]
    lines += [f"| {p['name']} | {p['state']} | {p['nextGate']} |" for p in phases["workstreams"]]
    lines += ["", "## Coverage and maintenance", "",
              ", ".join(f"{v} {k}" for k, v in counts.items()) + f"; {len(records) + 1} Markdown documents including this index.", "",
              "Active files receive current tracking links. Historical records retain exact dated claims; protected licences/attribution/versioned policies, third-party texts and PR templates are indexed without content edits. Runtime evidence is read from the named ledger, never inferred from a document date or checksum.", "",
              "Run `python3 scripts/update-doc-tracking.py --write` after updating plans, then `--check`. The JSON manifest records normalized content hashes and classification; it is not a runtime certificate. Feature/module references are separately checked by scripts/validate-feature-map.py."]
    for kind in counts:
        lines += ["", f"## {kind.title()} documents", "", "| Document | Scope |", "| --- | --- |"]
        for r in records:
            if r["class"] == kind:
                lines.append(f"| [{r['path']}]({link_from(INDEX, r['path'])}) | {r['scope']} |")
    outputs = {INDEX: "\n".join(lines) + "\n", MANIFEST: json.dumps({"schemaVersion": 1, "updated": phases["updated"], "index": INDEX, "phaseLedger": "docs/phase-progress.json", "countsExcludingGeneratedIndex": counts, "documents": records}, indent=2) + "\n"}
    for name, text in outputs.items():
        path = ROOT / name
        if not path.exists() or path.read_text() != text:
            stale.append(name)
            if write:
                path.write_text(text)
    if stale and not write:
        raise SystemExit("Stale tracking: " + ", ".join(stale))
    print(f"Documentation tracking {'updated' if write else 'valid'}: {len(records) + 1} Markdown files; {counts}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--write", action="store_true")
    group.add_argument("--check", action="store_true")
    build(parser.parse_args().write)
