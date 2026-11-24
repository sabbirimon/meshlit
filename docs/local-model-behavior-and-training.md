# Custom local models and real fine-tuning

Reviewed 2026-10-06; current source contracts, not a claim of hosted-policy bypass.

## Custom/uncensored model controls

Settings → Custom local model behavior saves an encrypted, human-authored
instruction profile (off by default, at most 4,000 characters). At the next local
coordinator generation, it prefixes those instructions to the original request.
Chat, local recipe steps and the phone provider share that coordinator. Direct
SDK voice/vision calls have their own controls. The policy cannot grant tools,
root, Android access or agent scopes, and does not modify hosted API requests.
Switching off preserves the original prompt exactly. Context limits still apply.
No built-in jailbreak prompt collection, fake success indicator or guarantee
that a model will ignore learned refusals is included.

Use Models to choose/import compatible custom GGUF weights through the existing
SAF/verified-download paths, including user-granted removable storage. Behavioral
properties belong to the actual selected weights; the bundled starter is not
silently replaced or retrained. A USB desktop launcher is not an Android engine.

Reviewed references and decisions:

| Reference | Actual role | Integration decision |
| --- | --- | --- |
| [0xSojalSec/Uncensored-AI](https://github.com/0xSojalSec/Uncensored-AI) | Heretic fork, weight-level directional abliteration/optimization, AGPL-3.0 | No source copied into Apache app; no on-phone removal toggle or hosted bypass claim |
| [Uncensored-Local-AI-Multiplatform](https://github.com/techjarves/Uncensored-Local-AI-Multiplatform) | Flutter local GGUF application, MIT advertised | Local import/weight choice as a design reference; no unnecessary Flutter port |
| [USB-Uncensored-LLM](https://github.com/techjarves/USB-Uncensored-LLM) | Portable desktop local-model launch environment | Removable-model workflow reference; license not verified, no code copied |
| [Soup](https://github.com/MakazhanAlpamys/Soup) | Apache-2.0 host fine-tuning CLI with LoRA/QLoRA and optional layer streaming | Original optional SSH companion targeting reviewed version 0.75.0; no vendored trainer |

A prompt control does not edit weights; abliteration, LoRA, quantization and
inference layer sharding are different operations. Weight edits require separate
quality/regression evaluation. Soup's old 4-GB throughput claims were marked by
upstream as awaiting remeasurement after a correctness fix; Meshlit makes no such
hardware/performance promise.

## Fine-tuning pipeline

```mermaid
flowchart LR
    Human[Review host and real dataset] --> SSH[Pinned SSH connection]
    SSH --> Start[UUID and bounded typed recipe]
    Start --> Worker[Detached owner-installed supervisor]
    Worker --> Snapshot[Snapshot dataset and validate Soup schema]
    Snapshot --> Soup[Actual Soup LoRA or QLoRA process]
    Soup --> Proof[Exit code and adapter artifact hashes]
    Proof --> Eval[Held-out quality evaluation]
    Eval --> Export[Host merge and GGUF export]
    Export --> Models[Transfer and verified Models import]
```

Settings → Fine-tuning saves absolute Python/script/workspace paths tied to an
existing SSH profile. It probes the actual environment, starts explicit approved
jobs, persists their IDs, and requests actual status/logs/cancellation. Saved job
references retain their original SSH host identity signature; editing a profile
to point elsewhere blocks management of those old jobs. There is no automatic
upload of phone conversations or training datasets and no implicit dependency
installation. The narrow supported recipe and all path/output limits are in
[the training companion guide](../companions/training/README.md).

Phone autograd is unavailable. The previous `LocalLoraTrainer` produced synthetic
LCG arrays and counted fake optimizer steps. That production path is removed:
compute/apply now reject unavailable training, and strategy dispatch refuses it
before thermal/network/gradient operations. Registry admission and synthetic
benchmark reporting also refuse unavailable training. Invented losses are removed;
missing desktop gradients/ring delivery and unimplemented DiLoCo peer exchange
return failures rather than fabricated peer contributions. Existing pure planner/averager tests
remain algorithm contract checks, not evidence that phones train weights.

## Acceptance and remaining work

- Behavioral-policy bounds/default-off/original prompt tests and companion
  path/schema/idempotence/version/cancellation/timeout/log/false-completion tests
  verify contracts, not output quality or actual model training.
- Both Android flavors must compile and the fine-tuning/behavior screens must
  render. Actual SSH + Soup training needs a named compatible host and real
  dataset. Never report doctor or fixture subprocesses as that proof.
- Add held-out evaluation/provenance, immutable full base-weight identity,
  artifact transfer and tested merge/export automation before automatic deployment.
- Integrate long-running training with the durable task board and explicit agent
  scopes; jobs currently use this separate host supervisor and saved references.
- Implement host reboot/orphan reconciliation, resumable optimizer/checkpoints
  and cooperative distributed training separately. Phone-cluster inference and
  portable KV/task recovery remain the main core acceptance priority.
