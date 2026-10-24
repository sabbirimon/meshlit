# Reply presentation and token controls
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Requested 2026-10-08. The owner's reference screenshots are visual
references from another phone, not Meshlit runtime or provider-account evidence.
The connected Samsung's earlier screen showed joined numbered sections, a large
bottom navigation strip and crowded composer controls.

## Current implementation

The four app bottom tabs are removed from the shared modern shell for both
flavors. The sidebar retains Models, Monitor, Networking, SSH, Labs and settings.
Android's own system navigation remains under the owner's OS preferences.
The composer keeps attachment, microphone and Send/Stop controls; photo/camera is
available in the attachment menu. Chat menu opens conversation search, settings and model details; the top bar and sidebar provide global search.

Replies use pinned CommonMark 0.30.0 with GFM tables, rendered as native Compose
blocks. No historical chat UI or inference mechanism is restored. Paragraphs,
headings, emphasis, inline code, ordered/nested lists, quotations, rules and fenced
code keep distinct spacing. Assistant content uses the reading-column width;
user messages remain compact bubbles. Paragraphs and table groups are split into
lazy-list items, so large replies do not become one giant scroll item.
Headings, bold emphasis and human-clicked links use the selected theme's accent;
inline code uses a contrasting background. Body prose keeps the normal readable
text color. Model-provided emoji text is preserved, including surrogate pairs at
chunk boundaries (artwork depends on installed Android fonts); Meshlit does not decorate or rewrite the model's answer.

Tables scroll horizontally when needed; groups of twelve rows repeat headers.
Code is selectable/copyable, can wrap horizontally and has a bounded vertical
viewport. Large code fences are split into labelled 4,096-character parts; Copy code copies
the displayed part, while the response's Copy/export preserves the full original.
HTTP(S) Markdown and plain source links open only after a human click. Images become
labelled links, without automatic fetches. HTML stays literal text: no WebView,
JavaScript, remote font or model-generated command execution. Invalid/oversized
formatting falls back to chunked original text, not a replacement answer.
AST conversion is bounded to 131,072 input UTF-16 code units, 12,000 visited nodes, depth 32 and
800 blocks; larger/deeper output keeps the original text through plain fallback.
Formatting runs off the main thread. Copy, Share and export preserve original text.

Each response has Copy, Share, Read full response and a menu for Markdown export
and token details. The full-screen reader removes the composer and provides a
heading outline, text search/Next and original Markdown view. It does not change
the selected model or message contents. The scroll follows new output until the
reader scrolls away; Jump to latest resumes following.

## Token management and speed

Open **Chat menu → Conversation and token settings**. The expanded sheet has
separate model/routing, tool permissions, token management and sampling sections.
Set exact output ceiling 1–2,048 (routed requests up to 1,024), quick presets and
0–20 history messages. Manual/Automatic cluster mode and a target duration use only eligible measured native cluster evidence; unknown/stale evidence visibly falls back to Manual. See [search and cluster output](SEARCH_AND_CLUSTER_OUTPUT.md). The ceiling is not actual usage or guaranteed answer length.
Reducing history keeps saved messages. Loaded context capacity is shown when
known; no tokenizer-based preflight/remaining-context estimate is claimed.

**Show token speed indicator** is saved per conversation and can be turned off.
While running, it shows actual monotonic elapsed application time and requested
limit. Counts and rates arrive after completion. The recorded response stores
authoritative input/output/cache counts, elapsed time, requested limit, known
context capacity, finish reason and rate provenance. Unknown/invalid counters
stay unknown. Cache counts must be within reported input usage.

Prefer a valid runtime-reported rate only when actual generated-token counts are
present. Otherwise, reported output tokens divided by full application elapsed
time give a labelled **end-to-end average**, including input/network overhead.
This is not isolated decoder throughput. Text callbacks, words and characters
are never promoted to tokens. The pinned SDK's unqualified terminal counters
remain excluded; the Samsung SDK route consequently reports speed unavailable.
Tool loops and routed multi-step totals are not aggregated. Old messages load
without invented usage; each new response retains its own details across restart.

## Explicit native charts

Only a fenced `meshlit-chart` block with the following exact JSON schema produces
a native bar chart. It does not infer or manufacture numeric data from prose or
tables. This example is illustrative fixture data, not a measured benchmark:

```meshlit-chart
{"type":"bar","title":"Illustrative values","unit":"ms","labels":["A","B"],"values":[10,20]}
```

The schema accepts only `type`, `title`, `unit`, `labels`, `values`; `type` must be
`bar`. Limits: 16,384 JSON UTF-16 code units, 1–24 labels (80 characters each), title 160 characters,
unit 32 characters, same number of actual numeric finite values, magnitude up to
10^12. Zero/negative values use a zero baseline; exact labels/values accompany the
chart for reading/accessibility. Invalid chart payloads remain visible as code.
There is no automatic web research, remote image gallery, arbitrary interactive
visualization, full plotting language or chart correctness guarantee.

## Validation boundaries

Parser/accounting unit contracts and physical-phone UI checks have separate
evidence. Labelled renderer fixtures are not generated model responses or live
provider/weather data. Actual app-reader tests use the owner's existing reply,
leave chat/model selection unchanged, test Cancel and Save/persistence/reopen, then restore the owner’s settings. Copy/Share/export
remain human-initiated OS actions. New build/device results are recorded in
`DEVICE_TESTING_2026-10-08.md` once complete; no unrun UI or provider test is passed.

Upstream parser/license: https://github.com/commonmark/commonmark-java,
BSD-2-Clause. License distributed with the APK under `assets/licenses/` and noted
in `THIRD_PARTY_NOTICES.md`.
