# Built-in Meshlit agent
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Updated 2026-10-23. This document distinguishes shipped code from design work.

## Current Android path

Chat uses the model loaded on the phone. When a conversation enables a tool group,
`LocalChatTools` exposes only that group's built-in MCP descriptors to
`LocalToolLoop`. The loop requests one JSON decision at a time and rechecks the
operation gate before every call. Android app control goes through
`AndroidControl`, which additionally checks saved autonomous delegation, Android
Accessibility binding, exact app scope, the foreground package and protected
surfaces. OpenClaw gateway settings are optional and do not participate in this
path. The phone agent has eight actions and a five-minute deadline; other local
tool conversations retain three calls and 180 seconds. Chat Stop and emergency
stop revoke future actions. Completed external actions cannot be rolled back.
Agent logs record task start, requested tool name, success/failure, completion and
cancellation. They omit prompts, tool arguments and tool results; opt-in encrypted
audit can also record this metadata. These logs are not a durable step checkpoint.

Saved local agent profiles can be applied in Chat conversation settings when
their assigned model is loaded. Applying one copies its instructions to the
conversation; tool groups and grants remain separate. The Agent management
**Run task** button remains a model-generation job and does not quietly inherit
Chat's tool access. These surfaces need a unified durable task model before
claiming desktop-style agent parity.

This is an experimental local agent, not a proven general-purpose autonomous
operator. The bundled Qwen2.5 0.5B model is intended for basic chat and was not
qualified for tool planning: in two Mac-side trials it answered instead of calling
a phone tool, then chose the wrong phone action. Android Chrome/YouTube task
completion remains a physical-device acceptance gate. More capable local models
can be imported without adding a host, subject to each device's RAM and thermal
capacity. A local model failure reports failure; it never switches to a cloud
model without the user selecting an online route.

## Open-source design references

Meshlit should use one native Kotlin runtime with adapters, not bundle whole
server frameworks into Android. These are design references; no source from them
is copied here:

| Project | Relevant pattern | Meshlit fit |
| --- | --- | --- |
| [MCP Kotlin SDK](https://github.com/modelcontextprotocol/kotlin-sdk) | Typed tools, transport-independent clients/servers | Interoperability layer for trusted local/remote tools; keep per-tool grants |
| [OpenAI Agents SDK](https://github.com/openai/openai-agents-python) | Tool guardrails and explicit handoffs | Adopt boundary checks as a design pattern; Python SDK is not the Android runtime |
| [LangGraph](https://github.com/langchain-ai/langgraph) | Checkpoints and human interrupts | Persist task checkpoints before later steps; never replay side effects blindly |
| [Google ADK Java](https://github.com/google/adk-java) | Java agent/tool abstractions | Evaluate a Studio/server adapter separately from the lightweight phone loop |

Before importing a dependency, pin a version, review its actual license and
transitive dependencies, and measure APK size, memory and startup impact. The
existing Meshlit MCP registry and Android permission gates remain the execution
authority. Third-party agents cannot gain OS access by registering a tool.

## Next acceptance work

The Android and Studio agent surfaces are still different. Android has local
inference, selected Chat tools, saved profile text jobs, typed backend permissions
and Accessibility scope. Studio has a local model and provider connections, but
its common agent authority, durable jobs, MCP/A2A hub, browser/PC control and
audit ledger are pending. The shared contract should be: model selection → task
plan → exact tool scope → step result → checkpoint → audit → pause/stop/revoke.
Implement the same task and permission record in both editions before presenting
their agent menus as equivalent. Model routing and layer sharding remain separate
from tool execution, and an online provider is never an implicit fallback.

1. Qualify a locally imported tool-capable model with a fixed plan suite on each
   supported memory tier; record real JSON validity, action choice, latency and
   thermal/RAM behavior.
2. Exercise Chrome open, snapshot, search, tap and Stop on Samsung and Xiaomi.
   Verify saved app scope, protected fields and revocation between steps.
3. Add durable task state and human review for costly or irreversible actions.
   A resumed run must never repeat a completed tap, payment or message.
4. Expose the same task and tool contract to Studio/server/container as optional
   adapters while preserving the on-device path when disconnected.
