# Distributed training config examples

Three ready-to-use `DistributedConfig` files that match the
§0 pros/cons table in
`/Users/code/.puku-cli/plans/distributed-hatching-bird.md`.

The files are JSON (NOT YAML) because `DistributedConfigLoader`
only parses JSON today. Loading from disk:

```kotlin
val cfg = when (val r = DistributedConfigLoader.fromFile(path)) {
    is MeshlitResult.Success -> r.value
    is MeshlitResult.Failure -> {
        // Show typed error: r.error.tag tells you what's wrong
        // (e.g. cluster.trainer.config_version_too_new, parse error).
        return
    }
}
```

## `cluster-p2p.json` — P2P ring all-reduce (default)

The default strategy. Every step crosses every peer once via the
existing `GradRingPacket` ring. Same code path as the pre-Phase-11
training loop — the strategy dispatcher is a wrapper, not a
replacement.

**When to use:** Mixed devices, churny networks, phone + laptop +
desktop. Best default for most users.

**Tunings:** `checkpointEvery=8` (every 8 steps write a
`filesDir/training/<jobId>/step-N.bin`), `keepLastN=5` (reclaim
older checkpoints), `optimizerOffload=NONE` (assume enough RAM).

## `cluster-diloco.json` — DiLoCo outer averaging

Inner AdamW per peer for 500 steps, then a single outer-averaging
round every 500 inner steps via the same ring. Reduces comms ~100×
at the cost of slightly worse convergence on aggressive
hyperparameters.

**When to use:** Heterogeneous bandwidth; intermittent phones;
nightly training over consumer links.

**Tunings:** `innerSteps=500` (outer step every ~500 inner steps),
`outerLr=0.7` (Nesterov factor — keep between 0.1 and 1.0 or the
run diverges), `optimizerOffload=CPU` (the outer-averaging state
spills off-GPU if a phone RAM-bumps).

## `cluster-accelerate.json` — Accelerate desktop-peer delegation

The Android side stays an observer; training runs on a desktop peer
through `core-ssh`. Requires a desktop Linux/macOS box with NVIDIA
GPU + Python + HuggingFace Accelerate.

**When to use:** Single desktop/server/workstation with 1+ NVIDIA
GPU; dev iteration; per-step latency is the bottleneck.

**Tunings:** `checkpointEvery=25` (fewer checkpoints because the
desktop has more reliable storage), `outerLr=0.7` (matches
`DiLoCo` defaults even though `Accelerate` ignores it locally),
`optimizerOffload=NONE` (the desktop has plenty of RAM).
