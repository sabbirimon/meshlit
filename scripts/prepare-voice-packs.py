#!/usr/bin/env python3
"""Build checksummed offline voice ZIPs from the exact upstream research artifacts.
No download, code execution, native compilation, SDK upgrade or license relicensing.
"""
import argparse, hashlib, io, json, tarfile, zipfile
from pathlib import Path

ARTIFACTS = {
    "whisper": ("a903e9afa30142cab9327015a331f2fe13b15e0511f0bf1c4acfc21b51a7b8e7", "WHISPER_STT", "whisper-tiny-en", "Whisper Tiny English INT8", "MIT (Whisper weights); preserve upstream notices"),
    "piper": ("88572edcb92be5fc6ebbad0957c4effa5bdcfcf479407c24a3e918255b25625f", "PIPER_TTS", "piper-lessac-medium", "Piper Lessac US English", "Upstream MODEL_CARD / Lessac dataset terms; research artifact, commercial rights not qualified"),
}
BASE = "https://github.com/RunanywhereAI/sherpa-onnx/releases/tag/runanywhere-models-v1"

def prepare(archive, kind, output):
    digest, modality, ident, name, license_note = ARTIFACTS[kind]
    assert archive.stat().st_size <= 256 * 1024 * 1024, "Oversized upstream archive"
    assert hashlib.sha256(archive.read_bytes()).hexdigest() == digest, "Upstream archive SHA-256 mismatch"
    files = {}
    with tarfile.open(archive) as source:
        for entry in source:
            parts = Path(entry.name).parts
            if not parts or parts[0] in ("/", "..") or ".." in parts or "\\" in entry.name:
                raise ValueError("Unsafe upstream archive path")
            if entry.isdir():
                continue
            if not entry.isfile() or entry.size > 128 * 1024 * 1024:
                raise ValueError("Links / special files / oversized files are unsupported")
            relative = "/".join(parts[1:])
            if kind == "whisper":
                if relative not in ("tiny.en-encoder.int8.onnx", "tiny.en-decoder.int8.onnx", "tiny.en-tokens.txt"):
                    continue
                relative = relative.replace(".int8.onnx", ".onnx")
            elif not (relative.startswith("espeak-ng-data/") or relative in ("tokens.txt", "en_US-lessac-medium.onnx", "en_US-lessac-medium.onnx.json", "MODEL_CARD")):
                continue
            if relative in files:
                raise ValueError("Duplicate upstream file")
            files[relative] = source.extractfile(entry).read()
    if kind == "whisper":
        files["UPSTREAM-NOTICES.txt"] = ("Whisper code and weights: https://github.com/openai/whisper/blob/main/LICENSE (MIT).\nONNX conversion / runtime: https://github.com/k2-fsa/sherpa-onnx\nArchive source: " + BASE + "\nEncoder and decoder INT8 file names normalized; bytes unchanged.\n").encode()
    assert files and sum(map(len, files.values())) <= 512 * 1024 * 1024
    manifest = dict(format="meshlit-voice-pack/1", id=ident, name=name, kind=modality, language="en", license=license_note, source=BASE,
                    files=[dict(path=p, bytes=len(b), sha256=hashlib.sha256(b).hexdigest()) for p, b in files.items()])
    output.parent.mkdir(parents=True, exist_ok=True)
    part = output.with_suffix(output.suffix + ".part")
    try:
        with zipfile.ZipFile(part, "w", compression=zipfile.ZIP_STORED) as z:
            z.writestr("manifest.json", json.dumps(manifest, separators=(",", ":")))
            for path, data in files.items():
                z.writestr(path, data)
        part.replace(output)
    finally:
        part.unlink(missing_ok=True)
    return dict(path=str(output), files=len(files), unpackedBytes=sum(map(len, files.values())), sha256=hashlib.sha256(output.read_bytes()).hexdigest(), upstreamSha256=digest)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--kind", choices=ARTIFACTS, required=True)
    parser.add_argument("--archive", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(prepare(args.archive, args.kind, args.output)))
