# Optional Colibri host

Meshlit connects to a separately operated Colibri host. This does not install a
frontier model on a phone, combine phone VRAM, or qualify any GPU backend.
The reviewed source is JustVugg/colibri at
`bf2442915d6e3dd4cdfd2eb9c2a3d2aa44a25850` (Apache-2.0). Preserved upstream
licences and notices are in `vendored/licenses/colibri/`. Model licences and
download requirements are independent.

Fetch source only, explicitly:

```sh
python3 companions/colibri/host.py fetch --source vendored/colibri
```

Read the pinned checkout's README and build instructions. Build the backend
appropriate to your actual hardware and obtain compatible weights yourself.
No setup, engine build, driver install or model download runs from an app toggle.
The gateway uses Python's standard library; the C inference core is separate.
The project's hardware/model claims are upstream claims, not Meshlit measurements.

Set `COLI_API_KEY` privately in the host environment, then start an already built
host with an existing model directory. The key must contain 32–4096 characters.
Do not put a real key in a command, repository, screenshot or chat:

```sh
python3 companions/colibri/host.py serve --source vendored/colibri \
  --model /path/to/existing/model --model-id my-colibri-model --port 8000
```

This launcher binds only `127.0.0.1`. Desktop clients on the same machine may
use `http://127.0.0.1:8000/v1`. Phone, LAN and Internet clients require a trusted
HTTPS gateway/tunnel and the host's enforced bearer key. Do not expose a raw
unauthenticated Colibri port. TLS uses platform trust; no trust-all fallback or
redirect forwarding is allowed. A phone's loopback is the phone itself.

The Meshlit adapter uses only `/v1/models` and text `/v1/chat/completions`.
Other upstream modalities, tool calls, decision APIs, scheduler changes, model
loads, shell execution and automatic hardware tuning are not delegated by this
adapter. Cancellation closes the client request; whether the host releases its
engine promptly depends on the host implementation.
