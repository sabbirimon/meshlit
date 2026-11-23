"""Stage the pinned CPU server and default model in Compose distribution resources."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "companions/studio-runtime"))
from studio_models import verify_model


def main():
    build = ROOT / "desktopApp/build"
    output = build / "package-resources/common"
    local = output / "local"
    local.mkdir(parents=True, exist_ok=True)
    native = build / "llama-build"
    subprocess.run([sys.executable, str(ROOT / "scripts/build-desktop-llama.py"),
                    "--output", str(native), "--jobs", "4"], check=True)
    [server] = (native / "bin").rglob("llama-server.exe" if os.name == "nt" else "llama-server")
    shutil.copy2(server, local / server.name)
    (local / "engine.json").write_text(json.dumps({"sha256": hashlib.sha256(server.read_bytes()).hexdigest()}))
    spec = json.loads((ROOT / "desktopApp/distribution/bundled-model.json").read_text())
    models = output / "models"
    models.mkdir(exist_ok=True)
    target = models / spec["filename"]
    partial = target.with_suffix(".partial")
    subprocess.run(["curl", "--fail", "--location", "--http1.1", "--retry", "3", "--retry-all-errors", "--retry-max-time", "900", "--max-time", "1800",
                    "--max-filesize", str(spec["sizeBytes"]), "--output", str(partial), spec["url"]], check=True)
    verify_model(partial, spec)
    partial.replace(target)
    shutil.copytree(ROOT / "desktopApp/distribution/notices", output / "notices", dirs_exist_ok=True)
    shutil.copy2(ROOT / "vendored/runanywhere-llama/LICENSE", output / "notices/llama.cpp-LICENSE.txt")


if __name__ == "__main__":
    main()
