# Portable configuration

Import `meshlit-default-v1.json` from Settings → Configuration Profiles, inspect
the preview, then apply it. It disables no existing credentials or scopes and
adds no providers/models; its empty listener firewall denies new connections.
This is a safe desired-settings baseline, not peer enrollment or model files.

Exports from the app are the best template for a custom configuration. The
strict Kotlin serialization contract is
`app/src/main/kotlin/com/meshlit/configuration/PortableConfiguration.kt`.
Unknown fields, secret fields and versions fail. Do not add hypothetical cluster,
Terraform, radio or camera-resource fields to schema v1. Those require schema v2
and real adapters from `docs/declarative-federation-roadmap.md`.

Model IDs are reconciled locally; missing IDs are reported/skipped. Imported
provider templates remain disabled and agent access off. Keys, device trust,
Android grants, root/autonomy consent and agent delegation remain device-local.
The app validates before applying; settings span stores, so errors may report a
partial apply that should be inspected before reapplying.
