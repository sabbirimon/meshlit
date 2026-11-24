# Desktop/server security, automation, VM and container design

Updated 2026-10-10. This records requested contracts and current boundaries;
unimplemented backends must remain unavailable in UI and tool discovery.

## Shared permission-controlled tooling

A single typed controller per feature will serve the human dashboard, agent,
hook, MCP/A2A gateway and headless server. Grants must bind actor, function,
resource/target, credential purpose, expiry and request/work budgets. Human
terminal use is available under the current OS account by default; enabling
agent command access is a distinct powerful grant. Shell working directory is
not a filesystem sandbox: a granted unrestricted shell can access the account's
files and run programs. A sandboxed runner must enforce a real runtime boundary.

Full-auto agents may continue approved steps without asking on every step, within
those scopes. Global Stop/revocation must propagate to owned child processes,
network streams and controllers; it cannot honestly claim to stop independent
services/commands after ownership is lost. Browser/PC control requires separate
site/app/input/file grants and the OS's accessibility/screen-recording permissions.
Credential/trust/policy changes must not be inferred from model output or documents.

Current implementation: human local batch commands, pinned outbound SSH,
status-only approved-key inbound SSH, scoped inference-host requests and local
cryptography. The common agent/hook/MCP dispatcher, autonomous desktop control,
durable audit and browser controllers are **not yet ported**.

## Strong cross-platform firewall requirement

Keep application transport policy and the system firewall visibly separate.
Existing Android listener rules are not an OS-wide desktop firewall. Current
Desktop HostClient enforces trusted HTTPS for remote hosts, authenticated access,
no redirects and numeric loopback-only HTTP; that does not filter other programs.

| OS | Native backend direction | Required operations and verification |
| --- | --- | --- |
| Windows | Windows Defender Firewall / supported Windows APIs or signed privileged helper | Profiles, explicit inbound rules, per-program/service scopes, readback, reject unauthorised admin operations; Windows host tests |
| Linux | nftables with a dedicated Meshlit table; detect unsupported kernel/tools | Input/output scope, conntrack return traffic, IPv4/IPv6, atomic checked updates, rule readback and rollback; independent network namespace tests |
| macOS | PF dedicated anchor where supported plus clearly distinguished application firewall | Preserve existing PF state/anchors, syntax validation, owned rule readback and rollback; do not call PF per-app filtering if backend cannot implement it |

Requested defaults: no unsolicited Meshlit public listener, deny unapproved
inbound app-node/control access, independent egress permission for agents/tools,
explicit interface/profile/address/port/protocol scopes and encrypted remote
transport. Changing system firewall policy needs an exact diff and OS-approved
privileges. Preserve connectivity/recovery paths, existing user rules, IPv6 and
established traffic; test lockout and restoration before shipping Apply.

Desktop OS firewall controller status: **planned**. Do not change the user's
current PC firewall while implementing the feature or display a fake “protected”
state. Status must reflect actual observed OS rules, privilege failures and drift.
Remote/cluster policy changes need independent device approval and readback.

## Security tools

Implemented source: SHA-256/SHA-512, random keys, HMAC, AES-256-GCM with fresh
nonces, strict remote API transport and SSH host-key pins. Requested additions:
certificate inspection, scoped DNS/host reachability/port checks, owned-interface
capture, read-only vulnerability/dependency inventory, secret redaction, security
lab assessments and export. Each has human/agent scopes and honest runtime state;
no scanner result may be fabricated from a UI toggle or a model's claim.

Packet capture is off by default and bounded by interface, filter, snaplen, packet
count, duration and disk quota. OS permission/installed capture runtime must be
reported. Private local PCAP storage/export needs a user-selected destination;
no automatic upload, root activation or ambient capture by an agent.

## VM, sandbox and container incubator

An in-app manager and a bundled container daemon/guest VM are different deliverables.
On macOS/Windows, Linux containers normally require a real guest/compatible runtime;
process isolation in the host account is not equivalent to a VM.

| Layer | Required controls | Current desktop status |
| --- | --- | --- |
| Bounded human shell | Selected shell/cwd, real exit/output, timeout, Stop | Implemented source; no PTY isolation |
| Agent runner | Actor/function grant, output/time limits, workspace mounts, environment purpose and audit | Pending |
| Rootless sandbox | Capability probe, filesystem/network limits, precise isolation report | Pending |
| QEMU VM | Verified guest/runtime, CPU/RAM/disk budget, snapshots, console, optional SSH | Pending; no guest/runtime bundled |
| Docker/Podman manager | Exact selected daemon/context, runtime version/state, containers/images/digests/volumes/stats/logs | Planned |
| Incubator workloads | Reviewed recipe/image, no privileged container or host socket/mount/network by default, resource quota, owned lifecycle | Planned |
| Bundled engine | Licence/security/update review, signed native packaging, real boot/runtime proof per OS | Separate planned deliverable |

Never silently install a daemon, pull an image, start a VM or execute an image hook.
Installing/starting a workload must report its actual state, not just submit a task.
Agent opt-in is independent of human runtime use. OS root/admin uses visible native
authentication; no stored root password or self-elevation promise.

## Kubernetes

Kubernetes connects to the exact reviewed kubeconfig/context/server/namespace and
uses real account RBAC; Meshlit does not invent administrator rights. Kubeconfig
exec/auth plugins can run local programs, so loading them is an explicit integration
approval. Begin with read-only inventory, events/logs, pods/deployments/jobs/nodes,
resource and quota views. Mutations require a plan/diff, target scope, human or
independently approved agent action, cancellation outcome, audit and rollback.
Do not expose secrets in tool results, exports or diagnostics. Cluster credentials
are purpose-bound; “use for all” means approved shared consumers, not copying keys.

## Response policy

Local response instructions offer Meshlit, custom or no application system prompt.
A model's alignment/refusals are properties of its weights and host configuration;
remote provider restrictions remain provider-enforced. These text-generation
choices never disable TLS, authentication, firewall, sandbox or tool authority.
Dolphin speech and Dolphin chat remain distinct adapters.

## Qualification gates

Record actual permission denied, missing runtime, timeout, revocation and Stop;
wrong target/actor/expired credential; privileged operation denial; corrupted
artifacts; redaction; rollback; independent OS/device transport. Require source
and package evidence separately. Production candidate admission stays separate
from experimental tool availability.
