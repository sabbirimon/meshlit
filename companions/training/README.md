# Optional Soup training host

This original Meshlit companion drives an owner-installed **Soup 0.75.0** CLI.
It does not bundle Soup/PyTorch into Android and does not perform synthetic
training. Supported adapter: text SFT, Transformers, LoRA ranks 1–64, fixed batch
1, 1–5 epochs, sequence length 128–4096, unquantized or 4-bit QLoRA; experimental
Soup `stream_layers` is an explicit choice. Exact compatibility is checked by
Soup's own schema and actual training runtime. There is no universal GPU support
or guarantee that a model fits. Soup is Apache-2.0; review its NOTICE and all model,
dataset and training dependency licenses before distributing a trained model.

Install Soup and the required GPU/runtime dependencies following its official
instructions, in a dedicated Python 3.10–3.12 environment. This repository does
not run `pip` or install drivers automatically. Copy `meshlit_training.py` onto
your **Linux/macOS POSIX** host, using your normal deployment process. Configure
that host in Meshlit Settings → SSH with its independently verified fingerprint,
then Settings → Fine-tuning with absolute Python, script and workspace paths.
The SSH user owns its workspace. Training is human initiated; no new agent
training delegation or unauthenticated HTTP listener is enabled.

Inside the workspace, prepare a materialized local Transformers snapshot with
`config.json` and nonempty `.safetensors` weights (not a Hugging Face cache made of
symlinks, not GGUF), its tokenizer files, and real Alpaca JSONL records containing
`instruction`, optional `input`, and `output`. Dataset limit: 64 MiB, 1 MiB per row,
at least ten rows. The worker snapshots/hashes the dataset before training.
Frozen base weights must not be modified during a job. The manifest records the
base config hash/path, not an integrity hash of every base weight file. Use an
immutable, independently verified base snapshot for reproducibility.

The app sends a bounded base64 JSON spec through quoted SSH arguments. The host
requires paths inside the workspace and validates them and the Soup schema.
Jobs persist in `meshlit-jobs/<UUID>`, with actual status, config, log and manifest.
Only one supervisor can train in a workspace at once. An SSH disconnect does not
end the detached job. The app saves its UUID before start; refresh that reference
after a missing reply. Reusing an ID with the same spec returns the original job;
a conflicting spec fails. There is no automatic retry or duplicate training start.

The supervisor runs Soup with offline Hub/dataset/Transformers environment flags
and disabled W&B. No base weights or dataset are automatically fetched by this
adapter. Dependency code still executes on the host: use an environment and
models you trust. Logs may contain training excerpts; treat exports as private.
Logs are drained continuously and capped at 8 MiB, with a 6,000-byte tail sent to
the phone. Status has no fabricated loss, epoch progress, throughput or cost.
A successful doctor diagnostic is not proof of model admission or training.

Cancel writes a request for the active supervisor. It terminates only its own
child process group, waits and escalates if needed; no stale PID is killed.
The worker must acknowledge `cancelled`. Default execution limit: four hours;
protocol maximum: 24 hours. A lost worker heartbeat is `unknown`, never completed.
After host reboot or supervisor kill, inspect/clean up the host manually: automatic
resume, orphan reconciliation and cluster failover are not implemented.

`completed` requires Soup exit zero plus nonempty `adapter_config.json` and
`adapter_model.safetensors`, with hashes saved in the manifest. This verifies
artifact presence/integrity, **not learned quality**. Evaluate held-out examples,
merge the compatible adapter into its base and export/quantize GGUF using Soup's
actual supported commands. Then transfer/import the GGUF through Models. Android
direct LoRA loading, automatic merge/export/transfer and training task scheduling
are future work. Quantization affects deployment size; LoRA/layer streaming do
not automatically shrink the base model or implement phone layer sharding.

Contract checks (test subprocesses are not training proof):

```sh
python3 -m unittest discover -s companions/training/tests -v
```

Host diagnostic example (replace paths with actual installed files):

```sh
/opt/meshlit-train/bin/python /srv/meshlit/meshlit_training.py doctor --workspace /srv/meshlit/work
```

No real Soup model-training job has been run in this checkout: a compatible host,
base snapshot and operator dataset are still required for that acceptance gate.
