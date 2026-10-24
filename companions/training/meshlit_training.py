#!/usr/bin/env python3
"""Owner-installed Soup companion. JSON replies over SSH; no network listener or fake training."""
from __future__ import annotations
import argparse
import base64
import contextlib
import hashlib
import importlib.metadata
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import sys
import threading
import time

SOUP_VERSION = "0.75.0"
MAX_DATA = 64 * 1024 * 1024
MAX_LOG = 8 * 1024 * 1024
TERMINAL = {"completed", "failed", "cancelled", "timed_out"}
JOB_ID = re.compile(r"[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\Z")


def inside(root: Path, value: str, *, exists=True) -> Path:
    if not isinstance(value, str) or len(value) > 1024 or "\0" in value:
        raise ValueError("Invalid path")
    path = Path(value)
    if not path.is_absolute():
        raise ValueError("Use an absolute host path")
    path = path.resolve(strict=exists)
    if path == root or root not in path.parents:
        raise ValueError("Path must be inside the configured workspace")
    return path


def workspace(value: str) -> Path:
    root = Path(value)
    if not root.is_absolute() or not root.is_dir() or root.is_symlink():
        raise ValueError("Workspace must be an existing absolute non-symlink directory")
    root = root.resolve()
    if root == Path(root.anchor):
        raise ValueError("Do not use the filesystem root as a training workspace")
    return root


def atomic(path: Path, value: dict):
    temporary = path.with_suffix(path.suffix + ".part")
    with temporary.open("w", encoding="utf-8") as file:
        json.dump(value, file, ensure_ascii=True, allow_nan=False)
        file.flush()
        os.fsync(file.fileno())
    temporary.replace(path)


def read_json(path: Path) -> dict:
    if path.stat().st_size > 65536:
        raise ValueError("Job metadata exceeds its bound")
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError("Invalid job metadata")
    return value


def digest(path: Path) -> str:
    sha = hashlib.sha256()
    with path.open("rb") as file:
        for block in iter(lambda: file.read(1024 * 1024), b""):
            sha.update(block)
    return sha.hexdigest()


def soup_command() -> list[str]:
    version = importlib.metadata.version("soup-cli")
    if not (3, 10) <= sys.version_info[:2] <= (3, 12):
        raise ValueError("Use a dedicated Python 3.10–3.12 Soup environment")
    if version != SOUP_VERSION:
        raise ValueError(f"This adapter requires Soup {SOUP_VERSION}; installed {version}")
    # The reviewed upstream console entry point. No shell or PATH-dependent executable.
    return [sys.executable, "-c", "from soup_cli.cli import run; run()"]


def validate_spec(root: Path, spec: dict) -> tuple[dict, Path, Path]:
    allowed = {"jobId", "basePath", "datasetPath", "epochs", "rank", "quantization",
               "streamLayers", "maxLength", "timeoutSeconds"}
    if set(spec) != allowed or not JOB_ID.fullmatch(str(spec["jobId"])):
        raise ValueError("Invalid job ID or unsupported/missing configuration fields")
    for key, lo, hi in (("epochs", 1, 5), ("rank", 1, 64), ("maxLength", 128, 4096),
                        ("timeoutSeconds", 60, 86400)):
        if type(spec[key]) is not int or not lo <= spec[key] <= hi:
            raise ValueError(f"Invalid {key}")
    if spec["quantization"] not in ("none", "4bit") or type(spec["streamLayers"]) is not bool:
        raise ValueError("Invalid quantization or layer-streaming setting")
    base = inside(root, spec["basePath"])
    data = inside(root, spec["datasetPath"])
    if not base.is_dir() or not (base / "config.json").is_file():
        raise ValueError("Base must be a local Transformers snapshot with config.json, not GGUF")
    inside(root, str(base / "config.json"))
    weights = list(base.glob("*.safetensors"))
    if not weights or any(p.is_symlink() or not p.is_file() or p.stat().st_size == 0 for p in weights):
        raise ValueError("Use a materialized local safetensors snapshot without symlink weights")
    if not data.is_file() or data.suffix != ".jsonl" or not 0 < data.stat().st_size <= MAX_DATA:
        raise ValueError("Dataset must be a nonempty JSONL file of at most 64 MiB")
    # A narrow, reviewed SFT recipe. Soup's own schema validates it before launch.
    config = {"base": str(base), "task": "sft", "backend": "transformers", "modality": "text",
              "data": {"train": "SNAPSHOT", "format": "alpaca", "val_split": 0.1,
                       "max_length": spec["maxLength"]},
              "training": {"epochs": spec["epochs"], "lr": 2e-5, "batch_size": 1,
                           "lora": {"r": spec["rank"], "alpha": spec["rank"] * 2},
                           "quantization": spec["quantization"],
                           "stream_layers": spec["streamLayers"]}, "output": "OUTPUT"}
    return config, base, data


def validate_dataset(path: Path):
    count = 0
    with path.open("rb") as file:
        while True:
            line = file.readline(1024 * 1024 + 1)
            if not line:
                break
            if len(line) > 1024 * 1024:
                raise ValueError("Dataset row exceeds 1 MiB")
            row = json.loads(line)
            if not isinstance(row, dict) or set(row) - {"instruction", "input", "output"}:
                raise ValueError("Dataset rows require Alpaca instruction/input/output fields")
            if not all(isinstance(row.get(k), str) and row[k].strip() for k in ("instruction", "output")):
                raise ValueError("Dataset instruction and output must be nonempty strings")
            if "input" in row and not isinstance(row["input"], str):
                raise ValueError("Dataset input must be text")
            count += 1
    if count < 10:
        raise ValueError("Provide at least ten real training examples")
    return count


def job_directory(root: Path, job_id: str) -> Path:
    if not JOB_ID.fullmatch(job_id):
        raise ValueError("Invalid job ID")
    jobs = root / "meshlit-jobs"
    jobs.mkdir(mode=0o700, exist_ok=True)
    if jobs.is_symlink():
        raise ValueError("Job storage cannot be a symlink")
    candidate = jobs / job_id
    if candidate.is_symlink():
        raise ValueError("Job directory cannot be a symlink")
    return inside(root, str(candidate), exists=False)


def status(root: Path, job_id: str) -> dict:
    directory = job_directory(root, job_id)
    value = read_json(directory / "status.json")
    if value["state"] not in TERMINAL and time.time() - value["updatedAt"] > 120:
        value = dict(value, state="unknown", error="Worker heartbeat lost; inspect host before retrying")
    log_path = directory / "train.log"
    if log_path.is_file():
        inside(root, str(log_path))
        with log_path.open("rb") as file:
            file.seek(max(0, log_path.stat().st_size - 6000))
            value["logTail"] = file.read(6000).decode("utf-8", errors="replace")
    manifest = directory / "manifest.json"
    if manifest.is_file():
        value["manifest"] = read_json(manifest)
    return value


def start(root: Path, spec: dict) -> dict:
    config, base, data = validate_spec(root, spec)
    soup_command()  # Refuse missing dependencies/version before accepting any job.
    directory = job_directory(root, spec["jobId"])
    request_hash = hashlib.sha256(json.dumps(spec, sort_keys=True).encode()).hexdigest()
    try:
        directory.mkdir(mode=0o700)
    except FileExistsError:
        if read_json(directory / "request.json")["requestHash"] != request_hash:
            raise ValueError("Job ID already belongs to a different request")
        return status(root, spec["jobId"])
    value = {"jobId": spec["jobId"], "state": "starting", "updatedAt": time.time(), "progress": None}
    try:
        atomic(directory / "request.json", {"requestHash": request_hash, "spec": spec})
        atomic(directory / "status.json", value)
        # Detached supervisor persists across SSH disconnects; it owns/cancels only its child.
        with (directory / "supervisor.log").open("ab") as log:
            subprocess.Popen([sys.executable, str(Path(__file__).resolve()), "worker", "--workspace",
                              str(root), "--job", spec["jobId"]], stdin=subprocess.DEVNULL,
                             stdout=log, stderr=log, start_new_session=True, close_fds=True)
        return value
    except Exception:
        atomic(directory / "status.json", dict(value, state="failed", error="Supervisor launch failed"))
        raise


def stop_owned_process(process: subprocess.Popen):
    if process.poll() is None:
        with contextlib.suppress(ProcessLookupError):
            os.killpg(process.pid, signal.SIGTERM)
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            with contextlib.suppress(ProcessLookupError):
                os.killpg(process.pid, signal.SIGKILL)
            process.wait(timeout=10)


def run_logged(command: list[str], directory: Path, timeout: int, update) -> tuple[int, str | None]:
    env = dict(os.environ, HF_HUB_OFFLINE="1", HF_DATASETS_OFFLINE="1", TRANSFORMERS_OFFLINE="1",
               WANDB_MODE="disabled", WANDB_DISABLED="true", TOKENIZERS_PARALLELISM="false")
    process = subprocess.Popen(command, cwd=directory, env=env, stdin=subprocess.DEVNULL,
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    failure = []
    def drain():
        written = 0
        try:
            with (directory / "train.log").open("ab") as log:
                while block := process.stdout.read(4096):
                    keep = min(len(block), max(0, MAX_LOG - written))
                    if keep:
                        log.write(block[:keep]); log.flush(); written += keep
        except Exception as exc:
            failure.append(type(exc).__name__)
    reader = threading.Thread(target=drain, daemon=True)
    reader.start()
    started = time.monotonic()
    reason = None
    try:
        while process.poll() is None:
            if (directory / "cancel.json").exists():
                reason = "cancelled"; break
            if time.monotonic() - started > timeout:
                reason = "timed_out"; break
            update()
            time.sleep(1)
        if reason:
            stop_owned_process(process)
        reader.join(timeout=10)
        if reader.is_alive() or failure:
            raise RuntimeError("Training log drain failed")
        return process.returncode, reason
    finally:
        stop_owned_process(process)
        process.stdout.close()


def artifacts(directory: Path) -> list[dict]:
    output = directory / "output"
    config = output / "adapter_config.json"
    weights = output / "adapter_model.safetensors"
    for path in (config, weights):
        inside(directory, str(path))
        if not path.is_file() or path.is_symlink() or path.stat().st_size == 0:
            raise ValueError("Soup exited without the expected nonempty LoRA adapter artifacts")
    read_json(config)
    return [{"path": str(p), "bytes": p.stat().st_size, "sha256": digest(p)} for p in (config, weights)]


def worker(root: Path, job_id: str):
    import fcntl  # POSIX host; Android never imports or executes this file.
    directory = job_directory(root, job_id)
    value = {"jobId": job_id, "state": "preparing", "updatedAt": time.time(), "progress": None}
    def update(**changes):
        value.update(changes, updatedAt=time.time()); atomic(directory / "status.json", value)
    lock = (root / "meshlit-jobs" / "active.lock").open("a")
    try:
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        update()
        spec = read_json(directory / "request.json")["spec"]
        config, base, data = validate_spec(root, spec)
        if (directory / "cancel.json").exists():
            update(state="cancelled"); return
        if shutil.disk_usage(root).free < 1024 * 1024 * 1024:
            raise ValueError("Training host needs at least 1 GiB free before model-specific admission")
        staged = directory / "dataset.jsonl"
        # Bound the copy again to prevent a growing source from bypassing admission.
        with data.open("rb") as source, staged.open("xb") as destination:
            copied = 0
            while block := source.read(1024 * 1024):
                copied += len(block)
                if copied > MAX_DATA:
                    raise ValueError("Dataset grew beyond 64 MiB")
                destination.write(block)
        rows = validate_dataset(staged)
        config["data"]["train"] = str(staged)
        config["output"] = str(directory / "output")
        soup_command()
        from soup_cli.config.schema import SoupConfig
        SoupConfig.model_validate(config)  # Never substitute our validation for Soup's schema.
        atomic(directory / "soup.yaml", config)  # JSON is a YAML subset; no string interpolation.
        manifest = {"jobId": job_id, "soupVersion": SOUP_VERSION, "basePath": str(base),
                    "baseConfigSha256": digest(base / "config.json"),
                    "datasetSha256": digest(staged), "datasetRows": rows,
                    "configSha256": digest(directory / "soup.yaml"), "artifacts": []}
        atomic(directory / "manifest.json", manifest)
        update(state="running")
        code, reason = run_logged(soup_command() + ["train", "--config", str(directory / "soup.yaml")],
                                  directory, spec["timeoutSeconds"], update)
        if reason:
            update(state=reason, exitCode=code); return
        if code != 0:
            update(state="failed", exitCode=code, error="Soup training exited unsuccessfully; inspect logs"); return
        update(state="verifying")
        manifest["artifacts"] = artifacts(directory)
        atomic(directory / "manifest.json", manifest)
        update(state="completed", exitCode=code)
    except Exception as exc:
        # No prompts, dataset rows, environment variables or credentials in protocol errors.
        update(state="failed", error=f"{type(exc).__name__}: {str(exc)[:400]}")
    finally:
        lock.close()


def doctor(root: Path) -> dict:
    import tempfile
    command = soup_command()
    with tempfile.TemporaryDirectory(prefix="meshlit-doctor-", dir=root) as temporary:
        directory = Path(temporary)
        code, reason = run_logged(command + ["doctor"], directory, 30, lambda: None)
        with (directory / "train.log").open("rb") as file:
            diagnostics = file.read(6000).decode("utf-8", errors="replace")
    return {"soupVersion": SOUP_VERSION, "python": sys.version.split()[0], "exitCode": code,
            "timedOut": reason == "timed_out", "diagnostics": diagnostics, "trainingProven": False}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("doctor", "start", "status", "cancel", "worker"))
    parser.add_argument("--workspace", required=True)
    parser.add_argument("--spec")
    parser.add_argument("--job")
    args = parser.parse_args()
    try:
        if os.name != "posix":
            raise ValueError("This SSH companion requires a POSIX Linux/macOS host")
        root = workspace(args.workspace)
        if args.action == "doctor":
            reply = doctor(root)
        elif args.action == "start":
            if not args.spec or len(args.spec) > 10000:
                raise ValueError("Missing/oversized job specification")
            spec = json.loads(base64.b64decode(args.spec, validate=True))
            reply = start(root, spec)
        elif args.action == "worker":
            worker(root, args.job); return
        elif args.action == "cancel":
            directory = job_directory(root, args.job)
            reply = status(root, args.job)
            if reply["state"] not in TERMINAL:
                atomic(directory / "cancel.json", {"jobId": args.job, "requestedAt": time.time()})
                reply["cancelRequested"] = True  # The worker must acknowledge completion separately.
        else:
            reply = status(root, args.job)
        print(json.dumps({"ok": True, "result": reply}, ensure_ascii=True, allow_nan=False))
    except Exception as exc:
        print(json.dumps({"ok": False, "error": f"{type(exc).__name__}: {str(exc)[:400]}"}))
        sys.exit(1)

if __name__ == "__main__":
    main()
