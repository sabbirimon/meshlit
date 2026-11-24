# Native host worker without the Android app

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
