# Model catalog — classification matrix

Every entry in [`ModelCatalog.kt`](../../app/src/main/kotlin/com/meshlit/models/ModelCatalog.kt)
carries three classification fields that drive the picker UI:

| Field | Type | Used by |
|---|---|---|
| `category` | `ModelCategory` | Chip colour + "filter by category" affordance |
| `minDeviceTier` | `DeviceTier` | Hides rows above the user's RAM tier with a "needs more RAM" hint |
| `ease` | `Ease` | "Easy / Medium / Hard" tag on the row + a confirm-dialog gate before Hard downloads |

This document is the source of truth for **what each model in the catalog
is good for, what device it needs, and how hard it is to set up**.

---

## At-a-glance table

| Entry | Category | Min device tier | Ease | Size | Why it's that rating |
|---|---|---|---|---|---|
| **SmolLM2-1.7B-Instruct · Q4_K_M** | Chat | **Low** (≤ 4 GB) | **Easy** | 1.1 GB | Smallest model in the catalog. Loads on a Pixel 4a. English-first, fast, Apache 2.0. Default for first-run. |
| **Llama-3.2-1B-Instruct · Q4_K_M** | Multilingual | **Low** (≤ 4 GB) | **Easy** | 0.9 GB | 1B params, lightest download, covers 8 European languages. Good first multilingual pick on low-RAM phones. |
| **Qwen2.5-1.5B-Instruct · Q4_K_M** | Multilingual | **Mid** (4–6 GB) | **Easy** | 1.1 GB | Best multilingual coverage at this size: EN/ZH/ES/FR/DE plus more. Slightly higher RAM than Llama-3.2-1B. |
| **DeepSeek-R1-Distill-Qwen-1.5B · Q4_K_M** | Reasoning | **Mid** (4–6 GB) | **Medium** | 1.1 GB | Reasoning-tuned — emits chain-of-thought by default. Verboseness can overwhelm low-RAM devices. |
| **Phi-3.5-mini-instruct · ONNX** *(Phase 2)* | Reasoning | **High** (≥ 8 GB) | **Hard** | 2.4 GB | ONNX distribution; runtime not yet shipped. Listed so the format/runtime link is visible end-to-end. Expects Pro device and patience. |

---

## Categories — what each is for

| Category | What the chip colour means | Pick this when… |
|---|---|---|
| `General` | Blue | …you don't know what to pick. A balanced default. |
| `Multilingual` | Teal | …you chat in EN/ES/FR/DE/IT/PT/ZH/JA/KO. |
| `Coding` | Green | …you want autocomplete / refactor help. (Reserved — no coding model ships in the catalog yet.) |
| `Reasoning` | Amber | …you want chain-of-thought and "show your work" answers. |
| `Chat` | Pink | …you want a fast small-model conversation partner. |
| `Small` | Grey | …you're on a 4-year-old phone with 3 GB of RAM. |
| `Enterprise` | Indigo | …you're on a Pro / Enterprise HF plan and the model is org-gated. |

---

## Device tiers — what each guarantees

The picker uses the device's `CapabilityTier` from `core-common` to decide
which rows are clickable. A row whose `minDeviceTier` is above the user's
tier renders a small "needs more RAM" lock badge and a long-press hint.

| Tier | Min RAM | Representative device |
|---|---|---|
| `Low` | 3 GB | Pixel 4a, Galaxy A20 |
| `Mid` | 4 GB | Pixel 6, Galaxy A52 |
| `High` | 8 GB | Pixel 7 Pro, Galaxy S22 |
| `Pro` | 12 GB | Pixel 8 Pro, Galaxy S23 Ultra, tablets |

The model itself is the same file regardless of tier — what changes is
the device's ability to keep the KV cache warm and not OOM during a long
generation. Picking a model at the wrong tier **does not corrupt the
file**, it just produces slow / cancelled / OOM-killed generations.

---

## Ease levels — how hard to set up

| Ease | Download flow | Post-download | Notes |
|---|---|---|---|
| **Easy** | One tap → progress bar → Done. | "Load" → "Chat". | No HF token required; public repo. |
| **Medium** | One tap → confirm dialog (reasoning models are slow on phones). | May need a few tries before the model "warms up" — first token can take 5–10 s. | No token, but expectations need calibration. |
| **Hard** | Confirm dialog with **two** warnings: (1) format/runtime gap (today: ONNX model has no shipped runtime) and (2) RAM requirement. | Won't actually load until the runtime ships (today: Phase 2). | Listed so the user knows what's planned. Not gated behind a token today, but **gated behind explicit acknowledgement** that the row is a placeholder. |

---

## Free vs paid Hugging Face credentials

The Settings → Models → HF token card now accepts **two** tokens:

| Field | Who fills it | What it's used for |
|---|---|---|
| **Personal access token** | Every user. Free. Create at <https://huggingface.co/settings/tokens>. | `Authorization: Bearer <personal_token>` — required for *gated* repos the user has access to (Llama community, Phi-3, etc.). |
| **Pro / Enterprise org token** | Paid users only. Create at <https://huggingface.co/settings/organizations>. | `X-HuggingFace-Organization: <slug>` + the org token is sent alongside the personal one when downloading from org-gated repos. |

How the request gets wired ([ModelCatalog.kt](../../app/src/main/kotlin/com/meshlit/models/ModelCatalog.kt)):

- Free user (only personal token set) → `Authorization: Bearer <personal>`
- Pro user (both tokens + org slug set) → `Authorization: Bearer <personal>` + `X-HuggingFace-Organization: <slug>` + `X-HuggingFace-Pro-Token: <org>`
- Pro-only user (only org token set, no personal) → `Authorization: Bearer <org>` + `X-HuggingFace-Organization: <slug>`

The token values themselves are **never logged**. Only `hasHfToken=true|false` and `hasHfProToken=true|false` are surfaced in `model.download.url.start`. This is a deliberate privacy contract — tokens in logs are a security incident.

---

## How the picker uses this

The v2 picker (Models screen) renders one row per entry:

```
┌─────────────────────────────────────────────────────────┐
│ ● SmolLM2-1.7B-Instruct · Q4_K_M      [Chat] [Easy]     │  ← chip + tag
│   1.1 GB · Chat · English-first · Apache 2.0            │
│   [Load] (or progress bar / [Cancel])                   │
└─────────────────────────────────────────────────────────┘
```

The chip + tag surface the entry's `category` and `ease` so the user can
filter / sort / pick by intent without reading the description. The "Load"
button is disabled (with a `MeshlitBanner` underneath) when the device
tier is too low — the user sees *why* before they tap.

The Hard rows (today: Phi-3.5 ONNX) render with an explicit
"Phase 2 — runtime not shipped yet" hint so nobody mistakes them for a
working model.