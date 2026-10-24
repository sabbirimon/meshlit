# Markdown documentation status and workstream tracking

Reconciled 2026-10-12. This index covers every tracked first-party, historical, protected and third-party Markdown file. The index itself is generated; counts below exclude this index to avoid self-hashing.

[Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Milestone gates](../BUILD_MILESTONES.md) · [Desktop feature tracker](DESKTOP_FEATURE_TRACKER.md) · [Release evidence](RELEASE_EVIDENCE.md)

## Workstream phases

Phase names are independent workstreams, not a unified numbered stage or an overall completion percentage. Source, contracts, host/device qualification, release and production admission are separate states. Historical phase numbers remain dated design labels.

| Workstream | Current qualification | Next gate |
| --- | --- | --- |
| Local core engine | Host/payload verified; Experimental | Clean-machine, lifecycle/stress, wider quality and hardware |
| Shared sharding | Contract-tested planner; desktop executor pending | Live paired-worker controller and independent-device oversized-model/peer-loss proof |
| Desktop management | Partial source and scoped host qualification | Remaining real category controllers and operational acceptance |
| Android reliability | Build44 evidence retained; build45 source saved, final build/lint/device qualification incomplete | Owner resumes work, then changed contracts, both flavors/lints and Samsung voice/RAG/native checks |
| Governed agents and fleet | Android reference; desktop authority/audit port pending | Common permission/controller/audit bridge before autonomous MCP/A2A/hooks |
| Platform and distribution | Intel Experimental release published; recovered payloads/hash verification pass | Clean-target signing/privacy and independent OS/device gates; CI remains separately observed |
| Android/desktop peering, identity, wallet and UI | Partial source saved for PR #4; application work paused for HyperL-first delivery | Desktop and Android compile/integration review, strict lint and qualified peer/recovery tests |

## Coverage and maintenance

133 active, 19 historical, 8 protected, 1 template, 6 third-party; 168 Markdown documents including this index.

Active files receive current tracking links. Historical records retain exact dated claims; protected licences/attribution/versioned policies, third-party texts and PR templates are indexed without content edits. Runtime evidence is read from the named ledger, never inferred from a document date or checksum.

Run `python3 scripts/update-doc-tracking.py --write` after updating plans, then `--check`. The JSON manifest records normalized content hashes and classification; it is not a runtime certificate. Feature/module references are separately checked by scripts/validate-feature-map.py.

## Active documents

| Document | Scope |
| --- | --- |
| [.claude/skills/build.md](../.claude/skills/build.md) | workflow guidance; current owner instructions and scoped evidence govern |
| [.claude/skills/cloud-mcp.md](../.claude/skills/cloud-mcp.md) | workflow guidance; current owner instructions and scoped evidence govern |
| [.claude/skills/journal.md](../.claude/skills/journal.md) | workflow guidance; current owner instructions and scoped evidence govern |
| [.claude/skills/release.md](../.claude/skills/release.md) | workflow guidance; current owner instructions and scoped evidence govern |
| [.claude/skills/test.md](../.claude/skills/test.md) | workflow guidance; current owner instructions and scoped evidence govern |
| [AGENTS.md](../AGENTS.md) | workflow guidance; current owner instructions and scoped evidence govern |
| [AGENT_BUILD.md](../AGENT_BUILD.md) | workflow guidance; current owner instructions and scoped evidence govern |
| [BUGS.md](../BUGS.md) | cross-platform tracking; feature and device gates remain separate |
| [BUILD.md](../BUILD.md) | cross-platform tracking; feature and device gates remain separate |
| [BUILD_MILESTONES.md](../BUILD_MILESTONES.md) | cross-platform tracking; feature and device gates remain separate |
| [CLAUDE.md](../CLAUDE.md) | workflow guidance; current owner instructions and scoped evidence govern |
| [CONTRIBUTING.md](../CONTRIBUTING.md) | Android/shared reference; desktop ports have separate acceptance |
| [FEATURE_MAP.md](../FEATURE_MAP.md) | cross-platform tracking; feature and device gates remain separate |
| [HYPERL.md](../HYPERL.md) | Android/shared reference; desktop ports have separate acceptance |
| [PLAN.md](../PLAN.md) | cross-platform tracking; feature and device gates remain separate |
| [PROGRESS.md](../PROGRESS.md) | cross-platform tracking; feature and device gates remain separate |
| [README.md](../README.md) | cross-platform tracking; feature and device gates remain separate |
| [REQUESTS.md](../REQUESTS.md) | cross-platform tracking; feature and device gates remain separate |
| [SECURITY.md](../SECURITY.md) | Android/shared reference; desktop ports have separate acceptance |
| [TODO.md](../TODO.md) | cross-platform tracking; feature and device gates remain separate |
| [app/BUILD_GUIDE.md](../app/BUILD_GUIDE.md) | Android/shared reference; desktop ports have separate acceptance |
| [app/CLAUDE.md](../app/CLAUDE.md) | workflow guidance; current owner instructions and scoped evidence govern |
| [app/SKILL-1.md](../app/SKILL-1.md) | workflow guidance; current owner instructions and scoped evidence govern |
| [app/SKILL-2.md](../app/SKILL-2.md) | workflow guidance; current owner instructions and scoped evidence govern |
| [app/SKILL-3.md](../app/SKILL-3.md) | workflow guidance; current owner instructions and scoped evidence govern |
| [app/SKILL-4.md](../app/SKILL-4.md) | workflow guidance; current owner instructions and scoped evidence govern |
| [app/SKILL.md](../app/SKILL.md) | workflow guidance; current owner instructions and scoped evidence govern |
| [app/src/main/assets/models/README.md](../app/src/main/assets/models/README.md) | Android/shared reference; desktop ports have separate acceptance |
| [companions/agentgateway/README.md](../companions/agentgateway/README.md) | optional host/schema/reference; not a bundled qualified service |
| [companions/colibri/README.md](../companions/colibri/README.md) | optional host/schema/reference; not a bundled qualified service |
| [companions/crawler/README.md](../companions/crawler/README.md) | optional host/schema/reference; not a bundled qualified service |
| [companions/cyber/README.md](../companions/cyber/README.md) | optional host/schema/reference; not a bundled qualified service |
| [companions/deployment/README.md](../companions/deployment/README.md) | optional host/schema/reference; not a bundled qualified service |
| [companions/node/README.md](../companions/node/README.md) | optional host/schema/reference; not a bundled qualified service |
| [companions/pipeline/README.md](../companions/pipeline/README.md) | optional host/schema/reference; not a bundled qualified service |
| [companions/training/README.md](../companions/training/README.md) | optional host/schema/reference; not a bundled qualified service |
| [configuration/README.md](../configuration/README.md) | optional host/schema/reference; not a bundled qualified service |
| [core-federation/src/main/kotlin/com/meshlit/core/federation/README.md](../core-federation/src/main/kotlin/com/meshlit/core/federation/README.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/AGENT_FEATURE_PORT_AUDIT.md](AGENT_FEATURE_PORT_AUDIT.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/AGENT_GATEWAY_AND_SECURITY_LAB.md](AGENT_GATEWAY_AND_SECURITY_LAB.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/AI_REPAIR_AND_EVOLUTION.md](AI_REPAIR_AND_EVOLUTION.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/ANDROID_DESKTOP_PARITY.md](ANDROID_DESKTOP_PARITY.md) | desktop/server; individual acceptance gates apply |
| [docs/ANDROID_SECURITY_TOOL_INTEGRATIONS.md](ANDROID_SECURITY_TOOL_INTEGRATIONS.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/AUDIT_TELEMETRY.md](AUDIT_TELEMETRY.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/BLUE_TEAM_AND_PACKET_ANALYSIS.md](BLUE_TEAM_AND_PACKET_ANALYSIS.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/BUILT_IN_AGENT_DESIGN.md](BUILT_IN_AGENT_DESIGN.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/CHAT_PRESENTATION.md](CHAT_PRESENTATION.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/CLIENT_HUB.md](CLIENT_HUB.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/COLIBRI.md](COLIBRI.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/CONTINUATION_2026_10_08.md](CONTINUATION_2026_10_08.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/DESKTOP_BUILD_LOG.md](DESKTOP_BUILD_LOG.md) | desktop/server; individual acceptance gates apply |
| [docs/DESKTOP_FEATURE_TRACKER.md](DESKTOP_FEATURE_TRACKER.md) | desktop/server; individual acceptance gates apply |
| [docs/DESKTOP_STUDIO_PLAN.md](DESKTOP_STUDIO_PLAN.md) | desktop/server; individual acceptance gates apply |
| [docs/DEVICE_COMPATIBILITY.md](DEVICE_COMPATIBILITY.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/DISCOVERY_AND_COMMUNITY.md](DISCOVERY_AND_COMMUNITY.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/GIBBERLINK.md](GIBBERLINK.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/HIGH_IMPACT_BACKLOG_2026-10-24.md](HIGH_IMPACT_BACKLOG_2026-10-24.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/LINUX_DISTRIBUTIONS_AND_LAB_TOOLS.md](LINUX_DISTRIBUTIONS_AND_LAB_TOOLS.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/LOCAL_MODEL_TOOLS.md](LOCAL_MODEL_TOOLS.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/MACOS_OFFLINE.md](MACOS_OFFLINE.md) | desktop/server; individual acceptance gates apply |
| [docs/MEMORY_AND_VOICE.md](MEMORY_AND_VOICE.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/MULTIPLATFORM_NEXT.md](MULTIPLATFORM_NEXT.md) | platform preview; NEXT device/build acceptance remains open |
| [docs/NEMOTRON_AND_HETEROGENEOUS_SHARDING_2026-10-23.md](NEMOTRON_AND_HETEROGENEOUS_SHARDING_2026-10-23.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/OLD_ANDROID_GAP_AUDIT_2026-10-23.md](OLD_ANDROID_GAP_AUDIT_2026-10-23.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/OLD_ANDROID_STUDIO_REQUEST_AUDIT.md](OLD_ANDROID_STUDIO_REQUEST_AUDIT.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/P2P_AND_CRYPTO.md](P2P_AND_CRYPTO.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/PAUSED_CHECKPOINT_2026-10-24.md](PAUSED_CHECKPOINT_2026-10-24.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/PEERING_UI_BUILD_PLAN.md](PEERING_UI_BUILD_PLAN.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/PEERING_UI_HANDOFF_2026-10-12.md](PEERING_UI_HANDOFF_2026-10-12.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/PENDING_FEATURES.md](PENDING_FEATURES.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/PLATFORM_ADAPTER_PLAN.md](PLATFORM_ADAPTER_PLAN.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/PLAY_DISTRIBUTION.md](PLAY_DISTRIBUTION.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/REDESIGN_PLAN_2026-10-23.md](REDESIGN_PLAN_2026-10-23.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/RELEASE_EVIDENCE.md](RELEASE_EVIDENCE.md) | cross-platform tracking; feature and device gates remain separate |
| [docs/REWARD_METERING_AND_SETTLEMENT.md](REWARD_METERING_AND_SETTLEMENT.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/SEARCH_AND_CLUSTER_OUTPUT.md](SEARCH_AND_CLUSTER_OUTPUT.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/SECURITY_LAB_AND_HARDWARE_ACCESS.md](SECURITY_LAB_AND_HARDWARE_ACCESS.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/SESSION_HANDOFF.md](SESSION_HANDOFF.md) | cross-platform tracking; feature and device gates remain separate |
| [docs/SSH_NODE.md](SSH_NODE.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/STITCH_GLASS_UI.md](STITCH_GLASS_UI.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/UI_AUDIT.md](UI_AUDIT.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/UI_REFERENCE_REDESIGN.md](UI_REFERENCE_REDESIGN.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/UI_WORKSPACE_REDESIGN.md](UI_WORKSPACE_REDESIGN.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/USER_GUIDE.md](USER_GUIDE.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/architecture/PRODUCTION_CHANNELS.md](architecture/PRODUCTION_CHANNELS.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/architecture/README.md](architecture/README.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/architecture/current-state.md](architecture/current-state.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/architecture/federation.md](architecture/federation.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/architecture/hooks.md](architecture/hooks.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/cloud-and-credential-management.md](cloud-and-credential-management.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/code-workspace-and-permissions.md](code-workspace-and-permissions.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/declarative-federation-roadmap.md](declarative-federation-roadmap.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/desktop/DOLPHIN.md](desktop/DOLPHIN.md) | desktop/server; individual acceptance gates apply |
| [docs/desktop/ENGINE_AND_SHARDING.md](desktop/ENGINE_AND_SHARDING.md) | desktop/server; individual acceptance gates apply |
| [docs/desktop/RECOVERY.md](desktop/RECOVERY.md) | desktop/server; individual acceptance gates apply |
| [docs/desktop/SEARCH_AND_MANAGEMENT.md](desktop/SEARCH_AND_MANAGEMENT.md) | desktop/server; individual acceptance gates apply |
| [docs/desktop/SECURITY_AND_RUNTIME.md](desktop/SECURITY_AND_RUNTIME.md) | desktop/server; individual acceptance gates apply |
| [docs/desktop/STABILIZATION_BUILD_GUIDE.md](desktop/STABILIZATION_BUILD_GUIDE.md) | desktop/server; individual acceptance gates apply |
| [docs/desktop/STABILIZATION_NEXT.md](desktop/STABILIZATION_NEXT.md) | desktop/server; individual acceptance gates apply |
| [docs/desktop/STABILIZATION_PLAN.md](desktop/STABILIZATION_PLAN.md) | desktop/server; individual acceptance gates apply |
| [docs/desktop/TERMINAL.md](desktop/TERMINAL.md) | desktop/server; individual acceptance gates apply |
| [docs/device-aware-model-runtime.md](device-aware-model-runtime.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/hugging-face-integration.md](hugging-face-integration.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/hyperl/ANDROID_ALPHA6.md](hyperl/ANDROID_ALPHA6.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/hyperl/APP_AND_LIBRARY.md](hyperl/APP_AND_LIBRARY.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/hyperl/LANGUAGE_V1.md](hyperl/LANGUAGE_V1.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/hyperl/REQUESTS_AND_PLAN.md](hyperl/REQUESTS_AND_PLAN.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/hyperl/RESEARCH_2026_10_08.md](hyperl/RESEARCH_2026_10_08.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/improvement-and-stryker-plan.md](improvement-and-stryker-plan.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/layer-pipeline-and-recovery.md](layer-pipeline-and-recovery.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/lint-findings.md](lint-findings.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/local-model-behavior-and-training.md](local-model-behavior-and-training.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/mcp/bring-your-own-server.md](mcp/bring-your-own-server.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/media-iot-and-radio-nodes.md](media-iot-and-radio-nodes.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/model-router-and-media.md](model-router-and-media.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/models/catalog-classification.md](models/catalog-classification.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/native-checkpoints.md](native-checkpoints.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/old-android-reuse-review.md](old-android-reuse-review.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/online-power-peripherals-and-configuration.md](online-power-peripherals-and-configuration.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/openclaw-integration.md](openclaw-integration.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/physical-device-test-guide.md](physical-device-test-guide.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/platform-and-backend-contract.md](platform-and-backend-contract.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/release-checklist.md](release-checklist.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/remaining-engine-bugs.md](remaining-engine-bugs.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/runanywhere-browser-and-llama.md](runanywhere-browser-and-llama.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/runtime-and-sandbox.md](runtime-and-sandbox.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/task-manager.md](task-manager.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/typed-agent-backend.md](typed-agent-backend.md) | Android/shared reference; desktop ports have separate acceptance |
| [docs/web-api-and-devices.md](web-api-and-devices.md) | Android/shared reference; desktop ports have separate acceptance |
| [examples/training/README.md](../examples/training/README.md) | optional host/schema/reference; not a bundled qualified service |
| [ohosApp/README.md](../ohosApp/README.md) | platform preview; NEXT device/build acceptance remains open |
| [tests/e2e/README.md](../tests/e2e/README.md) | Android/shared reference; desktop ports have separate acceptance |
| [vendored/README.md](../vendored/README.md) | optional host/schema/reference; not a bundled qualified service |

## Historical documents

| Document | Scope |
| --- | --- |
| [docs/DEVICE_TESTING_2026-10-08.md](DEVICE_TESTING_2026-10-08.md) | dated design/evidence snapshot |
| [docs/ODYSSEUS_REVIEW_2026_10_08.md](ODYSSEUS_REVIEW_2026_10_08.md) | dated design/evidence snapshot |
| [docs/PHASE1_VALIDATION.md](PHASE1_VALIDATION.md) | dated design/evidence snapshot |
| [docs/VALIDATION_BUILD_38.md](VALIDATION_BUILD_38.md) | dated design/evidence snapshot |
| [docs/VALIDATION_BUILD_40.md](VALIDATION_BUILD_40.md) | dated design/evidence snapshot |
| [docs/architecture/EVALUATION_REVIEW_2026_10_09.md](architecture/EVALUATION_REVIEW_2026_10_09.md) | dated design/evidence snapshot |
| [docs/architecture/dirty-tree-inventory-redesign-drawer-gemini.md](architecture/dirty-tree-inventory-redesign-drawer-gemini.md) | dated design/evidence snapshot |
| [docs/decisions/0001-user-driven-choices.md](decisions/0001-user-driven-choices.md) | dated design/evidence snapshot |
| [docs/history/BUGS-before-studio-2026-10-10.md](history/BUGS-before-studio-2026-10-10.md) | dated design/evidence snapshot |
| [docs/history/PLAN-before-2026-10-06.md](history/PLAN-before-2026-10-06.md) | dated design/evidence snapshot |
| [docs/history/PLAN-before-studio-2026-10-10.md](history/PLAN-before-studio-2026-10-10.md) | dated design/evidence snapshot |
| [docs/history/PROGRESS-before-2026-10-06.md](history/PROGRESS-before-2026-10-06.md) | dated design/evidence snapshot |
| [docs/history/REQUESTS-before-studio-2026-10-10.md](history/REQUESTS-before-studio-2026-10-10.md) | dated design/evidence snapshot |
| [docs/history/SESSION_HANDOFF-2026-10-07.md](history/SESSION_HANDOFF-2026-10-07.md) | dated design/evidence snapshot |
| [docs/history/TODO-before-studio-2026-10-10.md](history/TODO-before-studio-2026-10-10.md) | dated design/evidence snapshot |
| [docs/history/bring-your-own-server-before-2026-10-06.md](history/bring-your-own-server-before-2026-10-06.md) | dated design/evidence snapshot |
| [docs/history/current-state-upstream-baseline.md](history/current-state-upstream-baseline.md) | dated design/evidence snapshot |
| [docs/hyperl/VALIDATION_2026_10_08.md](hyperl/VALIDATION_2026_10_08.md) | dated design/evidence snapshot |
| [docs/journal/2026-08-01-phase-0-planning.md](journal/2026-08-01-phase-0-planning.md) | dated design/evidence snapshot |

## Protected documents

| Document | Scope |
| --- | --- |
| [AUTHORS.md](../AUTHORS.md) | licence, attribution or versioned agreement |
| [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) | licence, attribution or versioned agreement |
| [app/src/main/assets/colibri/THIRD_PARTY_NOTICES.md](../app/src/main/assets/colibri/THIRD_PARTY_NOTICES.md) | licence, attribution or versioned agreement |
| [core-hyperl/LICENSE_HISTORY.md](../core-hyperl/LICENSE_HISTORY.md) | licence, attribution or versioned agreement |
| [core-hyperl/MODIFICATIONS.md](../core-hyperl/MODIFICATIONS.md) | licence, attribution or versioned agreement |
| [core-hyperl/src/main/assets/hyperl/LICENSE_HISTORY.md](../core-hyperl/src/main/assets/hyperl/LICENSE_HISTORY.md) | licence, attribution or versioned agreement |
| [docs/PRIVACY_POLICY.md](PRIVACY_POLICY.md) | licence, attribution or versioned agreement |
| [docs/TERMS_OF_USE.md](TERMS_OF_USE.md) | licence, attribution or versioned agreement |

## Template documents

| Document | Scope |
| --- | --- |
| [.github/pull_request_template.md](../.github/pull_request_template.md) | pull-request template |

## Third-Party documents

| Document | Scope |
| --- | --- |
| [tests/e2e/node_modules/@playwright/test/README.md](../tests/e2e/node_modules/@playwright/test/README.md) | dependency text |
| [tests/e2e/node_modules/@types/node/README.md](../tests/e2e/node_modules/@types/node/README.md) | dependency text |
| [tests/e2e/node_modules/playwright-core/README.md](../tests/e2e/node_modules/playwright-core/README.md) | dependency text |
| [tests/e2e/node_modules/playwright/README.md](../tests/e2e/node_modules/playwright/README.md) | dependency text |
| [tests/e2e/node_modules/undici-types/README.md](../tests/e2e/node_modules/undici-types/README.md) | dependency text |
| [vendored/licenses/colibri/THIRD_PARTY_NOTICES.md](../vendored/licenses/colibri/THIRD_PARTY_NOTICES.md) | dependency text |
