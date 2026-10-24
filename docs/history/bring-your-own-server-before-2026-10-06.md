# Bring-your-own MCP server

Meshlit ships a set of bundled MCP (Model Context Protocol) servers that
cover the common chat-to-action cases (Remote Control, Filesystem, Cloud
Browser, Android Automation) — the **GENERAL PURPOSE** category. A second
set — **SPECIALIZED** — is opt-in: Clipboard, Notifications, Device Info,
Web Fetch.

For everything else (your own database, internal APIs, GitHub, Slack,
etc.) you can stand up an MCP server in any language that speaks the MCP
wire protocol and point Meshlit at it from
**Settings → MCP Servers → Custom Servers → Add**.

This doc walks through the three open-source frameworks Meshlit has
embedded to make that path low-friction for non-Android developers.

## Hermes (Python)

[Hermes](https://github.com/zb3/hermes) is the smallest of the three.
A working server is ~300 LoC.

```bash
pip install hermes-mcp
cat > my_server.py <<'EOF'
import hermes

app = hermes.App(name="my-server")

@app.tool()
def add(a: int, b: int) -> int:
    """Add two integers and return the sum."""
    return a + b

if __name__ == "__main__":
    app.serve(host="0.0.0.0", port=7700)
EOF
python my_server.py
```

Meshlit discovers the server via LAN multicast on port 7700. If your
firewall blocks multicast, paste `http://<host>:7700` into
**Settings → MCP Servers → Custom Servers → Add** instead.

### When to pick Hermes

- You already have Python tooling you want to expose.
- You want a one-file script, not a package.
- You need docstring-as-schema (Hermes reads type hints + docstrings
  and emits a JSON schema on `tools/list`).

## OpenClaw (TypeScript / Node)

[OpenClaw](https://github.com/openclaw/openclaw) is the right pick when
you already have an npm package or a typed TypeScript codebase. It
ships an Express transport and a CLI that scaffolds a server.

```bash
npx openclaw init my-server
cd my-server
npm install
cat > src/index.ts <<'EOF'
import { OpenClaw } from "@openclaw/sdk";

const server = new OpenClaw({ name: "my-server", port: 7701 });

server.tool("greet", {
  description: "Return a friendly greeting",
  input: { name: "string" },
  handler: ({ name }) => `Hello, ${name}!`,
});

server.listen();
EOF
npm run build && node dist/index.js
```

### When to pick OpenClaw

- You want a typed tool manifest (`zod` or `io-ts`) instead of Python
  docstrings.
- You need the built-in OAuth flow for `auth: required` tools.
- You're wrapping an existing npm package as an MCP server.

## OpenCode (Rust + WASM)

[OpenCode](https://github.com/opencode-ai/opencode) compiles to a
single static binary. The build is slow, but the runtime is the
fastest of the three and ships with sandboxing.

```bash
cargo install opencode
cat > src/main.rs <<'EOF'
use opencode::prelude::*;

#[opencode::tool]
fn reverse(input: String) -> String {
    input.chars().rev().collect()
}

fn main() {
    opencode::serve("reverse-server", 7702);
}
EOF
cargo build --release
./target/release/reverse-server
```

### When to pick OpenCode

- The tool needs sandboxed untrusted-code execution (a `code-eval` MCP,
  for example).
- You care about cold-start latency — OpenCode's AOT binary beats
  Python and Node by 10-50x.
- You already maintain a Rust workspace.

## Wiring it into Meshlit

1. Make sure the server is listening on a host Meshlit can reach
   (same Wi-Fi, Tailscale tailnet, or a public host with HTTPS).
2. Open **Settings → MCP Servers → Custom Servers → Add**.
3. Paste the URL (Hermes default: `http://<host>:7700`, OpenClaw:
   `http://<host>:7701`, OpenCode: `http://<host>:7702`).
4. Set the auth header if your server requires one
   (`Authorization: Bearer <token>` is the default Meshlit sends).
5. Pick a category: **GENERAL PURPOSE** for tools the agent should
   always be able to call; **SPECIALIZED** for tools that should only
   run on explicit request.
6. Hit **Save**. Meshlit handshakes, lists the tools, and persists
   the entry so it survives a cold restart.

## Debugging

**Settings → MCP Servers → Host** opens `McpServerHostScreen` — a
live tool-call log with per-tool latency and an Allow / Deny toggle.
This is where to look when the agent reports `tool X failed` and you
need the raw request / response to diagnose.

Common first-time failures:

| Symptom | Likely cause |
|---|---|
| Server reachable, but no tools listed | `tools/list` returns `[]` — Meshlit treats that as "not ready" and won't show the server in the agent's tool registry. Make sure at least one `@tool` / `server.tool(...)` / `#[opencode::tool]` is registered before `app.serve` / `server.listen` / `opencode::serve`. |
| `tool call denied` | Per-tool permission toggle is off in **Settings → MCP Servers → Permissions**. Flip to `Always allow` for tools you've audited. |
| Auth header mismatch | Meshlit sends `Authorization: Bearer <token>`. If your server expects `X-Api-Key`, set the header name in **Settings → MCP Servers → Custom Servers → Edit**. |
| Server crashes on first call | Open the host log; Python stack traces usually point at a missing `requirements.txt` dep, Node failures are almost always ESM/CJS interop, and Rust panics usually mean the tool signature changed without restarting the server. |

## See also

- `app/src/main/kotlin/com/meshlit/ui/screens/mcp/McpServerHostScreen.kt`
- `core-mcp/src/main/kotlin/com/meshlit/core/mcp/BundledMcpServer.kt`
- `app/src/main/kotlin/com/meshlit/ui/screens/settings/McpServersSettingsScreen.kt`
