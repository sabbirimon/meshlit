# An open resource network for everyone
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-31: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Updated 2026-10-31. Participation is intended for individuals, shared households,
independent operators and organizations of any size. Buyers and contributors
should not need to use the Meshlit UI. This is a product goal, not a promise of
income or a live marketplace.

## Why and what changes first

Spare resources can have value only when another party can reliably request,
receive and verify useful work. First strengthen model admission, scheduling
and pairing. Reuse the authenticated model gateway for compatible external
clients, with bounded grants and machine-readable capabilities. Keep large-model
layer execution separate from distributing independent inference requests.

| Work | Where | Implementation and verification |
| --- | --- | --- |
| Memory-safe sharding and engine admission | `core-inference`, portable desktop engine sources | Review memory arithmetic and placement; regress malformed/oversized inputs; physical multi-device proof remains separate |
| Fair dispatch | Android `MiniRouter` and peer selection | Use only approved, healthy eligible peers; verify selection and failure behavior without fabricated load signals |
| Strong pairing | `core-trust/PairingAuthority` | Bind sessions to issuer identity, prevent proof replay throughout the acceptance window; deterministic expiry/rotation tests |
| Bounded external participation | `core-mcp/gateway`, Android `GatewayHost` | Persist a finite per-client request allowance before dispatch; verify restart, exhaustion and persistence failure |
| Interoperability | Authenticated `/v1/resources` and existing `/v1/chat/completions` | Publish only scoped configured model IDs and actual protocol limits; test real local HTTP, no account signup required for a local client |
| Clear app setup | Android gateway and Studio settings | Separate contribution setup from usage records and future commercial settlement |

## Resource scope

The current gateway serves buffered text inference and separately scoped MCP/A2A
operations. Android hosting is foreground/screen-bound, loopback-only and off by
default. Remote buyers need an owner-configured encrypted transport. A model in
the catalog is configured, not a guarantee that it is loaded, fits memory or can
meet a performance target. Some configured routes use another provider; those
are not spare local compute and can incur that provider's charges.

Storage/cache hosting, bandwidth resale, sensors, raw GPU rentals, automatic
market matching and payments are not implemented resource services. Do not enable
advertising or accept customer work for them until their adapters actually exist.
Private prompts/KV caches are not public cache inventory. Sensor access requires
specific consent. Sharing network capacity is not permission to run an unrestricted
proxy. Device pairing never grants arbitrary resource access by itself.

## Execution topology: local, remote and mixed

| Requested mode | Source boundary | Next evidence |
| --- | --- | --- |
| One local device | Whole-model inference; runtime-specific CPU/accelerator controls | Memory/thermal and model-quality qualification for the chosen device |
| One remote device | Approved model endpoint or paired host, via authenticated encrypted remote transport | Actual external client, revocation, latency and cost measurements |
| Multiple local devices / one LAN cluster | Native Android RPC coordinator/workers and layer placement; desktop host proof exists separately | Model larger than each physical worker; allocations, outputs, cancellation and failure measured |
| Multiple WAN devices | Pinned RPC supports an explicit routable host; no built-in NAT traversal or WAN placement optimizer | Cross-owner grants, network budgets, leases and real WAN layer execution |
| Multiple local/WAN clusters | Target: hierarchical scheduling across independently governed clusters | Cluster-level reservations, fencing, model/runtime identity, recovery and inter-cluster transport are not complete |
| Mixed local and remote workers | Target: approved placement across both, with per-link admission | No assumption that pooled memory alone makes execution feasible |

Sharding one model means executing different tensor/layer work on cooperating
devices. Serving independent requests on several complete models is load
balancing. Splitting a GGUF file for storage is a third operation. The UI and
external contracts must keep these distinct. A single device may use its supported
native parallel runtime; that is not evidence of multi-device sharding.

The scheduler must first check model/tokenizer/runtime compatibility, independent
owner consent, resident weights + KV + graph/headroom, and current reservations.
Then compare measured link latency, sustainable throughput, reliability, energy
and permitted cost. For a prospective transfer, a planning estimate can use
`round trips × observed RTT + bytes / observed throughput`; unknown measurements
must remain unknown. This is an admission estimate, not a benchmark or guaranteed
completion time. Repeated layer synchronization makes WAN sharding different from
sending a whole independent job to a remote cluster.

## Pairing carriers and data paths

One identity/grant model should sit above carrier-specific adapters. An invitation
announces an identity and endpoint; possession of an invitation or proximity does
not grant model access. Verify the identity, complete a fresh signed challenge,
obtain receiver approval, then negotiate resource scopes and expiry. Revoke at the
identity/grant layer regardless of which cable or wireless path delivered it.

| Carrier requested | Current evidence / implementation direction |
| --- | --- |
| Ethernet, LAN and fiber | Existing IP transports work over an OS-provided routable interface; fiber is a physical link, not a special trust mode. No dedicated fiber hardware qualification claimed |
| Wi-Fi / internet | Existing discovery and explicit host paths; discovery alone is not trust. WAN requires authenticated encryption and explicit reachability setup |
| USB-C | OS-supported networking/tunnel can carry existing IP protocols. A USB connector does not prove peer networking; direct accessory transport needs its own adapter/permissions |
| Bluetooth / radio | Carrier-specific bounded invitation/data adapters remain to be integrated and qualified; no blanket driver or radio-protocol claim |
| Light / QR | Current QR/text invitation flow; optical blinking/Li-Fi is a separate unimplemented transport |
| NFC | Existing foreground NFC invitation exchange; physical interoperability still needs device evidence |
| Sound | Existing GibberLink PCM messages are unauthenticated received text, not automatic pairing. A future compact invitation/reference must fit packet limits and undergo the same verification/approval |
| Other links | Add behind a versioned transport adapter with cancellation, packet bounds and capability reporting; never infer bulk-inference suitability from successful pairing |

Never place a recovery phrase, private key or reusable full-access bearer token in
public QR, acoustic or optical broadcasts. Pairing is not permission for sensors,
bandwidth resale or unrestricted relaying. Existing raw TCP activation/plain HTTP
prototype paths must not be marketed as secure WAN paths. In particular, do not
reverse-proxy the legacy inference listener onto the internet: it may trust local
callers. The scoped authenticated gateway is the external-client entry point.

## Smart communication router, balancing and fallback

Separate three decisions: **which service can perform the job**, **which devices
can host its layers**, and **which approved path reaches the chosen identity**.
Changing a path must not silently change the recipient, permissions or provider.

The recovery sync path is the first bounded multiple-link implementation: an
owner may configure alternative pinned HTTPS or explicit loopback-tunnel routes
to the same replica member. Use actual observed success/latency and cooldowns;
do not equate an unmeasured link with zero latency. The journal's term, sequence,
hash and acknowledgements remain authoritative; link selection is not consensus.
No arbitrary radio/USB driver, multipath striping or bulk cache sync is implied.

| Failure point | Allowed behavior |
| --- | --- |
| No eligible peer, expired grant, stale memory plan | Block with a reason; refresh measurements/approval or ask for a new plan |
| Before dispatch to a configured path | Select an approved eligible path within the deadline and owner policy |
| Read-only replica status fails | A bounded alternate-path read may be safe; preserve cancellation and total deadline |
| Write sent, outcome unknown | Do not blindly repeat. Reconcile durable identity/sequence/idempotency state first |
| Tokens already streamed or tool may have executed | Stop with interrupted/uncertain state; no automatic provider replay |
| Layer worker disappears | Stop the current generation; replan only after resource checks. Recovery requires a compatible committed checkpoint |
| Remote route would incur cost | Require a separately approved route and budget; no silent paid fallback |

Fair rotation between fresh healthy peers is implemented separately from
load-aware reservations. Real load balancing still needs authenticated queue,
memory, thermal and workload observations, admission slots, and lease expiry.
Sticky sessions may reduce reload cost but must never override revocation or a
failed admission. Nested clusters must fence old coordinators before reassignment.

## Device health, worker limits and coordinator takeover

A workstation can coordinate a phone/desktop/server cluster, but being coordinator
does not give it authority to exceed a worker's limits. Each worker remains the
authority for its RAM, CPU/GPU slots, temperature, battery, power policy, network
allowance, consent and availability. Advertise reduced capacity or withdraw it;
the coordinator must replan or stop, never force the old allocation.

Current Android source adapts **new-load** CPU thread caps from real thermal,
power-save, battery and memory-pressure readings, while preserving the owner's
lower cap. Severe thermal state, OS low memory or critically low unplugged battery
blocks new heavy work. Missing health readings remain unknown. The next healthy
probe can restore the allowed cap. This does not dynamically change an already
running native context, migrate its KV state or increase worker count.

The desired automatic controller uses sustained observations and hysteresis:
reduce admission promptly under pressure; increase slowly only after a stable
healthy interval and fresh reservations. Preserve interactive work and explicit
owner ceilings. A software error, invalid model output or corrupted checkpoint
must not be treated merely as a request for more CPU. Distinguish paused,
degraded, failed and recoverable states in status/metrics.

The coordinator is a **role**, not permanently a desktop. A suitably capable,
approved phone can take over after a workstation fails, just as another server
can. Eligibility requires runtime/model access, enough coordinator memory/storage,
reachable workers, recovery keys and current authority; device category alone
does not qualify it. Failure detection alone cannot grant ownership.

Required takeover sequence: detect loss → establish quorum and a new fenced
ownership epoch → reject old-coordinator writes → load a verified compatible
committed journal/checkpoint → re-admit remaining workers → resume at an
acknowledged boundary with idempotent tool/job handling. Without quorum or usable
state, report paused/recovery-required, not success. A network partition must not
create two active masters. Physical kill/partition/rejoin tests must prove no
duplicate external actions and no stale-owner commits before automatic takeover
is enabled. Desktop native coordinator UI/controller and automatic mobile takeover
are explicit open gates; existing planner election is not that implementation.

## Corruption detection and recovery

The current storage path verifies exact transfer lengths, contiguous manifest
ranges and a required whole-model SHA-256 before publishing an artifact. A bounded
refetch can replace damaged data from the configured source. Partial resume needs
independently supplied per-shard digests and rehashes retained ranges; unknown
integrity metadata requires a fresh verified transfer or an explicit blocked state.
An old valid destination must survive failure of its replacement. A digest obtained
from the same malicious unauthenticated source is not an authenticity guarantee.

Existing local native checkpoints use AES-GCM authenticated metadata and ciphertext,
exact byte counts and SHA-256, plus model/runtime/options compatibility. Restore
rejects corruption before calling the native slot loader. It does not repair opaque
GPU pointers, arbitrary system RAM or distributed tensors. Hardware ECC and future
transport FEC may help within their documented fault bounds; they are not universal
software bit-repair guarantees and are not newly implemented here.

Recovery rules: retry immutable bad chunks within a bounded budget; reject damaged
saved state and choose a verified older compatible checkpoint if explicitly selected;
stop a corrupt native context and rebuild from verified inputs. Never modify model
weights by guessing bits or rerun completed external actions. Automatic distributed
compute resumption still requires the takeover/commit protocol above. Progress
statistics must distinguish bytes reverified, discarded/retried work, committed
offsets and uncertain outcomes; no recovered tokens or earnings may be fabricated.

## Existing external ecosystems

Independent operators can investigate [Vast host onboarding](https://docs.vast.ai/host/hosting-overview)
or [Akash provider onboarding](https://akash.network/docs/providers/getting-started/)
when their hardware and operation meet those services' requirements. These are
reference integration targets, not installed Meshlit adapters or partnerships.
[vLLM's compatible serving API](https://docs.vllm.ai/en/latest/serving/online_serving/openai_compatible_server/)
is another external-client protocol reference; its API key does not protect every
endpoint, so public deployment needs a reviewed authenticated transport boundary.
No documented acceptance by a closed-model vendor as a compute supplier was
established in this review. No earnings or demand are guaranteed.

## Commercial implementation sequence

1. Qualify reliable inference for approved external clients, with Stop, quotas,
   foreground/power limits and measured throughput on each supported device.
2. Add versioned offers with model/runtime identity, supported resource units,
   measurement provenance, availability/expiry and explicit owner caps. Require
   authenticated reservations and fenced leases before scheduling remote work.
3. Add durable idempotent job receipts agreed by requester and supplier. Record
   actual units only; unknown usage stays unknown. Handle cancellation, partial
   work, duplicate jobs, disputes and fraud before computing a payable amount.
4. Integrate one actual buyer/provider protocol with sandboxed jobs, revocation,
   host qualification and its accepted terms. Preserve model/data licensing.
5. Add payment settlement through an appropriate service and validate the complete
   paid workflow. Signed local contribution counts are not a currency or payout.

No automatic public exposure, paid-provider resale, onboarding or payment is
enabled by this plan. There is no minimum company size or company-only gate;
eligibility depends on consent, verified capabilities and a buyer's actual needs.

## Acceptance still required

Physical oversized-model execution across heterogeneous phones, useful performance
after network/thermal overhead, worker loss and revocation under load, clean
desktop packaging, real outside-client interoperability, external buyer demand
and end-to-end settlement remain separate gates. Unit tests do not establish any
of those outcomes. Latest results belong in `PROGRESS.md`.

## Optional security controls

Android Agent Gateway now offers a saved, default-off prompt-pattern heuristic.
It checks bounded input/output locally and can reject suspected instruction
injection; false positives and missed attacks remain possible. It is not a malware
scanner. Human owners can separately delegate on/off changes to agents. Such
clients also need MCP scope and the exact `gateway_prompt_protection_set` tool.
The tool cannot grant its own delegation or alter other policy fields. Turning
this optional check off never disables authentication, hashes, encryption,
resource limits, sandbox boundaries or receiver tool permissions.

Future malware-containment work needs real scanner adapters, provenance/signatures,
quarantine, archive limits and isolation before imported code executes. No binary
antivirus engine was installed or advertised in this increment. Treat all peer,
model, tool and retrieved text as data; a text claim is never a permission grant.

## Graceful degradation

If an ideal node or target performance is unavailable, re-evaluate each remaining
live node against its own verified capacity. Suitable independent work may continue
with fewer workers, smaller admitted batches, CPU execution or storage/control roles.
Do not drop integrity or consent to meet throughput. A different model, reduced
context or quality tradeoff needs an explicit supported policy/owner choice.
If the unchanged model cannot fit, pause it; do not claim successful sharding or
silently load the whole model on an undersized phone. Automatic migration/takeover
must still meet the journal, fencing and checkpoint gates above.
