#!/usr/bin/env python3
"""Reconstruct tested installers from verified temporary draft-release parts.

Never builds, signs, publishes a release, or overwrites an existing installer.
Temporary parts are deleted only after every reconstructed installer is verified.
"""
import argparse
import hashlib
import json
import re
import subprocess
import tempfile
from pathlib import Path

REPO = "sabbirimon/meshlit"
TAG = "v2.0.0-beta.5"
ALLOWED = {
    "Meshlit-v2-build45-arm64-v8a-debug.apk",
    "MeshlitPreview-2.0.45-studio.45.1-macos-intel.dmg",
}


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def validate(document):
    assert document["format"] == "meshlit-release-relay/1"
    assert document["repository"] == REPO and document["tag"] == TAG
    assert {a["name"] for a in document["artifacts"]} == ALLOWED
    assert len(document["artifacts"]) == 2
    names = set()
    for artifact in document["artifacts"]:
        assert type(artifact["size"]) is int and 0 < artifact["size"] < 2 * 1024**3
        assert re.fullmatch(r"[a-f0-9]{64}", artifact["sha256"])
        assert 1 <= len(artifact["parts"]) <= 256
        total = 0
        for part in artifact["parts"]:
            assert re.fullmatch(r"relay-beta5-(android|macos)-[0-9]{4}\.part", part["name"])
            assert part["name"] not in names
            names.add(part["name"])
            assert type(part["size"]) is int and 0 < part["size"] <= 16 * 1024**2
            assert re.fullmatch(r"[a-f0-9]{64}", part["sha256"])
            total += part["size"]
        assert total == artifact["size"]


def gh(*args, capture=False, timeout=600):
    return subprocess.run(["gh", *args], check=True, text=True,
                          capture_output=capture, timeout=timeout).stdout


def release():
    data = json.loads(gh("api", f"repos/{REPO}/releases/423840929", capture=True))
    assert data["tag_name"] == TAG and data["draft"], "Expected this draft release"
    pages = json.loads(gh("api", "--paginate", "--slurp",
                         f"repos/{REPO}/releases/423840929/assets?per_page=100", capture=True))
    data["assets"] = [asset for page in pages for asset in page]
    return data


def matches(remote, item):
    return (remote["state"] == "uploaded" and remote["size"] == item["size"]
            and remote.get("digest") == "sha256:" + item["sha256"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--validate-only", action="store_true")
    args = parser.parse_args()
    document = json.loads(args.manifest.read_text())
    validate(document)
    if args.validate_only:
        print("Relay manifest bounds, names and total sizes validated")
        return
    verified = []
    for artifact in document["artifacts"]:
        remote = {a["name"]: a for a in release()["assets"]}
        if artifact["name"] in remote:
            assert matches(remote[artifact["name"]], artifact), "Existing installer differs"
        else:
            with tempfile.TemporaryDirectory(prefix="meshlit-relay-") as temporary:
                folder = Path(temporary)
                assembled = folder / artifact["name"]
                with assembled.open("xb") as output:
                    for part in artifact["parts"]:
                        assert part["name"] in remote and matches(remote[part["name"]], part)
                        gh("release", "download", TAG, "--repo", REPO,
                           "--pattern", part["name"], "--dir", str(folder))
                        path = folder / part["name"]
                        assert path.stat().st_size == part["size"] and digest(path) == part["sha256"]
                        with path.open("rb") as stream:
                            while data := stream.read(1024 * 1024):
                                output.write(data)
                        path.unlink()
                assert assembled.stat().st_size == artifact["size"]
                assert digest(assembled) == artifact["sha256"], "Reconstructed installer differs"
                release()  # Refuse mutation if someone has published the draft.
                gh("release", "upload", TAG, str(assembled), "--repo", REPO, timeout=1200)
                uploaded = {a["name"]: a for a in release()["assets"]}
                assert matches(uploaded[artifact["name"]], artifact)
        verified.append({"name": artifact["name"], "sha256": artifact["sha256"], "size": artifact["size"]})
        print("Verified exact installer:", artifact["name"], flush=True)
    # Only our manifest-listed temporary parts, never installers or unrelated assets.
    remaining = {a["name"]: a for a in release()["assets"]}
    for artifact in document["artifacts"]:
        for part in artifact["parts"]:
            if part["name"] in remaining:
                assert matches(remaining[part["name"]], part)
                gh("release", "delete-asset", TAG, part["name"], "--repo", REPO, "--yes")
    Path("relay-result.json").write_text(json.dumps({"format": document["format"],
        "tag": TAG, "verified": verified, "temporaryPartsRemoved": True,
        "releasePublished": False}, indent=2) + "\n")


if __name__ == "__main__":
    main()
