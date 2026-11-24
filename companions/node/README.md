# Storage and offline DSP node companion

Original standard-library Python 3 source. Intended for operator-owned Linux/Pi/NAS or
desktop hosts; tested locally on macOS. Other OS/hardware acceptance remains open.
It advertises real OS/architecture/logical CPUs/free storage and only its implemented
storage/offline FFT capabilities. It never advertises transformer, GPU/NPU or radio TX.

```
python3 meshlit_node.py probe --objects /owned/private-objects
python3 meshlit_node.py stage --objects /owned/private-objects --file /owned/artifact --sha256 VERIFIED_DIGEST
python3 meshlit_node.py serve --objects /owned/private-objects --token-file /owned/private-token --port 18895
python3 meshlit_node.py iq --file /owned/recording.f32 --sha256 VERIFIED_DIGEST --sample-rate ACTUAL_RATE --samples 1024
```

The object/token directories and files must be private on POSIX (0700/0600); tokens
have at least 32 non-whitespace characters. Listener is **loopback only**, defaults
off, and rejects browser origins. Use an independently approved private SSH/TLS tunnel.
POST `/node` with bearer auth accepts `{"operation":"probe"}` or
`{"operation":"read","sha256":"VERIFIED_DIGEST","offset":0,"length":1048576}`.
Staging/deletion/arbitrary commands are deliberately absent from the server API.

Staging streams and verifies exact SHA before atomic publication. Limits: 2 GiB/object,
8 GiB directory quota, 64 MiB free-space reserve, 1 MiB/read, two active operations and
5-second socket deadlines. Reassemble chunks and verify the complete object SHA before
model/checkpoint use. Chunk hashes alone are insufficient. Private object directories
are operator-owned; hostile filesystem mutation is not handled as a trusted object.
Preserve sensitive checkpoints as recipient-encrypted blobs; no encryption key sharing
or automatic replica placement is implemented. Clean unneeded objects deliberately.

IQ analysis verifies an owned ≤128 MiB file, reads 256–4096 interleaved little-endian
float32 I/Q samples, performs an actual radix-2 FFT and reports 16 spectral peaks.
Sample rate is mandatory, values must be finite, amplitudes are uncalibrated, the
window is rectangular and frequencies are offsets from an unknown center frequency.
No live capture, demodulation, absolute dBm, SDR driver, transmit or invented link proof.

Tests use actual files/sockets and a mathematical tone fixture, clearly separate from
real-radio data. `python3 -m unittest discover -s companions/node/tests -v`.
Android device enrollment, automatic deployment, the full node-management UI and
hardware/media/MCU/radio/accelerator adapters remain future milestones.

## SDK/topology probes and network measurement

Keep this entire `node/` directory and the sibling `cyber/` companion when installing;
`accelerators.py` reuses the bounded fixed-command probe helper. Run
`python3 meshlit_node.py accelerators` for actual installed driver/SDK version probes
and OS memory/NUMA/network-link observations. Unknown memory types, bus width and
native fabric readiness stay unknown. Profiler/version presence never qualifies a
model or enables clock changes. The server's `accelerators` operation is disabled
unless the human starts it with `--allow-accelerator-probe`.

HTTP/1.1 responses support persistent connections; small replies use TCP_NODELAY.
Run the repository's `scripts/benchmark-node-network.py --endpoint
http://127.0.0.1:18895/node --token-file /owned/private-token` deliberately against an
owned endpoint. It reports sequential authenticated application RTT, p50/p95/p99/max
and population deviation. It includes JSON, scheduling and OS/storage probe work;
it does not establish HFT/NIC/RDMA/one-way performance. Future AI-oriented native
transports and direct/kernel backends are described in `docs/PLATFORM_ADAPTER_PLAN.md`.
