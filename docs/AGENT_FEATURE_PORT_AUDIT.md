# Old Android agent feature audit · 2026-10-23
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Source reviewed: `/Users/code/AndroidStudioProjects/mllm/app/src/main/kotlin/com/meshlit/agent/AgentScreen.kt`, `AgentSession.kt`, `AgentExporter.kt`, `agent/hooks/HookEngine.kt`, and old MCP files. “Present” below means code exists in the new checkout; physical qualification is separate.

| Priority | Old behavior | New Meshlit status | Next acceptance |
| --- | --- | --- | --- |
| 1 | Agent chat and modes (Chat, Code, Plan) | Chat is separate; Agents has typed model jobs and newly saved named profiles, but no matching persistent mode conversation | Test local and approved online profile jobs on Samsung; decide whether modes should be prompt templates or separate conversations |
| 1 | Autopilot | New delegated tools and bounded job controls exist; old unrestricted surface is not copied | Exercise each grant, stop and revoke on two phones; require human review for device actions |
| 1 | Agent creation | New Android Agents screen saves up to 16 named, disabled-by-default profiles with a model and instruction; selected profiles can run typed jobs | Verify create, enable, execute, cancel, delete and restart persistence on device |
| 2 | Parallel agents | New screen submits 2–4 typed model jobs | Qualify concurrent loads, memory pressure and cancellation on both devices |
| 2 | Export transcript/code | Existing chat sharing and file actions cover some outputs; old AgentExporter UX is not ported as a dedicated agent export | Add explicit agent job transcript/code export after evidence and privacy review |
| 2 | MCP | Embedded registry and Android MCP tooling are present; external servers and grants vary by transport | Test an authorized server and blocked server with real results |
| 3 | Hooks | Existing hook screen/engine needs feature-by-feature comparison with old pre/post trigger behavior | Audit trigger lifecycle, script sandbox, audit trail and revocation |
| 3 | Studio/server/container agents | Studio has local host chat and device status; agent/MCP daemon parity is pending | Implement and test host agent lifecycle and secure tool policy before claiming parity |
| 3 | Model customization and retrieval | Pinned SSH host LoRA jobs exist; local content search can inform phone chat. The old vector RAG adapters are not durable or configured for real remote service | Build persisted local index first, then explicit provider-backed remote retrieval, source citation and per-route/cluster tests |

## Add a new agent

Open **Agents**, choose the currently loaded local model or an agent-approved online profile, enter a name and optional instruction, and save. The profile starts disabled. Enable it, enter a task and select **Run**. The separate MODELS delegation must also be enabled. Profiles contain no credentials or device/tool permissions. Disable/delete prevents future launches; use **Cancel active typed agent jobs** or Task manager to stop jobs already running.

## Model selection

The APK keeps the real 135M starter for installation checks. The app also has downloadable larger models, including Qwen2.5 1.5B and SmolLM2 1.7B. New model names should enter the curated one-tap catalog only after exact artifact URL, license, device memory, runtime compatibility, download, load and short generation checks. A newer GGUF on a model site alone is not proof of Android support.
