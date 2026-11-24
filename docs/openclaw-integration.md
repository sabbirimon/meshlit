# OpenClaw in Meshlit

Reviewed 2026-10-06 at upstream `a81b9cd71a5991597ab664f2cf8561b3e0afbe30`.
Sources: [Android](https://docs.openclaw.ai/platforms/android),
[Gateway protocol](https://docs.openclaw.ai/gateway/protocol),
[HTTP agent client](https://docs.openclaw.ai/gateway/openai-http-api),
[embedding](https://docs.openclaw.ai/gateway/embedding).
OpenClaw is MIT; its notice is retained at `vendored/licenses/OpenClaw-MIT.txt`.
Upstream source is reviewed separately; its entire Android app is not copied into this APK.

## Implemented integration

Settings → OpenClaw and autonomy contains three distinct opt-ins:

1. **Gateway agent client.** Encrypted HTTPS gateway/operator token and agent target.
   Send requests use OpenClaw's `/v1/chat/completions`, with a per-screen session ID.
   Enable `gateway.http.endpoints.chatCompletions.enabled` on your own gateway.
   This is owner/operator access, not a restricted mobile chat credential.
   The gateway runtime runs on the host. Phone inference remains Meshlit's default.
2. **Paired Android node.** Explicit WebSocket connection, protocol 4, Ed25519 identity,
   nonce/challenge timestamp and v3 signature. Gateway-specific node tokens are encrypted.
   New device identities require gateway approval: inspect `openclaw devices list`
   and approve the exact request. No automatic approval is issued by Meshlit.
   Advertised commands: `device.info`, `device.status` and, when delegated,
   `meshlit.android.control`. Permit the custom command in the gateway node policy
   explicitly; it is not a stock upstream `mobile.ui.*` implementation.
   A persistent notification offers Disconnect. Reconnect is explicit; failed pairing
   never executes commands. This limited adapter is not the entire official Android app.
3. **Phone model provider.** Start an authenticated loopback OpenAI endpoint at
   `http://127.0.0.1:18791/v1`; model ID `meshlit-local` means the coordinator's currently
   loaded real local or layer-pipeline model. GET `/models` lists it only when loaded.
   POST `/chat/completions` supports text, bounded sampling and buffered SSE output.
   Function tools, images and tool messages currently return an explicit unsupported error.
   This text provider must not be advertised as a complete local OpenClaw tool-loop backend.

## Pair local phone models with OpenClaw

Load a verified model first, enable sharing and copy the private provider token.
A gateway on another device cannot reach phone loopback directly. Use an owner-configured
TLS tunnel/private ingress; do not bind the cleartext provider to the LAN.
For a development computer with the device owner explicitly approving USB debugging,
`adb forward tcp:18791 tcp:18791` provides a local tunnel to the phone endpoint.
Meshlit does not enable debugging or authorize ADB itself.

Example custom provider configuration on that gateway (text-only experimental):

```json5
{
  models: {
    mode: "merge",
    providers: {
      meshlit: {
        baseUrl: "http://127.0.0.1:18791/v1",
        apiKey: "${MESHLIT_PROVIDER_TOKEN}",
        api: "openai-completions",
        models: [{ id: "meshlit-local", name: "Meshlit phone", input: ["text"], reasoning: false,
          contextWindow: 2048, maxTokens: 256 }],
      },
    },
  },
}
```

Choose a contextWindow no larger than the actual loaded context. Gateway compatibility
and tool-loop acceptance need live verification; a successful curl text response is
not proof of a working autonomous OpenClaw agent. Configure another tool-capable model
for gateway tasks until Meshlit's phone function-call contract is implemented and tested.

## Android autonomy

Enable autonomous delegation, choose package scope or all supported apps, and manually
enable Meshlit in Android's accessibility settings. Each action reads actual foreground
package and saved settings. Supported actions: snapshot, click by descriptor, focused
text input, app launch, Back and Home. Password text is redacted from snapshots and refused
for injection; configured high-risk system packages require human control. This grant does
not confer root, grant Android permissions, bypass secure screens or defeat OS restrictions.

The production service now uses AccessibilityNodeInfo/AccessibilityService directly;
its former UiDevice(null instrumentation) path could not work in an ordinary APK.
Trees are bounded, node references recycled and caller target claims do not override
foreground scope. Emergency stop revokes autonomy, disconnects the node and closes model
sharing. An already dispatched individual OS action cannot be undone by revocation.

## Remaining acceptance

- Build/test both flavors after these changes and validate ordinary-APK actions on devices.
- Real gateway signed-pairing, reconnect/token rejection, custom-command policy and revocation tests.
- Canonical signature golden-vector and hostile-frame tests; node invoked actions need audit receipts.
- Paired operator WebSocket chat (replace owner HTTP secret for least-privilege mobile access).
- Official mobile.ui contract, optional camera/mic/location command families only after grant checks.
- Model-appropriate native chat templates and verified structured function calling.
- Optional supervised gateway host/VM installation, readiness and recovery. The TypeScript gateway
  and Node.js runtime are not bundled or silently installed inside Android.

### Typed backend over the paired node
The node also advertises `meshlit.agent_command_submit`, `meshlit.agent_job_status`,
`meshlit.agent_job_cancel`, `meshlit.agent_job_retry` and `meshlit.agent_command_schema`.
Their params match the corresponding MCP tools. They use the same durable command
controller and saved scopes; node pairing alone never grants mutation permissions.
The gateway must explicitly allow the intended custom commands in its node policy.
