# Desktop companion services
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-31: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: desktop/server; individual acceptance gates apply. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Meshlit Studio now connects to the full workspaces of Claude-Mem, OpenMausBot
and OpenBot through **Settings → Companion services** (`Meshlit: Companion
Services` in the command palette). Each upstream application remains independently
installed and configured. Opening its workspace provides its own UI and features;
it does not turn every upstream function into a native Meshlit function.

The reviewed dependencies have unresolved security advisories. These optional
services are not production-qualified; see [the bounded security review](UPSTREAM_SECURITY_REVIEW.md).

## Prepare the reviewed source

From the Meshlit checkout:

```sh
python3 companions/agents/prepare.py --cache /absolute/external/companion-cache
python3 companions/agents/prepare.py --cache /absolute/external/companion-cache --verify-only
```

Use a cache outside the Meshlit repository. The helper fetches shallow checkouts
at the revisions in
`companions/studio-runtime/extension/companion-sources.lock.json`, checks the exact
commit, repository origin, tracked worktree and root license, and reports its
SHA-256. Existing mismatched or edited checkouts are preserved and rejected.
Git hooks, LFS downloads, submodule setup, dependency installers, service startup,
account setup and model downloads are not performed. `--source claude-mem`,
`--source openmausbot` or `--source openbot` selects one source; `--report PATH`
writes the provenance JSON.

On this development machine the three verified sources are under
`/Users/code/Documents/Codex/meshlit-companion-cache`. These external sources,
dependencies and binaries are excluded from the Meshlit deliverable and Git tree.
Preserve upstream licenses and notices if separately distributing their builds.

| Source | Reviewed revision | Root license |
| --- | --- | --- |
| [Claude-Mem](https://github.com/thedotmack/claude-mem/tree/70aa40042cfb8157a0d84954b9c0d2b30b50af80) | `70aa40042cfb8157a0d84954b9c0d2b30b50af80` | Apache-2.0 |
| [OpenMausBot](https://github.com/milind-soni/OpenMausBot/tree/21628af229abe8100cdd1f8761ccca391ff56bff) | `21628af229abe8100cdd1f8761ccca391ff56bff` | Apache-2.0 |
| [OpenBot](https://github.com/CopilotKit/OpenBot/tree/743649efc3f21d7bf0badbe6af2bcd5daedd97ce) | `743649efc3f21d7bf0badbe6af2bcd5daedd97ce` | MIT |

## Connect and understand the state

1. Install/start the selected companion using its reviewed upstream instructions.
2. Enter its **API origin** and **Workspace origin**, then **Save addresses**.
   Origins use literal loopback `127.0.0.1` or `[::1]`; credentials, queries,
   fragments, remote hosts and arbitrary paths are refused.
3. Choose **Check endpoint**. The read-only check has a five-second deadline,
   64 KiB response cap and no redirects. **Cancel check** cancels this request.
4. Choose **Open workspace** to open its full UI in the browser. Authentication,
   capture permissions, model selection, connected apps and spending controls
   belong to that companion. Meshlit's model budget does not cap upstream spending.

Saving an address performs no network request. Checks occur only on request,
and the result is session-local with its check time. No continuous background
polling occurs. **Remove addresses** removes only Meshlit's saved origins; it
does not stop the companion, delete its data or remove independent MCP connectors.

| Companion | Health and UI | Meaning of a successful check |
| --- | --- | --- |
| Claude-Mem | Worker `/api/health`, UI `/viewer`. Default worker port at this revision is `37700 + uid % 100`; Windows fallback is `37777`. Use the worker's printed address. | Recognized health shape, worker initialization and its reported MCP readiness. No observer/model/search quality proof. |
| OpenMausBot | `/api/health`, usual harness port `8799`. Packaged UI uses the same origin when `static:true`; source UI normally uses `5199`. | Recognized `app:openmausbot` response. Does not verify bot credentials, computer availability or connected apps. |
| OpenBot | API normally `3001/health`; workspace normally `3010/`. | Only endpoint responsiveness. Upstream returns generic `status:ok`, so identity, authentication and model readiness remain unverified. |

Health recognition is a protocol check, not cryptographic identity attestation.
The dashboard discards unknown fields instead of forwarding configuration,
paths, account data or raw error responses to the webview.

## Claude-Mem memory tools

The upstream worker supports persistent observations/session summaries and
progressive search, timeline and selected observation details. Its optional cloud
sync, screenshot capture and observer provider are controlled upstream. Review
those settings before enabling them: the upstream installer may offer an observer
trial and fall back to a paid model plan when the trial expires.

For tool access, run the installed **separate MCP HTTP server**, then save its
`/mcp` address (default MCP port `37790`) and choose **Add memory MCP connector**.
The connector joins Meshlit's existing MCP directory, disabled by default. Select
it for an Edit or Autopilot agent; each tool call still requires review. Worker
health does not establish that this separate port is running. Adding the connector
does not install capture hooks, copy Meshlit chats, enable automatic recall, or
grant cloud sync. Meshlit's own project-memory feature remains independent.

At this pin the source build is `npm run build`; it builds the plugin artifacts.
The runtime needs Bun (`>=1.1.31`) and uv for optional Python/Chroma work. The root
does not include an npm package lock, so a root dependency install is not claimed
as reproducible merely because the Git commit is pinned. The generated plugin has
a committed `plugin/bun.lock`. Do not use `build-and-sync` to merely build: it also
updates an external marketplace installation and restarts its worker.

## OpenMausBot teams and paired MCP

The complete companion owns bots, channels, tasks, computers, approvals,
connected apps and voice. Its documented MCP interface provides inspection,
bounded transcript search, profile/channel/task management, send/wait/interrupt
and model selection. Approval grants, credential changes and computer lifecycle
stay in the upstream human-facing app.

To enable authenticated MCP:

1. In OpenMausBot, follow **Settings → Phone → Set up a phone** and the
   [upstream MCP pairing instructions](https://github.com/milind-soni/OpenMausBot/blob/21628af229abe8100cdd1f8761ccca391ff56bff/docs/mcp-server.md)
   to obtain a paired session token. Do not paste tokens in chat or a repository.
2. Save the exact `http://127.0.0.1:PORT` API origin in Meshlit. This paired adapter
   currently supports explicit ports `1024–65535`, not remote or IPv6 sessions.
3. Choose **Add paired MCP connector**, select your installed `openmausbot` CLI,
   and enter the `omb_sess_…` value in the password field. Meshlit fixes its
   arguments to `mcp`; arbitrary secret arguments/environment variables are refused.
4. Select that connector when choosing tools for an Edit or Autopilot agent.
   A native confirmation names the executable and bound port before SecretStorage
   is read. The token is passed only to the selected MCP process, with both
   `OMB_PORT` and `OPENMAUSBOT_URL` pinned to the approved loopback endpoint.

Persistent settings contain only a secret reference and port. Neither the
selection preview, model prompt nor webview receives the token. The child process
has the token because it must authenticate; select only a trusted installed CLI.
There is no inferred permission from the connector's name or a health response.

**Remove paired MCP connector** closes Meshlit chats using it, removes its registry
entry and erases its saved secret. Also revoke the paired client in OpenMausBot
Settings → Phone to invalidate the upstream session. Changing/removing dashboard
addresses does not retarget an existing paired connector: its endpoint stays bound
until it is removed and paired again.

At this pin source requirements are Node 24+ and pnpm (the source names 10.33.0;
its workspace also supports pnpm 11). `pnpm install --frozen-lockfile --ignore-scripts`
prepares dependencies without installing hooks or running lifecycle downloads.
`pnpm build` compiles/typechecks the app and `pnpm build:server` builds the harness.
If pnpm 11's dependency-status check requests an unattended module purge, run
the pinned scripts explicitly instead of permitting an automatic reinstall:

```sh
node node_modules/typescript/bin/tsc -b
node node_modules/typescript/bin/tsc -p tsconfig.server.json
node node_modules/vite/bin/vite.js build
node node_modules/typescript/bin/tsc -p tsconfig.server.build.json
node scripts/bundle-server.mjs
```

`pnpm dev:server` and `pnpm dev` are separate source-runtime commands. Complete
desktop packaging additionally requires its platform-specific Electron, speech,
browser, CUA and other runtime assets; a web build is not that installer.

## OpenBot governed workspace

OpenBot is a customizable source template. Its full deployment needs Bun 1.4+
(the reviewed package manager pin is 1.4.3), Docker, PostgreSQL, configured model
access and CopilotKit Intelligence configuration/license. `bun install --frozen-lockfile`
prepares its workspaces; use `--ignore-scripts` for reviewed dependency preparation
without lifecycle hooks. `bun run build` generates tenant app configuration and
builds them. Follow the pinned setup guide for `.env`, migrations, authentication
and runtime startup. Meshlit does not fabricate any required account keys.

AG-UI bot endpoints are not MCP endpoints. This dashboard opens the full workspace
and checks the API; it does not register a native AG-UI adapter, reproduce OpenBot's
policy gateway, start containers or turn its permissive example policy into a
Meshlit grant. Configure upstream policy before granting real tools.

## Verification and boundaries

The focused checks cover literal-loopback validation, response limits, deadline
and cancellation, service-specific evidence, local persistence, secret references,
selection cancellation, and connector revocation. Real Antigravity/MCP fixture
processes verify approval/denial and paired-secret environment transport, including
absence of the test secret from model requests and tool events. They are transport
tests with a synthetic backend, not live Claude-Mem, OpenMausBot or OpenBot account
qualification. Full source preparation is recorded separately from dependency
installation, compilation and live runtime tests.

### Actual local qualification, 2026-10-31

All three complete source repositories were fetched at the pinned revisions and
their root licenses verified. Dependencies were prepared in the external cache
with lifecycle scripts disabled. Bun 1.4.3 was downloaded from its official
release into that cache and its archive matched the published SHA-256; no global
runtime installation was made.

Claude-Mem's complete `npm run build` passed, including its viewers and plugin
bundles. The rebuilt real worker then passed local health/version, viewer HTML,
manual memory save/read, persistence across restart, and deletion checks. Its
temporary database was removed and its process stopped. This proves those local
storage operations, not model-generated observations or semantic search.

The repeatable macOS qualification helper requires an explicit source and Bun:

```sh
python3 companions/agents/qualify-memory.py \
  --source /absolute/external/companion-cache/claude-mem \
  --bun /absolute/external/companion-cache/runtime/bun-1.4.3/bun \
  --output /absolute/external/companion-cache/qualification
```

It uses a fresh data/config directory and a loopback-only macOS network sandbox,
disables capture/telemetry/cloud sync/provider fallback, and requests no model
generation. It does not install hooks or connect the user's existing history.

OpenBot's generated app configuration and complete frontend build passed
(`bun run generate:app-config`, `bun run --filter app build`). Its backend and
container deployment remain unqualified: Docker is absent and CopilotKit
Intelligence/model credentials were not configured. No accounts were fabricated
and no server was started.

OpenMausBot's client and server typechecks passed. Its frontend build transformed
5,655 modules and reached chunk rendering, then was stopped to finish the requested
low-credit checkpoint. Frontend completion, server emission/bundling and desktop
packaging remain incomplete; this is not a compile-error or success claim. Resume
the last three explicit commands above from the prepared external source cache.
No OpenMausBot account, server or computer-control runtime was started.

The focused Meshlit checks passed **14 Node tests and 6 Python tests**, including
actual SDK/MCP transport against local fixtures. Detailed build states, runtime
evidence and audit hashes are in
`docs/desktop/evidence/companion-integration-20261031.json`.

The desktop dashboard makes no Android runtime claim. Android can use separately
supported, owner-paired host capabilities; Electron, Bun, Docker and desktop
computer-control runtimes are not bundled into the APK by this integration.
