# Meshlit Universal Search Provider Companion
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-11-03: [Plan](../../PLAN.md) · [Progress](../../PROGRESS.md) · [Document status](../../docs/DOCUMENTATION_STATUS.md).
Scope: optional host/schema/reference; not a bundled qualified service. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Universal Search Provider with zero-setup free search and multi-vendor API key support.

## Features

- **DuckDuckGo (Free / Zero Setup)**:
  - Powered by `duckduckgo_search` / `ddgs` Python library.
  - No API key is required. Provider verification challenges can block live search.
  - Returns instant titles, canonical links, and text snippets.
  - Built-in HTTP fallback.
- **Universal Multi-Vendor Search**:
  - Add any API key for:
    - **Brave Search** (`X-Subscription-Token`)
    - **Serper.dev** (`X-API-KEY`)
    - **Tavily** (`Authorization: Bearer <key>`)
    - **Bing Web Search** (`Ocp-Apim-Subscription-Key`)
    - **Google Programmable Search (CSE)** (`key` + `cx`)
    - **Perplexity Sonar** (`Bearer <key>`)
    - **SearXNG**
- **Custom Search Providers**:
  - Add any custom endpoint URL (GET or POST).
  - Configure custom header names (e.g., `X-Custom-Key`, `Authorization`).
  - Configure authentication prefixes (`Bearer `, `Token `, etc.).
  - Configurable query parameter and response JSON mapping.
- **Automatic Fallback & Resilience**:
  - If a configured third-party vendor quota is exceeded or fails, automatically falls back to DuckDuckGo when available. Provider failures or verification challenges can still prevent search.

## Usage

### In Python Code

```python
from companions.search import UniversalSearchProvider, search

# 1. Zero-setup instant search (free DuckDuckGo):
results = search("meshlit agent framework", k=5)
for r in results:
    print(r.title, r.url, r.snippet)

# 2. Add API key for any vendor:
provider = UniversalSearchProvider()
provider.set_api_key("brave", "YOUR_BRAVE_KEY")
provider.set_api_key("tavily", "YOUR_TAVILY_KEY")

# 3. Add any custom search endpoint with any API key:
provider.add_custom_endpoint(
    name="my_internal_search",
    endpoint="https://search.internal.net/api/v1/query",
    api_key="internal_secret_token",
    header_name="X-Internal-Token",
)

results = provider.search("query", vendor="my_internal_search")
```

### CLI

```bash
# Default zero-setup search:
python3 -m companions.search.universal_search "python async" --k 3

# With custom key:
python3 -m companions.search.universal_search "quantum computing" --vendor brave --key "YOUR_KEY"
```
