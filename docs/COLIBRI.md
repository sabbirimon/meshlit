# Colibri: optional host inference

The integration targets JustVugg/colibri revision
`bf2442915d6e3dd4cdfd2eb9c2a3d2aa44a25850`. Colibri is Apache-2.0; its
LICENSE, NOTICE and THIRD_PARTY_NOTICES are preserved separately. Meshlit's
own adapter is independent code. No Colibri weights, engine binaries or GPU
drivers are bundled in Android. Source preparation is explicit and bounded to
the pinned source checkout; it does not download models.

## Controls and routing

Colibri is Experimental and off by default. Host access, agent switching and
conversation routing are separate grants. Users configure the exact API base,
private token and a model returned by the host. A read-only model refresh gives
five minutes of availability evidence. It is not a generation benchmark or
proof of model accuracy, host identity or GPU acceleration.

- **Off** retains the existing local chat path.
- **On** requires an authorized host and fresh selected-model evidence; missing
  setup blocks the request. A failed host request remains a failure.
- **Auto** chooses that host when no local model is ready, or when the user has
  selected “Prefer host in Auto”. Otherwise it keeps local inference. It does
  not measure or assume hardware power, initiate discovery or download weights.

Explicit provider, router and local-tool conversations retain their existing
route. Human conversation settings and human host policy remain authoritative.
An agent's mode request requires saved host delegation, Models delegation,
global automation/cloud grants and the active conversation's user grant. It is
an in-memory override for that conversation, expires after 30 minutes and cannot
edit hosts, keys or saved policy. Human edits/revocation clear overrides and
cancel active Colibri requests. Stop cancels the client request; host-side
engine interruption remains the host's responsibility.

## Transport, privacy and limits

Use `https://host:port/v1` with trusted platform TLS and an enforced bearer token
for LAN or Internet clients. Plain HTTP is accepted only for literal loopback
`127.0.0.1`/`::1`, never a LAN address or hostname. Credentials, queries,
fragments, redirects and automatic retries are refused. Encrypted Android
storage holds endpoint credentials; tokens do not appear in chat history,
tool arguments/results, logs or exported configuration. The selected host
receives the chosen conversation context, system instructions and user text.
No Web search or cloud-provider permission is implied by selecting Colibri.

The Android adapter is buffered text, with cancellation, a 120-second deadline,
bounded JSON size/depth, 512 model IDs, 96,000 context characters and 2,048 output
tokens. Desktop uses its existing bounded SSE reader, up to 4,096 output tokens.
Reported token counts stay unknown when absent. Displayed tokens/s includes
request latency and is not an isolated decode benchmark. Reasoning metadata,
tool execution, image/audio generation, decision endpoints and scheduler/model
management are outside this adapter.

The pinned registry includes GLM, GLM-5.3-Flash, DeepSeek V4/V4.1, Qwen3.6,
Qwen3.8, Kimi K3, MiMo, Inkling and OLMoE text families, plus separate image and
decision engines. Actual checkpoint, memory, disk capacity and accelerator
support depend on the host's build and model. An advertised family is not
proof that this Mac, a phone or a mixed phone cluster can run that checkpoint.
The application lists actual `/v1/models` identifiers, not an invented catalog.

Core Candidate refuses this route. HarmonyOS NEXT uses the explicitly enabled
generic HTTPS host client source; DevEco compilation, HAP signing, runtime
integration and physical hardware qualification remain pending. No Compose
port to NEXT or Android APK compatibility is implied.

See [host preparation](../companions/colibri/README.md), the pinned upstream
[API](https://github.com/JustVugg/colibri/blob/bf2442915d6e3dd4cdfd2eb9c2a3d2aa44a25850/docs/api.md)
and [registry](https://github.com/JustVugg/colibri/blob/bf2442915d6e3dd4cdfd2eb9c2a3d2aa44a25850/c/family_registry.py).
Model licences are separate. Benchmarks, fixture tests and compilation are
recorded separately from real model inference.
