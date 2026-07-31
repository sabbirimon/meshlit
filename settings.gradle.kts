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

// Modules in topological order: leaves first, then app consuming them.
include(
    ":core-common",
    ":core-trust",
    ":core-discovery",
    ":core-inference",
    ":core-mcp",
    ":core-training",
    ":core-files",
    ":core-ssh",
    ":core-firewall",
    ":core-guardrails",
    ":core-tunnel",
    ":core-users",
    ":core-orchestration",
    ":app"
)
