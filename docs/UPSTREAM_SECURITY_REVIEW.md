# Upstream integration security review
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-31: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Reviewed 2026-10-31. **The optional upstream applications are not cleared for
production deployment.** Source/build success and health checks do not resolve
the dependency advisories below. Meshlit does not install or start these services
automatically; connectors require explicit configuration and per-agent selection.

This was a bounded defensive review: pinned source/provenance checks, selected
authentication/network/secret boundaries, dependency advisory queries and local
integration tests. It was not an exhaustive code audit, penetration test, or
proof that unknown vulnerabilities do not exist. No zero-day was demonstrated.
No external service was attacked and no paid model call was requested.

## Scope and dependency results

The first three revisions are recorded in
`companions/studio-runtime/extension/companion-sources.lock.json`. OpenLive's
existing source cache was audited separately.

| Source | Revision | Critical | High | Moderate | Low |
| --- | --- | ---: | ---: | ---: | ---: |
| Claude-Mem | `70aa40042cfb8157a0d84954b9c0d2b30b50af80` | 1 | 5 | 3 | 1 |
| OpenMausBot | `21628af229abe8100cdd1f8761ccca391ff56bff` | 1 | 26 | 18 | 6 |
| OpenBot | `743649efc3f21d7bf0badbe6af2bcd5daedd97ce` | 3 | 41 | 50 | 16 |
| OpenLive | `c9121ebe6ed8217f5b8e7b77c78cb36620c1a252` | 3 | 30 | 26 | 4 |

Counts deduplicate by advisory URL within each repository; package registries can
return several IDs/version ranges for the same advisory. These are advisory
matches across development, optional and workspace dependencies, not a count of
exploitable production defects. Bun's reports do not identify production paths.
The pnpm reports identify non-development matches of C1/H9/M10/L3 for OpenMausBot
and C2/H11/M13/L1 for OpenLive; those still do not establish runtime reachability.
Claude-Mem's root has no committed dependency lock: its locally resolved build
graph is not claimed to be reproducible from the Git pin alone.

Read-only `bun audit --json` and `pnpm audit --json` queries returned advisory
data (exit 1, no stderr). Raw results and the deduplicated summary are retained
under `/Users/code/Documents/Codex/meshlit-companion-cache/security/`.
`docs/desktop/evidence/companion-integration-20261031.json` records compact evidence
and raw-report hashes. No automatic audit fix or unreviewed dependency upgrade
was applied.

The critical matches require follow-up before deployment:

- **Claude-Mem / OpenLive — shell-quote:** a command-quoting injection advisory
  matches the pinned 1.9.0 dependency. OpenLive's reported path is development
  tooling (`concurrently`); Claude-Mem also declares it in plugin dependencies,
  so its root development classification would not establish absence from runtime.
  The advisory's fixed range begins at 1.11.0. [GHSA-pqg4-j6r4-53mv](https://github.com/advisories/GHSA-pqg4-j6r4-53mv)
- **OpenMausBot / OpenBot — better-auth:** an OAuth/Magic Link authentication
  advisory affects versions before 1.7.7. OpenMausBot's reported 1.7.1 path is
  `cloudflare/control-plane`, not evidence that the local desktop harness uses
  this vulnerable flow. OpenBot production reachability was not established.
  [GHSA-965c-763c-88jm](https://github.com/advisories/GHSA-965c-763c-88jm)
- **OpenLive — Next.js:** two remote-code-execution advisory matches affect
  `apps/web`'s 16.3.6 dependency. Their conditions involve image optimization or
  cached functions; neither was exploited or proven reachable in the Meshlit
  voice bridge. The reported fixed range begins at 16.4.1.
  [Image optimization advisory](https://github.com/advisories/GHSA-2464-fhr4-w9xw),
  [cached function advisory](https://github.com/advisories/GHSA-673q-mjm6-r5xr)
- **OpenBot — DOMPurify and proxy-addr:** registry matches concern a conditional
  template-sanitization bypass and trusted-address spoofing. Exact deployed call
  paths and configuration remain unqualified.
  [DOMPurify advisory](https://github.com/advisories/GHSA-cg2g-xh64-v4q7),
  [proxy-addr advisory](https://github.com/advisories/GHSA-jqcg-44mw-7w3h)

## Verified integration boundaries

Meshlit's companion checks accept literal loopback origins, reject redirects,
bound response size/time, discard unrecognized response fields, and make no
request until the user chooses a check. Health schema recognition does not
authenticate a process. OpenBot's generic health response is explicitly labeled
as endpoint responsiveness, with identity/model readiness unverified.

OpenMausBot paired tokens are held in VS Code SecretStorage. Saved connectors
contain a secret reference, fixed `mcp` arguments and port. Credentials are read
only after connector selection and confirmation, and the runtime accepts only
the explicit token/port/loopback-URL environment map. Both `OMB_PORT` and
`OPENMAUSBOT_URL` are pinned because upstream URL precedence could otherwise
retarget a token through an inherited variable. Tests verify secret omission
from previews, model requests and tool events. This protects the integration
boundary; the explicitly selected CLI itself receives the credential and must
be trusted. Removal also requires upstream session revocation to invalidate it.

The source preparer verifies exact commit/origin/root license and refuses dirty
or mismatched existing checkouts. It disables Git hooks and LFS smudge and does
not run installers. Separate dependency preparation used ignored lifecycle
scripts. Explicit build scripts still execute upstream code; they ran only in
the external cache, with no app installation or background account setup.

## Upstream configuration observations

- Claude-Mem defaults to loopback, but permits a non-loopback worker bind and
  warns that its full API/settings must then be protected. Its defaults include
  a Claude observer/fallback and transcript watching when configured; telemetry
  is opt-out. Our isolated worker test disabled transcript capture, cloud sync,
  telemetry, model fallback and optional watchers; a macOS network sandbox
  allowed only loopback. No capture hooks were installed. These choices are
  qualification settings, not a modification of upstream defaults.
  [Settings defaults](https://github.com/thedotmack/claude-mem/blob/70aa40042cfb8157a0d84954b9c0d2b30b50af80/src/shared/SettingsDefaultsManager.ts),
  [worker binding](https://github.com/thedotmack/claude-mem/blob/70aa40042cfb8157a0d84954b9c0d2b30b50af80/src/services/worker-service.ts)
- OpenMausBot's paired MCP process can invoke powerful permitted tools. Its
  upstream pairing/session and human approval boundaries remain authoritative;
  a responsive health endpoint is not permission to operate a computer.
  [MCP contract](https://github.com/milind-soni/OpenMausBot/blob/21628af229abe8100cdd1f8761ccca391ff56bff/docs/mcp-server.md)
- OpenBot is a deployment template: its example environment/policy requires
  owner configuration before real accounts/tools or access by other users.
  The public example vault key is for development and upstream refuses it in
  production. Its policy gateway and AG-UI protocol are not silently replaced
  with Meshlit MCP grants. No OpenBot server or Docker service was started.
  [Example environment](https://github.com/CopilotKit/OpenBot/blob/743649efc3f21d7bf0badbe6af2bcd5daedd97ce/.env.example)
- OpenLive's existing origin/token controls were inspected at selected bridge
  boundaries. The complete host, Electron/web applications, native libraries
  and all provider implementations were not exhaustively audited.

## Other supplied sources and limits

[Impeccable](https://github.com/pbakaus/impeccable) was used as read-only design
guidance from its main-branch skill/references. It was not installed, cloned or
executed; no exact revision was recorded. Its installer, hooks and full source
are therefore **not audited**. [Pipecat](https://github.com/pipecat-ai/pipecat) and
[Hugging Face speech-to-speech](https://github.com/huggingface/speech-to-speech)
were optional references, not installed integrations, and were not audited here.

No paid-provider/account flows, full desktop companion installers, mobile
upstream runtimes, semantic memory search, Docker deployment, browser exploit
paths, complete dependency provenance, or unknown-vulnerability guarantees were
qualified. Resolve relevant advisory matches, recheck the chosen deployment
configuration and run feature-specific tests before production use.
