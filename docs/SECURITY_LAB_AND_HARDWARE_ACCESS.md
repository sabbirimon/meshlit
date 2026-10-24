# Hardware access and Security Lab roadmap
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Requested by IMON. **Design milestone, not a deployed penetration-testing suite.**
GitHub Full retains the existing implemented runtime/network features. The future
Play edition has an independently reviewed surface; the present Play Review build
only implements the restrictions described in PLAY_DISTRIBUTION.md.

## Capability probes rather than a universal root switch

### Built-in security references — 2026-10-24 source

Android **Security Lab** and Studio **Security skills** now share eight plain-text
references: security reports, source review, configuration review, dependency
review, crash triage, API authorization, mobile privacy and agent trust boundaries.
People can read or copy them into any supported LLM. Android MCP-capable agents
can call `cyber_skills_list` and `cyber_skill_read`; local chat exposes these with
its selected CYBER tools and saved operation/delegation grant. A model's ability
to follow a reference remains dependent on that model.

The owner requested Claude-Red integration and looser controls. The reviewed
[upstream snapshot](https://github.com/SnailSploit/Claude-Red/tree/a8de813edc9f66f60d44cfe42d47750e89d9930b)
informed this independently written defensive subset. It is not a full import of
upstream attack skills, installers or payloads. The references execute nothing,
cannot grant network access, and do not modify firewall or agent authorization.
Actual lab tools retain exact artifact scope, expiry, separate execution grants,
VM isolation and emergency stop. Studio's read/copy panel does not implement its
pending lab execution or agent MCP bridge.

Unit checks cover catalog uniqueness/bounded content, unknown IDs and denial when
CYBER agent execution is unavailable. APK compilation is separate from physical
agent use; see [progress](../PROGRESS.md) for current qualification.

Probe OS/API, architecture, kernel, available native executables, USB classes,
user-granted handles, root broker, namespaces, cgroups, perf/eBPF permissions,
SELinux policy, `/dev/kvm` and actual vendor runtime libraries. Present supported,
permission-required and unavailable states with evidence. Never infer GPU/NPU
execution, virtualization or radio support from a chipset name or root alone.
Root increases privileges but does not bypass every kernel/driver/SELinux boundary.

| Area | Existing source | Useful extension and required evidence |
|---|---|---|
| Runtime | APP, explicit human ROOT/ROOT_CHROOT, PRoot/Bubblewrap plans, QEMU software VM/SSH/VNC lifecycle | Compatible packaged binaries, process-tree supervision, actual namespace/cgroup isolation and rooted-device cancellation tests |
| Network | Interfaces, DNS and bounded owner-authorized TCP checks; opt-in capture bridge | Approved-target TLS/configuration checks, defensive packet statistics, real driver/capture support and rate limits |
| Monitoring | Actual Android device/process measurements and optional redacted audit exports | Kernel counters, permitted eBPF/perf probes, driver-memory visibility and calibrated hardware readings |
| Devices | USB descriptors/grants, removable storage and capability inventory | Specific serial/SDR/camera adapters with hotplug, permission denial and device lifecycle tests |
| Virtualization | QEMU software emulation configuration | Verify KVM API, architecture, kernel support and permissions before advertising hardware acceleration |
| Model cluster | Native CPU workers and coordinator, local KV checkpoints | Physical phone evidence, resource reservations and replicated/fenced recovery; root is not shared RAM or transformer sharding |

## Blue Team profile

Prioritize authorized asset inventory, configuration drift, certificate/TLS review,
service exposure, application-owned firewall rules, package/version inventory,
logs/audit correlation and bounded detection rules. Report observations, exact
source/version, severity rationale and remediation proposals. Read-only evidence
collection is the default. Remediation has independent saved permissions and rollback.

## Red Team profile

An engagement has an owner-approved target set, time window, techniques, rate and
resource limits, credentials from purpose-bound vault references and an explicit
stop condition. Start with inventory, scoped service enumeration and controlled
configuration validation. Additional tools belong in isolated approved lab/VM
companions with reviewed licenses and recorded versions. Requests outside the
saved engagement are denied even if the executing device is rooted.

Do not expose unrestricted exploitation, credential attacks or wireless disruption
as default agent tools. Firmware/bootloader changes, destructive resets, privilege
changes and kernel policy changes need separate human review. Web/model suggestions
cannot expand authorization. Do not disable SELinux globally as a compatibility fix.

## Human and agent controls

Maintain separate human/agent profiles, exact host/network scope, per-technique
approval, expiry, concurrency/rate/output budgets, battery/thermal limits and an
emergency stop that reaches native descendants. Record command/tool version,
authority, pseudonymous target, outcome and actual verification in bounded audit
history. Sensitive raw evidence belongs in separately approved encrypted artifacts,
not generic telemetry. Existing human-only root boundaries remain until an explicit
capability-broker design and hardware tests authorize narrower agent operations.

## Acceptance and licensing

Use an owned lab with consented targets. Test denial, stop/revocation, namespace
escape boundaries, concurrent workload impact, secret redaction and actual reports.
Do not label simulated/unit results as a completed engagement. Stryker may inspire
workflows; preserve its GPL conditions if integrating a separately licensed
companion, and do not copy GPL source into this Apache-2.0 tree without deliberate
license compatibility review. Missing tools/drivers report unavailable.

Sources reviewed 2026-10-07:

- [AOSP SELinux](https://source.android.com/docs/security/features/selinux): mandatory controls also apply to root processes.
- [AOSP eBPF architecture](https://source.android.com/docs/core/architecture/kernel/bpf): kernel probes can collect statistics; system support does not grant ordinary apps unrestricted access.
- [AOSP traffic-monitor kernel requirements](https://source.android.com/docs/core/data/ebpf-traffic-monitor).
- [Linux KVM API](https://www.kernel.org/doc/html/latest/virt/kvm/api.html): capabilities and device access must be queried rather than assumed.

## Reviewed Android tooling and root references

[Android security tools and Magisk plan](ANDROID_SECURITY_TOOL_INTEGRATIONS.md)
records IMON’s requested checklist, Objection, Drozer, Havoc and Magisk sources,
exact review revisions, license boundaries and companion/hardware acceptance
criteria. These are reviewed plans; none is bundled or operational in this beta.

[Blue Team and packet-analysis roadmap](BLUE_TEAM_AND_PACKET_ANALYSIS.md) adds
Quark, Androguard, archived Cuckoo, Atomic Red Team, Autopsy/Sleuth Kit and the
PCAPdroid/Wireshark workflow with evidence and licensing gates.
