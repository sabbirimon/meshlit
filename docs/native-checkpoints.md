# Native CPU checkpoints and usage correctness

Updated 2026-10-06. This is the first recovery implementation; distributed task
replication, coordinator failover and portable/RPC KV restoration remain pending.

## Human workflow

1. In Models, choose native CPU with the desired context and key-cache precision,
   then load a real model and generate a response.
2. Settings → Checkpoints and recovery → Save completed native cache.
3. After stopping/reloading, load the identical model with identical settings.
4. Select Restore. Native generation can reuse matching prompt prefixes; the
   runtime must report actual cached token usage. Restoring a KV cache does not
   reconstruct chat history, resume external tool actions or finish a task.

The actual native slot save/restore APIs are used. Generation, model replacement,
save and restore share the inference mutex; administrative operations then take
the host mutex in that order. Never take these locks in reverse order.

AES-256-GCM encrypts snapshots using an app Keystore key. The encrypted index is
committed synchronously; each blob is synced and atomically renamed before it is
indexed. Manifest metadata is authenticated as AAD, and plaintext size/SHA-256 is
verified before the native restore request. Plaintext staging is deleted in
finally blocks and on host initialization. Limits: 128 MiB per snapshot, 16
snapshots, 256 MiB total. Blob-transfer replication is not implemented. Free memory and disk-space preflight
reserve staging/cryptography headroom; unknown/low memory fails explicitly.

Restoration requires SHA identity of the entire model and packaged executable,
plus identical backend, context and cache precision. ABI/runtime mismatch is
therefore rejected. The current encryption key belongs to this app installation;
copying an encrypted blob to another phone does not make it recoverable there.
A future replica protocol needs authorized recipient key wrapping and revocation.
These checks deliberately do not assert cross-device cache
compatibility. A corrupted GCM blob cannot be passed to native restore.

## Worker identity correction

The old Android TLS key omitted DIGEST_NONE, required for Conscrypt raw ECDSA.
The corrected v2 Keystore alias permits raw ECDSA/SHA256, and a single-alias
key manager prevents selecting an incompatible legacy key. The private key stays
in Keystore. The v2 certificate has a different fingerprint: existing worker and
control-server pins need independent verification and explicit reapproval.

TCP 50551 must be permitted by the persisted listener firewall. A denied request
is not a successful handshake. Native worker sockets remain private loopback.
The native CPU model server uses `--device none -ngl 0`: CPU is not an offload
device in this pinned server's argument parser.

## Token accounting

Rendering deltas are text chunks. Pinned RunAnywhere 0.20.12 terminal counters have no provenance flag and still
report 3 completion tokens for output previously measured as 6 by its native
backend. SDK usage therefore remains null; estimates/chunks are not billing units.
A stream missing terminal completion fails. Application code no longer truncates
at a number of text events. The SDK receives the real max-token/stop options.

Native completion metadata supplies tokens_predicted, tokens_evaluated,
timings.cache_n, stop_type and native decode throughput. tokens_cached is final slot
occupancy and must not be reported as reused prompt tokens. ONNX text length is not
reported as token usage. HTTP lifecycle/UI metrics preserve unknown usage.

## Agent API

CHECKPOINT_LIST, CHECKPOINT_SAVE, CHECKPOINT_RESTORE and CHECKPOINT_DELETE are
available through the durable typed command controller. Restore/delete accept
only a checkpoint UUID, never a filesystem path. Agents need saved RECOVERY
delegation; enrolled remote devices also need the independently approved RECOVERY
scope. Restoring does not enlarge scopes or execute tasks. Recovery status keeps
replication/failover/portable KV flags false.

## Next recovery milestone

Implement a consented, bounded replicated task/session journal with membership
identity, explicit persistent acknowledgements, conflict checks, fenced ownership
and a recovery checkpoint commit. Keep large blobs on selected content-addressed
holders. A saved local KV snapshot or current capability election is not consensus.

Required fault evidence: lose the elected coordinator, resume from a committed
replica without rerunning completed tool actions, reject an old owner's writes,
rejoin a stale node, and fail explicitly without quorum. Three consenting replicas
are needed to tolerate one failure, five for two, with sufficient reachable durable
copies of referenced model/checkpoint objects. Do not replace these tests with a
planner, UI badge or a single-emulator restart.

## Final emulator observation

API35 x86_64, real bundled SmolLM2/native CPU: saved/restored snapshot occupancy
18 tokens; **actual reused prompt tokens 2**, from `timings.cache_n`. Quantized
K-cache snapshot 318,572 bytes versus f16 415,772 bytes. Context mismatch and
corrupted authenticated ciphertext are rejected; restored seeded output matches.
This is local native executable restart/checkpoint evidence, not phone quorum
recovery, cross-device KV portability or application-process restart proof.
