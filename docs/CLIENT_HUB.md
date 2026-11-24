# Meshlit as an authenticated model and agent hub

Updated 2026-10-08. Current application: Android Full builds. The built-in gateway
runs only while its visible screen remains open. It is not an always-on background
service, OAuth server or an independently qualified enterprise deployment.

## Issue access to a client

Load a compatible real local model, then open **Agent Gateway** from the workspace
sidebar/settings. Set the model ID and loopback port (default 18893), save and start.
The configured content policy and existing inference/tool/operation grants apply.

In **Client access**, issue a named client key with explicit model IDs and scopes:
`MODELS`, `CHAT`, `MCP`, `A2A`. MCP tool names are independently allowlisted. Set an
expiry, maximum output tokens and requests/minute. Copy the newly issued key once;
only its SHA-256 verifier and policy are retained in encrypted app storage. Keys
are random 256-bit opaque bearer secrets, not JWTs. Keep them out of prompts,
ordinary config exports, URLs and browser JavaScript. Copy explicitly places a
secret on the OS clipboard.

Policy changes, client issuance/removal/revocation and owner-token rotation stop
the gateway and active owned work. Restart after reviewing the updated configuration.
Rotating the owner token does not itself revoke independently issued client keys;
use each client's Revoke/Remove control. Expired and revoked keys are rejected.

| Limit | Current behavior |
| --- | --- |
| Client records | At most 64; remove expired/revoked records to make room |
| Key lifetime | Core issuance: 1 minute–30 days; UI: 1–720 hours |
| Output budget | 16–1,024 requested output tokens per client |
| Request budget | 1–120 requests/minute per client; fixed local window |
| Active requests | One per client; two globally at the embedded server |
| Request body | At most 256 KiB JSON |
| Handler deadline | At most 120 seconds and no later than key expiry |
| Model/tool policy | Up to 32 exact model IDs and 64 exact tool names |
| Job identity | MCP/A2A job reads/cancellation bound to the authenticated client |

Budgets constrain requests; they are not measured native token usage or a cluster
capacity claim. Caller-supplied names/IDs do not enlarge permission. A missing or
wrong key returns 401, missing scope returns 403, exhausted admission returns 429.
Content policy, Android/agent grants and emergency stop remain independent gates.

## LAN, off-grid and internet clients

The phone binds **127.0.0.1**, not a public/LAN plaintext interface. Same-device
clients can use loopback with their own scoped key. Another device needs a trusted
operator transport: owner-paired USB/ADB forwarding for development, a verified
private encrypted tunnel, or a host HTTPS proxy that keeps its upstream key server
side. No public listener or firewall hole is enabled automatically.

For a local developer check, while the gateway screen is open:

```sh
adb -s PHONE_TARGET forward tcp:18893 tcp:18893
# From the trusted host's local process, use an issued scoped client key.
curl -H "Authorization: Bearer $MESHLIT_CLIENT_KEY" \
  http://127.0.0.1:18893/v1/models
```

The host's loopback forwarding can work without internet, including owner-paired
USB or local wireless debugging. ADB debugging is a trusted developer transport,
not an app-level client identity or production internet exposure mechanism.
Remove the forward with `adb -s PHONE_TARGET forward --remove tcp:18893` when done.

For remote clients, configure verified HTTPS/private transport on an operator-owned
host; do not forward the phone's plaintext port directly to the internet. TLS is
required for exposed bearer tokens, consistent with
[RFC 6750](https://www.rfc-editor.org/rfc/rfc6750.html). A cluster worker's
pinned-TLS protocol is separate from this model API. TLS certificate provisioning,
host deployment, browser companion and live remote-host acceptance remain pending.

## Compatible API clients

The buffered OpenAI-format endpoints are `/v1/models` and
`/v1/chat/completions`. Point a supported desktop/mobile client's API base URL at
your verified gateway transport plus `/v1`, and supply its scoped client key.
Only approved exact model IDs are listed/accepted. Requests must remain within the
client's token policy. Streaming responses are not implemented by this gateway.

```python
# A trusted server/desktop process; obtain secrets from local protected configuration.
import os
from openai import OpenAI
client = OpenAI(base_url=os.environ["MESHLIT_API_BASE"],
                api_key=os.environ["MESHLIT_CLIENT_KEY"])
reply = client.chat.completions.create(
    model=os.environ["MESHLIT_ALLOWED_MODEL"],
    messages=[{"role": "user", "content": "Explain this small function."}],
    max_tokens=512, stream=False)
print(reply.choices[0].message.content)
```

This is a client setup example, not a live Python SDK/provider acceptance result.
`/mcp`, `/a2a` and `/.well-known/agent-card.json` also require authentication and
scope. The current MCP JSON transport and A2A adapter are separately bounded;
OAuth, Streamable HTTP/SSE session interoperability and arbitrary vendor agents
need their own tests. Remote tool discovery does not authorize invocation.

The gateway rejects browser Origin headers. A web UI therefore needs a reviewed
backend-for-frontend with session authentication, exact origins, CSRF controls and
server-held scoped credentials. There is no shipped standalone browser companion
or direct public web app in this increment. Client-key local HTTP tests verify
wrong/expired/revoked keys, scopes, models and token limits; this is not physical
LAN/internet, multi-client throughput or cluster security qualification.

## Next production gates

A supervised foreground/background service, explicit network-listener ownership,
certificate enrollment/revocation, client session/audit views, user/admin roles,
quotas backed by actual usage, key rotation UX, safe web companion, multi-client
load/revocation/expiry tests and independent review precede production exposure.
Real multi-phone model execution and failover remain separate acceptance gates.
