pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
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
    ":core-orchestration",
    ":core-advanced-engines",
    ":core-gpu",
    ":core-net",
    ":core-observability",
    ":feature-advanced",
    ":feature-ghosty",
    ":app"
)
