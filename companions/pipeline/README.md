# Native host worker without the Android app
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-11-24: [Plan](../../PLAN.md) · [Progress](../../PROGRESS.md) · [Document status](../../docs/DOCUMENTATION_STATUS.md).
Scope: optional host/schema/reference; not a bundled qualified service. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

A Linux server/NAS or desktop can join the phone's approved layer-worker list
without installing Meshlit. `worker.py` starts an installed CPU GGML RPC worker
on loopback and serves the same certificate-pinned/token-authenticated TLS
protocol as Android, including live capability offers. Python standard library
only; `psutil` is optional for additional platform memory probes.

Build the pinned native worker on the target host using
`python3 scripts/build-pipeline-native.py`; it is architecture/OS specific.
The default build is CPU-only. GPU/NPU acceleration needs a separately verified
compiled backend and is not claimed by this script. The RPC interpreter remains
experimental: enroll trusted operator-owned hosts only.

Create a private working directory and a TLS certificate (for example with
OpenSSL), record its SHA-256 fingerprint independently, and create a random
32–128 character token in a mode-0600 file. Keep private keys/tokens outside Git.

```sh
python3 companions/pipeline/worker.py \
  --native build/pipeline-host/bin/ggml-rpc-server \
  --certificate /absolute/private/worker.crt \
  --key /absolute/private/worker.key \
  --token-file /absolute/private/pairing-token \
  --node-id 45c9f304-58ae-4248-b8f8-7a419a36cc97 \
  --bind 192.168.1.20 --port 50551 --threads 2
```

Select an actual private interface address. Default bind is loopback. Add the
host/fingerprint/token in Meshlit Network → Add approved worker. Both Android
and host must use the pinned runtime revision. Use an operator-verified
`--memory-budget-mb` limit when memory probing is unavailable, or to lower the
advertised budget. A budget estimate is not a hardware memory reservation.

This is a compute companion. It does not yet implement web enrollment, SSH
installation, phone-replicated task state, NAS checkpoint storage or failover.
No downloaded shell commands or automatic SSH elevation are used.

### Optional Goose layer planner

Build `layer-placement.goose` with Goose revision `2239b92eb2ae0e8f390f98640b09ff57a17698fc`, using `goose build companions/pipeline/layer-placement.goose -o layer-placement`. The arguments are block count, combined weight/KV bytes, per-worker overhead bytes and each worker's admitted capacity in bytes. It prints one integer count per worker in input order. The algorithm preserves a spare layer per worker and uses exact integer comparisons to break utilization ties in roster order. Android uses its in-process Kotlin implementation.

Pass the executable to `scripts/prove-layer-rpc.py --goose-planner PATH --blocks N --kv-bytes K --worker-budget-bytes A B` along with the normal model, runtime and output paths. The harness validates the counts against the provided budgets and passes them to the actual two-worker model server. These capacities are operator estimates. Native placement and completion are checked independently.

On an Intel Mac, the Goose and Kotlin allocators agreed on 52 deterministic inputs covering ties, heterogeneous capacities, 64 workers and 28 rejected cases. The Goose planner placed Qwen2.5 0.5B weights with a 14/10 split across two CPU RPC processes and matched all 32 output tokens from the single-process baseline. Both workers ran on the same host.
