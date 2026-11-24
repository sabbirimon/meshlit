# External MCP and OpenClaw integration

The previous page is preserved in
`../history/bring-your-own-server-before-2026-10-06.md`. Its claims about bundled
Hermes/OpenClaw/OpenCode scaffolding, automatic multicast discovery and custom
server settings pages were not supported by this checkout. Do not run the
old package/CLI examples as setup instructions.

## Implemented integration paths

- Embedded tools are registered through `core-mcp` and app DI.
- Optional crawler: `companions/crawler/README.md` and the modern Advanced
  settings crawler card. The host service is installed separately.
- OpenClaw gateway, Android node and phone model provider:
  `../openclaw-integration.md`. This uses OpenClaw's gateway protocol and
  HTTP model APIs; it is not an embedded generic TypeScript MCP server.
- Typed app commands: `../typed-agent-backend.md`. Saved delegation scopes
  and observable jobs apply without navigating the user interface.

A general external-server onboarding UI needs a verified transport, connection
state, credential storage, cancellation, tool-schema validation and explicit
permission handling before it can be advertised. Confirm the actual adapter
and server protocol in source rather than assuming a framework or port number.
Do not expose an unauthenticated control endpoint to the LAN.
