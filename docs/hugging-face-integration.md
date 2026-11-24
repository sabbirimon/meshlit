# Hugging Face integration

Models includes a live Hub and hosted API panel. Nothing is fetched until the
user searches, reads a repository or explicitly sends a hosted request.

## Local downloads

Search uses the public Hub model API filtered for GGUF. Repository inspection
uses `?blobs=true` and pins its reported commit revision. Only real single-file
GGUF artifacts appear; size and SHA are shown only if the API reports them.
The selected artifact is installed by the existing verified HTTPS downloader
or RunAnywhere's actual SDK flow. GGUF validation and published hashes run before
Installed status. Public ungated files need no account token. Gated/private
repositories use an encrypted read token and still require the user's Hub access
or license acceptance. No subscription switch grants repository permission.

## Hosted API

A separate encrypted inference token is used for hosted requests. The user saves
an explicit enabled configuration, model/provider suffix, HF router or dedicated
HF endpoint, and optional `X-HF-Bill-To` organization/resource-group identifier.
The panel sends a real nonstreaming chat request and displays the actual JSON,
including any returned usage. It can list actual hosted model IDs. Dedicated
endpoints must support `/v1/chat/completions`; no endpoint is provisioned by Meshlit.

Credentials are sent only to validated HTTPS Hugging Face router/dedicated hosts;
redirects are disabled. Responses, time and prompt/output budgets are bounded.
Cancellation cancels the real HTTP call. HTTP 401/403/402/404/429 states are surfaced.
There is no automatic paid retry or cloud fallback from local chat. Secrets and
server error bodies are not logged. Repository/API content is untrusted data.

Free accounts, PRO and organization accounts may have different credits and billing
rules. Meshlit does not infer subscription from the token, display invented credit
balances or buy credits. Provider keys configured on the user's HF account can
change how HF bills routed requests; organization billing remains an explicit
saved setting. Verify charges and spending limits in Hugging Face itself.

Hosted chat currently lives in this explicit panel. Main chat routing, image/speech
provider APIs and delegated autonomous hosted requests are later integrations;
no nonfunctional switch claims that those are complete.

Primary API documentation reviewed 2026-10-06:
- https://huggingface.co/docs/hub/en/api
- https://huggingface.co/docs/inference-providers/en/index
- https://huggingface.co/docs/inference-providers/en/pricing

Public Hub discovery can be verified without credentials. Authenticated gated
access and hosted billing/generation require user credentials and have not been
claimed as live-tested. See PROGRESS.md for exact evidence.

## Main chat, pricing and free-offer update

Main chat now selects saved online profiles as well as offline inference. Public
HF hosted model discovery and published pricing/free offers can be refreshed
without login. Inference still needs the configured provider's actual access
conditions/token. Offers retain nullable rates/free status and source/timestamp;
only `is_free=true` qualifies for the free filter. Applying a price pins its
provider. Other provider prices use reviewed official pages. See
`online-power-peripherals-and-configuration.md` for implemented limits and scopes.
