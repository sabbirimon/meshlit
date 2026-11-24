# Global search, chat search and cluster output controls

Implemented in the shared modern Android interface for both flavors, phone and
wide windows. These controls use the current UI and inference pipeline.

## Find a feature or setting without Internet

Tap the top-bar Search icon in Chat, or **Menu → Search all of Meshlit** from any
screen. All local searches connected workspace pages and their keywords, current
chat options, appearance/voice controls, saved model names, saved conversation
titles/messages, imported articles and approved device access records. Result
cards identify their category and open a real destination. Token controls open
the conversation sheet; a chat result selects that conversation and opens chat
search. Stop a running generation before switching conversations.

Local search runs off the UI thread. It includes the latest 40 saved chats, at
most 100 messages per chat, up to 20 matching messages per conversation, 200 saved
model entries, 20 articles and 1,000 approved device records. A query returns at
most 200 cards. The app does not enumerate arbitrary storage or search credential
stores. Multiword local queries require every word; previews are bounded snippets.
The App, Chats, Articles and Devices category controls filter the same local results. Web has
a separate explicit request button; typing never triggers a web request.

Use cases: find **SSH**, **Monitor**, **Networking**, **Security Lab**, fonts,
permissions, model context, output ceilings or a remembered conversation without
loading an LLM or contacting a server. Searching identifies a control; it does
not toggle permissions, run a command or change a model by itself.

## Find text inside a conversation

Choose **Chat menu → Search this chat**, type a phrase, then use Previous/Next.
The timeline remains intact and scrolls to matching message blocks; the current
message block receives a theme highlight. This searches the selected saved
conversation, including user text and rendered response blocks. It is separate
from **Read full response → Search**, which searches just one response. Closing
search keeps your conversation and settings unchanged.

## Keep articles available off-grid

Open **Settings → Search access and articles → Import article**. Android's file
picker grants a read of the selected UTF-8 text/Markdown document. Meshlit copies
the content into private app storage and indexes it without a network connection.
Limits: 20 articles, 128 KiB each, and 4 MiB for the encoded index; binary/PDF/office
parsing is not provided. Imports exceeding the storage limit are rejected before
replacing the saved index.
Read article uses the native response reader; Remove deletes that indexed copy.
The original file stays where you selected it. This storage is ordinary local
app data, like chat history, not an encrypted document vault.

Web results can **Save snippet**. This stores the returned snippet with source
URL and retrieval time, explicitly labelled as a snippet rather than the full
article. It does not crawl that page. Imported/retrieved text is untrusted data;
it cannot change grants, execute commands or serve as system instructions.

## Device settings on a private LAN or over Internet

Devices local results contain approved enrollment names, kinds, roles and saved
access scopes. They omit credential hashes/tokens and exclude pending/revoked
devices. These are local access records, not proof of live remote settings.

For a live read, use **Devices → Read approved device settings**. Configure a
human-enabled authenticated MCP route in Agent Gateway with the exact
`device_settings_read` allowlist. On the remote Meshlit, separately allow Settings
delegation and issue a client key permitted to call that tool. Its endpoint must
be an approved HTTPS/private proxy or loopback transport; the existing gateway
does not become a public listener. LAN/private connectivity works without Brave
or Internet search. Internet use needs the operator's reviewed HTTPS/private
transport, valid credentials and ordinary certificate verification.

Only the specific read-only tool is invoked. The supported snapshot contains
theme mode, accent, dynamic colors, font, surface, animation, font scale, contrast,
layout and sidebar style. No arbitrary Android setting, password, remote file or
mutation is requested. Responses retain route and reception time, validate schema
and whitelist fields. Unsupported/denied routes report failure. Reading has a
90-second overall deadline, 20 seconds per tool and at most eight routes; Stop
cancels it. Remote Settings delegation and MCP/client approvals are independent
of a device being discovered or enrolled. A snapshot is not continuous monitoring.

## Turn Internet search and agent access on or off

**Search access and articles** provides independent controls:

| Control | Default | Effect |
| --- | --- | --- |
| Human local search | Available | Searches local app content without Internet |
| Allow agents to search local content | Off | Exposes matching saved-chat/article snippets to an authorized agent |
| Internet search | Off | Allows explicit Brave API searches |
| Allow agents to use Internet search | Off | Allows agents to request searches within the Internet grant |
| Per-chat Local search tools / web search and page tools | Off | Exposes selected tools to the on-device chat model |

Save your own Brave Search API key. It is stored encrypted on this device and
used only at `https://api.search.brave.com/res/v1/web/search`, in the provider's
`X-Subscription-Token` header. No account/key/paid entitlement is bundled. The
provider may charge your account. Queries go to Brave; this adapter does not send
the entire chat. The fixed endpoint follows the [Brave API reference](https://api-dashboard.search.brave.com/api-reference/web/search/get).

Search web returns up to ten normalized title/URL/snippet rows with retrieval
time. HTTP failures, missing keys and revoked grants remain explicit failures;
they are not represented as successful empty searches. Redirects and connection
retries are disabled; deadline 20 seconds, body at most 1 MiB, query at most 600
characters/75 words. Result links open in a browser only after a human click.
Opening a source contacts that site under the browser's own permissions.

The separate `crawl_url` tool still requires your configured HTTPS Crawl4AI
companion, credential and approved domains. URLs and fetched content go to that
host. In chat it also observes the Internet/agent grants; disabling Internet
cancels pending chat web/crawler requests. It does not bypass robots, login,
CAPTCHA or other access controls. Web search snippets and approved retrieved
pages remain distinct sources.

Agent tools: `app_search`, `web_search`, `search_access` and the independent
`device_settings_read`. Gateway clients need exact tool scopes in addition to
these saved grants and operation gates. Agents can pause/resume their own web
search via `search_access` **inside** the existing human Internet/agent grant.
They cannot turn on a revoked Internet grant, permit private local content,
edit credentials or silently change Android permissions. Revocation cancels
matching in-flight web work; query/credential changes discard old results.
Local tool planning still permits at most three calls in 180 seconds; ten
descriptors is an inventory cap, not permission for more calls.

## Manual or Automatic cluster output

Open **Chat menu → Conversation and token settings → Token management**.
Manual remains the saved default. Maximum output tokens remains your ceiling,
1–2,048 (routed requests at most 1,024). It is not a promise of reply length.

Automatic cluster optionally uses target output duration, 5–120 seconds. After a
successful native layer-cluster response with at least eight authoritative output
tokens and a finite positive native decode rate, the controller retains evidence
for that exact loaded model/session. Evidence expires after ten minutes and is
not restored after app restart. Reloading/changing the model or host invalidates
the match. Provider, local-device, routed and tool-loop rates are not substituted.

The next eligible request uses the minimum of measured whole-cluster rate ×
target seconds, your ceiling and a quarter of the verified loaded context, with
a minimum of one token. This context fraction is a conservative allocation,
not measurement of free context. Counts from previous prompts do not establish
remaining capacity. Device rates are not added together; adding devices never
claims linear throughput. Missing/stale context/rate/session evidence leaves the
manual ceiling in force, with a visible explanation.

The running indicator and response details retain the actual requested ceiling.
This is a chat request policy, not a tensor-sharding implementation, client quota
increase or gateway authentication change. Multi-phone physical throughput and
thermal tests remain separate from policy unit tests or a single Samsung check.

## Validation

Unit tests exercise permission denial/revocation, provider request shape and
failure handling, remote field whitelisting, offline matching, stale-session
cluster fallback and output bounds. Provider/remote responses in those tests are
labelled fixtures. Device checks search the owner's real saved conversation and
current controls without sending a web request, editing credentials or enlarging
grants. Final build/physical evidence belongs in `DEVICE_TESTING_2026-10-08.md`.
Live Brave account execution and a second physical device are separate gates.
