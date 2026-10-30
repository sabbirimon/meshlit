# Meshlit user manual
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Updated 2026-10-31. This manual covers the current source and build candidates.
Check the accompanying build evidence for what was actually tested on your platform.
The older Compose Studio, the newer Code OSS Studio, and Android share product
concepts but do not yet expose every feature in the same place.

## Choose the right application

| Application | Best use | Important boundary |
| --- | --- | --- |
| Studio workbench | Editor, independent chats, reviewed coding agents, project memory, voice-host tuning and optional computer companions | Optional services need their own installation and configuration. Check whether your package says development or portable candidate. |
| Earlier Compose Studio | Local model library, native engine controls, HyperL CPU work, device dashboards and existing paired-host workflows | These dashboards are not all migrated into the Code OSS workbench. |
| Android V1/V2 | Native local chat, models, voice, encrypted personal memory, approved tools, device pairing and supported host services | Desktop Docker/Electron/SDK environments do not run inside the APK. Both flavors use the shared modern settings. |

## Start a desktop conversation

1. Open Studio and use **Open Folder** for a local project. Project tools and
   project memory require a trusted local workspace.
2. Choose **Chat**, or **Coding agent** when working with files.
3. Select the bundled local model, or add a connection to a running compatible
   model server. A connection label does not establish model or GPU capability.
4. Unlock the Meshlit usage ledger when asked. Its passphrase is separate from
   your OS login and provider API key. Close the older Studio if it owns the vault.
5. Wait for the local model to report ready, then send a message. **New chat**
   creates an independent conversation; at most four are open concurrently.
6. Use **Stop** to stop that session. Open a new chat to continue after Stop.
   Reviewed edits remain unsaved in the editor until you save them yourself.

Chat history in the SDK session is transient. Saving a project note does not save
or restore the entire conversation. Changing model or mode after a session starts
opens a new conversation so old permissions/history are not silently transferred.

## Understand modes and tools

| Mode | What it can do |
| --- | --- |
| Chat | Conversation without workspace tools |
| Plan | Read approved workspace information; no file edits |
| Edit / Build | Propose one reviewed file edit per turn; selected MCP tools require review |
| Autopilot | Multiple bounded steps with the same tool approvals and edit review |

Use the **+** menu → **Choose a tool-enabled mode**. For MCP, add a connector in
Settings → Memory and agents → MCP connectors, then use **Select MCP tools for
this chat** in Edit/Autopilot. Adding a connector does not enable it for every chat.
Changing tools after a session starts opens a new chat. Cancel leaves the existing
selection alone; selecting no connectors deliberately clears it.

If a tool cannot be enabled, check the selected mode, workspace trust, saved
connector and OS/provider permissions. Approval does not make an unavailable
backend work. macOS app access also needs the dedicated helper, OS Accessibility
permission and an app allowlist. **Stop app access** revokes future/ongoing access;
it does not reverse already completed external changes.

## Find settings without guessing

Studio's **Settings** is a Meshlit hub with searchable, grouped destinations:
Models and voice; Memory and agents; Services and integrations; Workspace and
appearance. **Editor settings** separately opens Code OSS fonts, themes and editor
preferences. Search includes common terms such as tokens, permissions and speech.
Vendor dashboards remain available under Services instead of filling the sidebar.

Android Settings offers Basic and Advanced views. Search spans the complete
available catalog; changing the view does not reset settings. Advanced groups
related destinations by purpose. Build-profile restrictions still apply. Quick
access is separate from authorization: pinning a destination grants no permission.

For a complete route inventory and verification boundaries, see
[Android settings audit](ANDROID_SETTINGS_AUDIT.md), the existing
[full feature guide](USER_GUIDE.md), and the build's settings audit evidence.
A source-reviewed setting is not automatically device-tested.

## Models, providers and costs

Keep local inference as the private default. To use online inference, explicitly
select a provider connection and review the destination. Save API keys only in the
application's credential controls; never in a URL, project note or chat message.

Studio's compatible routes use a provider's OpenAI-compatible API. Android also
has its existing native provider adapters. OpenAI, Google, Anthropic, Mistral,
DeepSeek and Meta model names are not interchangeable protocols. A Meta Llama
model can run through a compatible installed local runtime or provider only when
its exact format, architecture, license and device requirements are met. Use the
model IDs returned by the configured service or a verified provider ID.

**Check a model connection** reads its model catalog; it does not generate text or
prove available credit. Public model listings and a service named FreeLLMAPI do
not guarantee free hosted use. Unknown usage/cost remains unknown. Meshlit's
recorded budgets are not a provider billing cap. There is no automatic paid fallback.
Your ChatGPT subscription balance is not available through this application.

## Protected project memory on desktop

Open **Project memory** from the sidebar. Select a workspace, enter a title and
note, and save. Notes are manually entered; Meshlit does not capture every prompt,
screen or source file. The encrypted store uses a key in editor SecretStorage.
If that key cannot be opened, the existing data is preserved and no plaintext
fallback is used. Preserve the profile and OS secret storage together when planning
backup; copying ciphertext alone does not create a portable backup.

Each project has separate permissions, all initially off:

- Include matching notes with the bundled local model.
- Include matching notes with online or local-host APIs.
- Keep reviewed action metadata.

There are at most 100 notes, 4,000 characters per note and 200,000 total characters
per project. Search uses bounded word matching, not semantic embeddings. Recall
includes up to three excerpts and may omit them when the request has no remaining
character budget. **Context preview** shows matching content and its destination;
character counts are not token counts. Notes are reference material, not authority
to grant tools or change permissions.

**Forget** removes a note from future recall. Turning recall off also affects future
requests, but neither operation erases text already sent to a model. Open a new
chat to exclude prior recalled context. Existing provider retention is governed
by the selected provider. Android personal memory is a separate existing encrypted
fact store; automatic synchronization with Studio is not implemented.

## Reviewed action history

Enable history in Project memory → Project permissions. The latest 200 events
retain time, tool name, mode and outcome. Raw prompts, arguments, credentials and
file paths are not stored in receipts. **Approved** means permission was granted;
for MCP it does not prove the remote tool completed. **Complete**, **failed** and
**denied** describe observed local outcomes. Clearing history does not undo actions.
No saved action is automatically replayed after restart.

## claude-mem, OpenMausBot and OpenBot

Open **Computer companions**, or Settings → Services → Memory and computer
companions. Configure only your installed local service. Saving an endpoint starts
nothing. **Check** makes a bounded local health request; **Open** explicitly opens
the configured upstream interface.

- **claude-mem:** its worker provides the viewer and persistent observation tools.
  Use the actual worker port from your installation; it can depend on your OS UID.
  The worker and HTTP MCP server are separate endpoints. Attach the installed MCP
  endpoint, then select it for an agent. Do not enable cloud observers or sharing
  accidentally while following upstream setup.
- **OpenMausBot:** use the full upstream interface for bots, channels, computer
  environments and approvals. Its installed MCP command can expose supported
  tools with a paired token bound to its service port. Tokens belong in protected
  connector setup. MCP cannot grant approvals or manage credentials/computers for you.
- **OpenBot:** its complete UI exposes the self-hosted agent-computer workflow.
  Bun, Docker/Postgres, configured models and CopilotKit configuration are upstream
  prerequisites. A generic health response only proves an endpoint responded.
  AG-UI is a distinct protocol; Meshlit does not pretend it is an MCP endpoint.

These integrations preserve access to the complete upstream applications, while
Meshlit's own controls implement only the listed connection/health/MCP operations.
The full applications are not bundled into Android. See
[companion setup, pinned source preparation and limits](DESKTOP_COMPANIONS.md).

## Voice and live conversation

On Android choose a speech input and output before starting. Native Android TTS
and installed offline Whisper/Piper are separate from host-provided Kokoro/Pocket
or online speech. Microphone permission, a usable chat model, and the chosen speech
engines must all be ready. Keep the application visible during live voice use.

Use the live view to distinguish listening, transcribing, thinking and speaking.
Mute stops listening; End stops the session. Output interruption and silence
detection are bounded features, not a guarantee of human-like full-duplex speech.
If the app starts but hears nothing, check permission, input device, the measured
microphone level and detection sensitivity. An input timeout must not be read as
successful transcription. If a transcript appears but no reply does, inspect the
selected model and generation error; if text arrives silently, check speech output.

Kokoro/Pocket currently synthesize on the configured host; the phone receives PCM
for playback. Host speech does not work while that host service is stopped.
ElevenLabs needs your own account key. Free-tier-only checks default on and reject
unknown/paid/exhausted accounts, but they are not atomic provider quota reservations.
Pocket's selected export has additional model-use terms. Detailed recipes and
the exact tested boundaries are in [Natural voices](NATURAL_VOICES.md) and
[Voice and vision](VOICE_AND_VISION.md).

Studio's Voice and vision page tunes the optional OpenLive host and plays real
speech previews. It is distinct from Android's microphone-to-model live session.
Do not infer working camera vision or continuous conversation from a TTS preview.

## Core engine, HyperL and different devices

The native llama.cpp/SDK path executes supported language models. HyperL performs
its own numerical CPU operations through the available adapters. One working
HyperL operation does not establish LLM support, distributed inference or a GPU
driver. C++ is already used in these native compute paths; new language choices
must preserve cancellation, admission and measurable benefits.

Check actual OS/ABI, installed backend, model format/hash, memory headroom,
permissions and device offer before assigning work. Device category alone does
not grant a capability. Unsupported GPU/NPU/vendor backends should report why they
are unavailable. Peer pairing, remote chat and multi-device layer execution are
different features; the latter needs explicit matching native evidence.

## Recovery and troubleshooting

| Symptom | Check or next action |
| --- | --- |
| Settings destination opens but service is unavailable | Read the prerequisite; saved configuration is not an installation. Check the specific service, not just network connectivity. |
| Local model will not load | Verify artifact integrity, format, memory and installed backend. Keep the error; do not infer online fallback. |
| Chat send is disabled | Wait for host/local-model readiness and unlock Usage. Keep your draft. |
| Chat tools cannot be enabled | Use a supported local route/mode, inspect build restrictions and tool-count limit, then enable the separate saved grants. |
| Project memory cannot open | Unlock OS secret storage; preserve the encrypted file and key. Do not reset/delete them to hide the error. |
| Companion health fails | Confirm the actual local port and startup state. Redirects and non-loopback endpoints are rejected by this integration. |
| MCP tool denied | Review agent selection, explicit tool approval, connector credentials and service pairing. |
| Voice never hears speech | Check actual input level and permission, choose the correct input, adjust sensitivity, and inspect the timeout/engine error. |
| HyperL target unavailable | Use a qualified installed adapter. A target label does not install its driver. |

When reporting a bug, include app/build, OS/device, destination/model, exact action,
visible error and whether it reproduces after reopening. Exclude API keys,
passphrases and private conversation contents. Keep source tests, installed-app
tests and physical-device results distinct.

Developer repair handoff: [where to look, why, how to fix and verify—including Android](SMALL_MODEL_MAINTENANCE_GUIDE.md).
