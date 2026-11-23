"""Build the relocatable, full Studio suite with a Rust launcher, C++ inference, VSCodium and a C/C++ toolchain."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tempfile
import plistlib
import zipfile

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


def package_extension(suite):
    src = ROOT / "companions/native-studio/extension"
    name = "meshlit-native.vsix"
    manifest = """<PackageManifest Version="2.0.0" xmlns="http://schemas.microsoft.com/developer/vsx-schema/2011">
    <Metadata><Identity Language="en-US" Id="native-studio" Version="2.0.48" Publisher="meshlit"/>
    <DisplayName>Meshlit Studio</DisplayName><Description>Offline model studio</Description>
    <Properties><Property Id="Microsoft.VisualStudio.Code.Engine" Value="^1.95.0"/></Properties></Metadata>
    <Installation><InstallationTarget Id="Microsoft.VisualStudio.Code"/></Installation><Dependencies/>
    <Assets><Asset Type="Microsoft.VisualStudio.Code.Manifest" Path="extension/package.json" Addressable="true"/></Assets></PackageManifest>"""
    with zipfile.ZipFile(suite / name, "w", zipfile.ZIP_DEFLATED) as bundle:
        bundle.writestr("extension.vsixmanifest", manifest)
        bundle.writestr("[Content_Types].xml", '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="json" ContentType="application/json"/><Default Extension="cjs" ContentType="application/javascript"/><Default Extension="vsixmanifest" ContentType="text/xml"/></Types>')
        for file in sorted(src.iterdir()):
            if file.suffix in (".cjs", ".json") and ".test." not in file.name:
                bundle.write(file, "extension/" + file.name)
        bundle.write(ROOT / "LICENSE", "extension/LICENSE")
    (suite / "extension.sha256").write_text(hashlib.sha256((suite / name).read_bytes()).hexdigest())


def launcher(suite):
    target = suite / ("Meshlit.exe" if SYSTEM == "Windows" else "Meshlit")
    if SYSTEM == "Darwin":
        contents = suite / "Meshlit.app/Contents"
        (contents / "MacOS").mkdir(parents=True)
        target = contents / "MacOS/Meshlit"
        (contents / "Info.plist").write_bytes(plistlib.dumps({
            "CFBundleExecutable": "Meshlit", "CFBundleName": "Meshlit", "CFBundlePackageType": "APPL",
            "CFBundleIdentifier": "bd.com.imon.meshlit.studio", "CFBundleShortVersionString": "2.0.48", "CFBundleVersion": "48"}))
    subprocess.run(["rustc", "--edition=2021", "-C", "opt-level=3", str(ROOT / "companions/native-studio/launcher.rs"), "-o", target], check=True)
    if SYSTEM == "Darwin":
        subprocess.run(["codesign", "--force", "--sign", "-", suite / "Meshlit.app"], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("resources", type=Path, help="Validated stage-desktop-inference.py resources")
    parser.add_argument("output", type=Path)
    parser.add_argument("--cache", required=True, type=Path)
    args = parser.parse_args()
    specs = json.loads((ROOT / "desktopApp/distribution/studio-tools.json").read_text())[TARGET]
    output = args.output.resolve()
    output.mkdir(exist_ok=True, parents=True)
    args.cache.mkdir(exist_ok=True, parents=True)
    suite = output / "Meshlit-Studio"
    suite.mkdir()
    spec = ROOT / "desktopApp/distribution/bundled-model.json"
    model = json.loads(spec.read_text())
    (suite / "models").mkdir()
    shutil.copy2(args.resources / "models" / model["filename"], suite / "models" / model["filename"])
    inference = suite / "inference"
    inference.mkdir()
    server = "llama-server.exe" if SYSTEM == "Windows" else "llama-server"
    for name in ("engine.json", server):
        shutil.copy2(args.resources / "local" / name, inference / name)
    shutil.copy2(spec, inference / "model.json")
    notices = suite / "notices"
    notices.mkdir()
    for name in ("QWEN-MODEL-LICENSE.txt", "llama.cpp-LICENSE.txt"):
        shutil.copy2(args.resources / "notices" / name, notices / name)
    if b"END OF TERMS AND CONDITIONS" not in (notices / "QWEN-MODEL-LICENSE.txt").read_bytes():
        raise ValueError("Incomplete Qwen license")
    for name in ("LICENSE","NOTICE"):
        shutil.copy2(ROOT / name, suite / name)
    with tempfile.TemporaryDirectory(dir=output) as temp:
        raw = Path(temp)
        unpack(specs["editor"], raw / "editor", args.cache)
        marker = {"Windows": "VSCodium.exe", "Linux": "codium", "Darwin": "VSCodium.app"}[SYSTEM]
        [executable] = list((raw / "editor").rglob(marker)) if SYSTEM != "Linux" else [
            p for p in (raw / "editor").rglob(marker) if (p.parent / "resources/app/product.json").is_file()]
        executable.parent.rename(suite / "editor")
        unpack(specs["zig"], raw / "compiler", args.cache)
        [zig] = [p for p in (raw / "compiler").rglob("zig.exe" if SYSTEM == "Windows" else "zig") if p.is_file()]
        zig.parent.rename(suite / "toolchain")
    automation = suite / "automation"
    automation.mkdir()
    if SYSTEM == "Darwin":
        subprocess.run([__import__("sys").executable, ROOT/"companions/studio-runtime/prepare_app_access.py",
                        "--extension", automation], check=True)
    else:
        helper = "windows.ps1" if SYSTEM == "Windows" else "linux.py"
        shutil.copy2(ROOT/"companions/native-studio/automation"/helper, automation)
    package_extension(suite)
    launcher(suite)
    if SYSTEM == "Linux":
        shutil.copy2(ROOT / "companions/native-studio/meshlit-studio.apparmor", suite)
    shutil.copy2(ROOT / "docs/desktop/FULL_STUDIO.md", suite / "START.md")
    forbidden = [p.relative_to(suite) for p in suite.rglob("*") if p.is_file() and (
        p.suffix.lower() in {".jar",".class",".jmod"} or p.name.lower() in {"java","java.exe","javaw.exe","javac","javac.exe","jvm.dll","libjvm.so","libjvm.dylib"})]
    if forbidden:
        raise ValueError(f"Unexpected JVM payload: {forbidden[:10]}")
    components = {p.name: inventory(p) for p in suite.iterdir() if p.is_dir()}
    installed = sum(item["applicationBytes"] for item in components.values()) + sum(p.stat().st_size for p in suite.iterdir() if p.is_file())
    if installed <= 500_000_000:
        raise ValueError(f"Model-free installed payload below Studio minimum: {installed}")
    report = {"platform": TARGET, "commit": os.environ.get("GITHUB_SHA"), "jvmFiles": 0,
              "installedModelFreeBytes": installed, "components": components, "upstream": specs}
    (suite / "STUDIO-CONTENTS.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report))


if __name__ == "__main__":
    main()
