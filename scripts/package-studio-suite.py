"""Build the relocatable, full Studio suite from a jpackage image and verified upstream tools."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SYSTEM = platform.system()
ARCH = platform.machine().lower().replace("amd64", "x86_64").replace("aarch64", "arm64")
TARGET = f"{SYSTEM.lower()}-{ARCH}"
MODELS = {".gguf", ".safetensors", ".onnx", ".tflite"}


def unpack(spec, dest, cache):
    archive = cache / spec["name"]
    if not archive.is_file():
        partial = archive.with_suffix(".partial")
        subprocess.run(["curl", "--fail", "--location", "--retry", "3", "--output", partial, spec["url"]], check=True)
        partial.replace(archive)
    with archive.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    if archive.stat().st_size != spec["size"] or digest != spec["sha256"]:
        raise ValueError(f"Upstream archive failed verification: {archive}")
    dest.mkdir()
    if SYSTEM == "Darwin" and archive.suffix == ".zip":
        subprocess.run(["ditto", "-x", "-k", archive, dest], check=True)
    else:
        subprocess.run(["tar", "-xf", archive, "-C", dest], check=True)


def inventory(folder):
    regular = [p for p in folder.rglob("*") if not p.is_symlink() and p.is_file()]
    weights = sum(p.stat().st_size for p in regular if p.suffix.lower() in MODELS or "models" in p.relative_to(folder).parts)
    return {"files": len(regular), "applicationBytes": sum(p.stat().st_size for p in regular) - weights, "modelBytes": weights}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("image", type=Path, help="Gradle createDistributable app directory")
    parser.add_argument("output", type=Path)
    parser.add_argument("--cache", required=True, type=Path)
    args = parser.parse_args()
    specs = json.loads((ROOT / "desktopApp/distribution/studio-tools.json").read_text())[TARGET]
    output = args.output.resolve()
    output.mkdir(exist_ok=True, parents=True)
    args.cache.mkdir(exist_ok=True, parents=True)
    suite = output / "Meshlit-Studio"
    suite.mkdir()
    [app] = [p for p in args.image.iterdir() if p.is_dir() and p.name.lower() in ("meshlit", "meshlit.app")]
    shutil.copytree(app, suite / ("Meshlit.app" if SYSTEM == "Darwin" else "Meshlit"), symlinks=True)
    # Check the notice inside the image that will ship.
    [model_notice] = list((suite / ("Meshlit.app" if SYSTEM == "Darwin" else "Meshlit")).rglob("QWEN-MODEL-LICENSE.txt"))
    license_text = (ROOT / "desktopApp/distribution/notices/QWEN-MODEL-LICENSE.txt").read_bytes()
    if b"END OF TERMS AND CONDITIONS" not in license_text:
        raise ValueError("Bundled model license text is incomplete")
    if model_notice.read_bytes() != license_text:
        raise ValueError("Rebuild the app image with the reviewed model notice")
    with tempfile.TemporaryDirectory(dir=output) as temp:
        raw = Path(temp)
        unpack(specs["editor"], raw / "editor", args.cache)
        marker = {"Windows": "VSCodium.exe", "Linux": "codium", "Darwin": "VSCodium.app"}[SYSTEM]
        [executable] = list((raw / "editor").rglob(marker)) if SYSTEM != "Linux" else [
            p for p in (raw / "editor").rglob(marker) if (p.parent / "resources/app/product.json").is_file()]
        executable.parent.rename(suite / "editor")
        unpack(specs["jdk"], raw / "jdk", args.cache)
        [javac] = list((raw / "jdk").rglob("javac.exe" if SYSTEM == "Windows" else "javac"))
        javac.parent.parent.rename(suite / "toolchain")
    shutil.copy2(ROOT / "docs/desktop/FULL_STUDIO.md", suite / "START.md")
    components = {p.name: inventory(p) for p in suite.iterdir() if p.is_dir()}
    installed = sum(item["applicationBytes"] for item in components.values())
    if installed <= 500_000_000:
        raise ValueError(f"Model-free installed payload below Studio minimum: {installed}")
    report = {"platform": TARGET, "commit": os.environ.get("GITHUB_SHA"),
              "installedModelFreeBytes": installed, "components": components, "upstream": specs}
    (suite / "STUDIO-CONTENTS.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(components))


if __name__ == "__main__":
    main()
