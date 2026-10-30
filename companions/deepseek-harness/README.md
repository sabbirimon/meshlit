# DeepSeek Harness host integration
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-31: [Plan](../../PLAN.md) · [Progress](../../PROGRESS.md) · [Document status](../../docs/DOCUMENTATION_STATUS.md).
Scope: optional host/schema/reference; not a bundled qualified service. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Meshlit opens the **real upstream web workspace** inside Studio's integrated
Chromium browser and Android's in-app host view. This is optional and separate
from Meshlit's existing Antigravity agent host. No model key, permission grant,
usage budget or MCP configuration is silently transferred between them.

Reviewed source: [DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness/tree/f3803c96974ad4a94195fb97e5c637e9c2a3afe3),
MIT; `@deepseek-ai/dsh` **0.2.2-alpha.2**, Node **>=24.9**. Exact npm dependencies
and integrity values are recorded in `package-lock.json`. The upstream Python
SDK lists Linux x64/arm64, macOS 14+ arm64 and Windows x64; Android and Intel Mac
are not listed. The Node web host was separately checked on this Intel Mac.
The Android client does not bundle Node, Python, desktop executables or an SDK
that upstream has not qualified for Android.

## Install and start on a host

Install deliberately on your own workstation or server, with enough disk space
(the Intel Mac qualification installation occupied about 920 MiB). Keep the
installation outside the application package. Installation is never triggered
by opening a Meshlit screen or pasting a repository URL.

```sh
cd companions/deepseek-harness
npm ci --ignore-scripts --no-audit --no-fund
python3 launch.py --runtime . --home "$HOME/.meshlit-deepseek-harness" \
  --workspace /absolute/path/to/project
```

`launch.py` checks the installed top-level version, uses a dedicated 0700 home,
does not inherit provider keys or `NODE_OPTIONS`, and starts `dsh --profile web`
on literal loopback with `--no-open`. Initial permissions are read-only with
upstream approval prompts; `--allow-workspace-write` changes that initial mode.
The host still has its own permission UI. These controls are not an OS isolation
guarantee. Use a dedicated workspace and review the upstream safety notice.

The launcher applies a final policy overlay disabling the official-model session
log contribution, session OTel uploads and desktop product telemetry. It does
not erase local Harness history. It does not rewrite your saved provider settings.
No model is called at launch; choosing and sending to a provider is a separate
action. Account charges and extra model calls for compaction/subagents are owned
by Harness and the provider, outside Meshlit's current ledger.

The host prints a **private login URL**. Paste it into Meshlit's Harness
connection prompt; never put it in an issue, screenshot, command history or Git.
Upstream exchanges its `?token=` field for a signed browser cookie and redirects
to the clean root. The connector saves only that clean root in its settings;
embedded-browser history and diagnostics follow the browser’s own policy. The npm install is
developer-preview software; review [upstream safety](https://github.com/deepseek-ai/deepseek-harness/blob/f3803c96974ad4a94195fb97e5c637e9c2a3afe3/SAFETY.md).

## Open inside Meshlit

- **Studio:** DeepSeek Harness in the Meshlit rail, or `Meshlit: DeepSeek Harness`
  in the command palette. It opens as an integrated browser tab alongside your
  chats/editor. Uses isolated temporary browser sessions; closing its tab clears
  that tab's session. Missing browser support reports an error, with no external
  browser fallback.
- **Android:** Settings → DeepSeek Harness; also Tools and automation and the
  Code workspace's Harness button. Both flavors use the same screen. The
  workspace button overlays the host view so unsaved editor state stays mounted.
  The embedded view has no native JavaScript bridge, file access, downloads,
  camera/microphone grants or certificate bypass. It blocks navigation and HTTP
  subresources outside the approved origin. Browser cookies belong to Android
  WebView and can survive disconnect; forgetting the address is **not logout**.

For a phone, `127.0.0.1` refers to the **phone**, not your desktop. Use a host TLS
certificate trusted by the phone and matching its hostname/IP, or an already
configured loopback tunnel on the phone. The launcher refuses network HTTP:

```sh
python3 launch.py --runtime . --home "$HOME/.meshlit-deepseek-harness" \
  --workspace /absolute/path/to/project --host 192.168.1.20 \
  --tls-cert /private/server-chain.pem --tls-key /private/server-key.pem
```

No firewall, VPN, router or certificate trust changes are made for you. DNS or
reverse-proxy deployment also needs upstream's exact trusted-host and proxy
configuration; use its [deployment guide](https://github.com/deepseek-ai/deepseek-harness/blob/f3803c96974ad4a94195fb97e5c637e9c2a3afe3/docs/user/guide/public-deployments.md).
The helper intentionally supports direct IP listeners only.

Disconnecting the phone or closing a tab **does not stop server jobs**. Use
Harness's Stop first, or stop the host with Ctrl-C. Upstream has no logout
operation in the reviewed revision: revoking all browser sessions requires
removing its documented `client-connection/browser-session` credential record
and restarting the host. Do not delete the whole credentials file blindly.

## Models and tools

Inside Harness use **Settings → Models → Custom model API** for an existing
local/distributed OpenAI-compatible server, OmniRoute, FreeLLMAPI, OpenRouter or
another compatible vendor. Choose the endpoint's actual protocol and model ID.
Its catalog is the upstream catalog, not evidence that your account has free
credits. The provider URL is resolved from the **host**, not from the phone.
Meshlit's in-process GGUF engine is not exported into this host automatically.

Harness plugins, MCP connections, tool execution, conversation persistence and
concurrency remain upstream features. Their being visible is not Meshlit approval
to install a plugin or act on another paired device. A host operator must review
them separately. The [integration plan](../../docs/architecture/deepseek-harness.md)
describes the next shared model/permission contracts.

## Checks

```sh
python3 -m unittest discover -s companions/deepseek-harness -p 'test_*.py'
node --test companions/studio-runtime/extension/*harness*.test.cjs
python3 companions/deepseek-harness/qualify.py --runtime /path/to/install \
  --output /tmp/harness-evidence.json
```

Qualification uses a disposable empty workspace/home and the real installed
runtime. It checks effective privacy settings, 401 without authentication, real
HTML after token exchange, clean redirect/cookie continuation, and 403 for a
foreign API Origin. It makes **zero model requests**, suppresses private startup
URLs, stops its host and removes its temporary data. It does not claim physical
phone, TLS deployment, provider, agent execution or rendered UI qualification.
