pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("com\\.google\\.firebase.*")
                includeGroupByRegex("com\\.google\\.gms.*")
                includeGroupByRegex("com\\.google\\.mlkit.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "meshlit"

// Vendored RunAnywhere SDK Kotlin sources live in
// `vendored/runanywhere-kotlin/` for reference only — the actual
// artifact is fetched via Maven (`libs.runanywhere.sdk`).
// See vendored/runanywhere-kotlin/{LICENSE,README,MODIFICATIONS}.md.

// Modules in topological order: leaves first, then app consuming them.
include(
    ":core-common",
    ":core-config",
    ":core-flags",
    ":core-trust",
    ":core-discovery",
    ":core-inference",
    ":core-mcp",
    ":core-cloud-mcp",
    ":core-training",
    ":core-files",
    ":core-ssh",
    ":core-firewall",
    ":core-guardrails",
    ":core-tunnel",
    ":core-users",
    ":core-terminal",
    ":core-bootstrap",
    ":core-registry",
    ":core-lifecycle",
    ":core-probe",
    ":core-role",
    ":core-orchestration",
    ":core-advanced-engines",
    ":core-gpu",
    ":core-net",
    ":core-observability",
    // Phase 6 — versioned peer-to-peer federation protocol.
    // Owns the wire codec (JSON over TLS 1.3 with mTLS), the
    // `protocol_version` handshake, the `/v1/*` endpoints, and
    // the `PeerTransport` interface that the durable agent
    // kernel calls when it hands off a session to another
    // device. ADR-008.
    ":core-federation",
    // Phase 0 LLM-agents — on-device reimplementation of the
    // layered memory architecture borrowed from the (rejected)
    // TencentDB Agent Memory review. See
    // ./Users/code/.puku-cli/plans/tencentdb-agent-memory-review.md
    // for the architecture borrow list + the on-device rationale.
    ":core-agent-memory",
    ":feature-advanced",
    ":feature-ghosty",
    ":app"
)
