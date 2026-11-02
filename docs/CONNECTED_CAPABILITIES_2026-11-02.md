# Connected capabilities — active source build
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-11-03: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

The owner authorizes adapting compatible upstream features, combining overlapping integrations, removing unsuitable features/settings and improving Android and Studio usability. Existing data, model files, identities, grants and saved settings remain intact. This is an active working-tree increment; compilation/protocol fixtures do not qualify live external services.

## One workflow, distinct capabilities

| Capability | Meshlit path | Upstream role | Current acceptance boundary |
| --- | --- | --- | --- |
| Roles | Role library → enabled saved templates → team member selection | Agency Agents | 268 bounded editable excerpts, MIT attribution, pinned source paths/hashes. No imported grants, installers or tools. |
| Team coordination | User directed or supervised autonomous coordinator | Meshlit shared Android/desktop implementation | Fixed members, selected models/targets, strict manager JSON, bounded rounds/concurrency/deadline, attributed outputs, pause/Stop and owner-only delegation. Text analysis; arbitrary tool work is outside this coordinator. |
| Knowledge and memory | Built-in encrypted knowledge/project memory plus optional connected retrieval | OpenViking; existing Claude-Mem connector | OpenViking namespace-scoped find and L0/L1/L2 reads. Per-route recall and agent switches default off. No automatic chat capture, session commit, memory mutation, skill execution or cloud sync. Claude-Mem retains its own MCP approval path. |
| Phone actions | Native Android Accessibility or an explicitly configured device host | Google Artemis | HTTP status, actual device list, reviewed task admission, status and Stop. App lock is an upstream constraint, not proof of Meshlit per-action enforcement. Direct agent Artemis execution remains unavailable pending that qualification. |
| Inference | Existing local/remote model provider routes | Strata | OpenAI-compatible host selection/catalog/generation path. No Strata engine, weights, CUDA/HIP/Metal backend or installer is copied into the phone. Backend-specific memory/offload and speed need actual supported hardware. |
| Computer companions | Existing companion dashboard and scoped MCP connectors | OpenMausBot, OpenBot, Claude-Mem | Optional installed services. Existing paired tokens, per-tool review and upstream lifecycle remain authoritative. |

The canonical review manifest is `shared-workspace/src/jvmMain/resources/integrations/catalog.json`. Runtime readiness comes from observed responses, not this catalog. Agency role data uses schema 2: the unused full source bodies were removed, reducing the canonical JSON from about 4.1 MiB to 414 KiB while preserving all 268 searchable roles, bounded excerpts and attribution.

## Data and control boundaries

- OpenViking's current main server is AGPLv3. Meshlit uses independently written API clients; no server implementation is copied, translated or relabeled. An owner configures any optional service separately. Its embedding/VLM configuration may use third-party providers or incur charges.
- Both API adapters require HTTPS for remote hosts, authentication outside literal loopback, no URL credentials, no redirects, bounded JSON, a request deadline and cancellation. Keys stay endpoint-bound in protected storage. HTTP loopback on a phone means that phone, not the owner's Mac.
- Context results carry backend, namespace URI, kind, layer and observation time. Local lexical scores and remote vector scores are not compared as equivalent quantities. Retrieved memory, skills and role guidance remain untrusted reference text.
- Android team model calls can use the same granted knowledge path. Studio ordinary chat/coding-agent recall composes the existing encrypted project memory with separately approved connected memory within its character budget. Studio team calls currently use the explicit task, selected role guidance and peer findings; automatic project-memory recall for backend-created teams is not qualified.
- Artemis task UUID and host-bound admission are persisted before POST. An uncertain submission is retained without automatic retry. Server status is an observation, not independent proof of task completion. Remote Stop is confirmed only by its acknowledgment; missing acknowledgment remains visible. Android active-task monitoring requests Stop after timeout, revocation or Operations cancellation. Studio also observes admitted tasks for at most ten minutes and requests Stop on timeout, connection/workspace revocation or observation failure; restart requests cleanup of previous nonterminal admissions without replaying them. A delayed status reply cannot resurrect a confirmed terminal task. Process-exit cleanup is best effort, so the host remains authoritative. Restart does not replay task submission.
- Artemis screenshots/app content go to its configured host/model according to the reviewed task. Installing Artemis, configuring a model/provider, ADB authorization, accessibility grants and global IDE rules are separate owner-controlled steps. No upstream startup/install scripts or global rules were executed by this integration.

## Removed from the current product flow

- The acoustic GibberLink agent modem is no longer registered as an agent tool or offered in conversation controls/navigation. Its legacy serialized flag remains readable for old chats and has no effect on tool exposure or route selection. The library and historical data remain in source.
- Rewards-wallet, lab-package and security-lab launcher routes are retired from settings and workspace navigation. Signed historical receipts are preserved; no balances or histories are deleted.
- Legacy RAG backend/agent-loop selectors were removed from the knowledge screen. That screen now presents the actual knowledge library, retrieval grants and a connected-memory entry point.
- Duplicate full upstream role bodies and the blocking model-loading gate were removed. Legal acceptance still precedes app-owned runtime startup. Model readiness remains visible in Chat and Models.

## Validation and open work

Earlier shared team/runtime tests, both Android unit suites, desktop tests, Node extension tests and packaged-Python SDK tests passed before subsequent integration/UX edits. New HTTP fixtures cover exact namespace/device/task binding, returned source metadata, bounded responses, redirect refusal and Stop acknowledgment. The final combined builds/lints, current Samsung update and scoped UI navigation checks passed. The signed portable Studio candidate passed runtime checks and real three-role local inference at contexts 1024/4096, including a cold default-context start. Desktop GUI/accessibility, sustained-load behavior, actual autonomous-manager model quality and real cross-device teams remain unqualified.

No live OpenViking, Artemis or Strata service is configured or qualified on this machine/phone. No cloud key, embedding model, VLM or large model was downloaded for these optional adapters. Live outside-LAN pairing remains deferred at the owner's request. Genuine distributed tensor memory, backend GPU pooling and autonomous fleet configuration remain separate unfinished compute-runtime workstreams; these integrations do not deliver them by themselves.

## Reviewed primary sources

- [Agency Agents](https://github.com/msitarzewski/agency-agents/tree/b37b5bb33b78f2c1d6567d98e25ca818e3259219), MIT license retained with bundled data.
- [OpenViking retrieval API](https://github.com/volcengine/OpenViking/blob/9925e819e52ff2c589da9ae1bbac16cd8d9a00bc/docs/en/api/06-retrieval.md), [content API](https://github.com/volcengine/OpenViking/blob/9925e819e52ff2c589da9ae1bbac16cd8d9a00bc/docs/en/api/12-content.md), [server license](https://github.com/volcengine/OpenViking/blob/9925e819e52ff2c589da9ae1bbac16cd8d9a00bc/LICENSE).
- [Google Artemis client contract](https://github.com/google/artemis/blob/351ca8422f7b5b54e80a9c1ce03a222e02415b6b/packages/artemis-client/src/artemis_client/client.py), [license](https://github.com/google/artemis/blob/351ca8422f7b5b54e80a9c1ce03a222e02415b6b/LICENSE).
- [Strata architecture and component licenses](https://github.com/Niko1221/Strata/blob/c62d5e3a15b40ea51c96aae9e8d36928965f53ab/docs/HOW_IT_WORKS.md), [API and hardware reference](https://github.com/Niko1221/Strata/blob/c62d5e3a15b40ea51c96aae9e8d36928965f53ab/docs/AI_REFERENCE.md).
