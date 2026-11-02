"""Universal Search Provider for Meshlit.

Features:
- Free by default: zero-setup DuckDuckGo search via duckduckgo_search / ddgs with direct HTTP fallback.
  No API key or registration required; returns instant titles, links, and snippets.
- Multi-vendor support with API keys: Brave, Serper, Tavily, Bing, Google CSE, Perplexity, SearXNG.
- Custom vendor support: add any API key, custom endpoint, headers, and payload formats.
- Fallback & resilience: automatically falls back to free DuckDuckGo if an upstream provider fails.
"""
from __future__ import annotations

import html
import json
import os
import re
import urllib.parse
from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Dict, List, Optional
import urllib.request
import urllib.error


class WebSearchVendor(str, Enum):
    DUCKDUCKGO = "duckduckgo"
    BRAVE = "brave"
    SERPER = "serper"
    TAVILY = "tavily"
    BING = "bing"
    GOOGLE_CSE = "google_cse"
    PERPLEXITY = "perplexity"
    SEARXNG = "searxng"
    CUSTOM = "custom"


@dataclass
class SearchResult:
    """Normalized search result row across all vendors."""
    title: str
    url: str
    snippet: str
    score: Optional[float] = None
    vendor: str = "duckduckgo"

    def to_dict(self) -> Dict[str, Any]:
        d: Dict[str, Any] = {
            "title": self.title,
            "url": self.url,
            "snippet": self.snippet,
            "vendor": self.vendor,
        }
        if self.score is not None:
            d["score"] = self.score
        return d


def _strip_html(text: str) -> str:
    """Clean HTML tags and unescape entities."""
    if not text:
        return ""
    clean = re.sub(r"<[^>]+>", "", text)
    return html.unescape(clean).strip()


def _is_safe_url(url: str) -> bool:
    """Validate that the URL is public HTTP/HTTPS with no credentials."""
    try:
        parsed = urllib.parse.urlparse(url)
        return (
            parsed.scheme in ("http", "https")
            and bool(parsed.netloc)
            and parsed.username is None
            and len(url) <= 4096
        )
    except Exception:
        return False


class SearchUnavailable(RuntimeError):
    """The selected provider could not supply a search response."""


class DuckDuckGoProvider:
    """Zero-setup, completely free DuckDuckGo search provider.

    Uses the duckduckgo_search / ddgs Python library when available,
    falling back to direct HTTP queries. No API key or registration required.
    """

    def __init__(self, timeout: int = 15):
        self.timeout = timeout

    def search(self, query: str, k: int = 5) -> List[SearchResult]:
        query = query.strip()
        if not query:
            return []
        k = max(1, min(k, 25))

        # 1. Try python library (ddgs or duckduckgo_search)
        results = self._search_via_library(query, k)
        if results:
            return results

        # 2. Fallback to direct HTTP endpoint
        return self._search_via_http(query, k)

    def _search_via_library(self, query: str, k: int) -> List[SearchResult]:
        # Try ddgs first, then duckduckgo_search
        ddgs_cls = None
        try:
            from ddgs import DDGS
            ddgs_cls = DDGS
        except ImportError:
            try:
                from duckduckgo_search import DDGS
                ddgs_cls = DDGS
            except ImportError:
                pass

        if ddgs_cls is None:
            return []

        try:
            with ddgs_cls() as ddgs:
                raw_rows = ddgs.text(query, max_results=k)
            results: List[SearchResult] = []
            for item in raw_rows or []:
                url = item.get("href") or item.get("url") or ""
                title = _strip_html(item.get("title") or "")
                snippet = _strip_html(item.get("body") or item.get("snippet") or "")
                if url and title and _is_safe_url(url):
                    results.append(
                        SearchResult(
                            title=title[:300],
                            url=url,
                            snippet=snippet[:1500],
                            vendor=WebSearchVendor.DUCKDUCKGO.value,
                        )
                    )
            return results[:k]
        except Exception:
            return []

    def _search_via_http(self, query: str, k: int) -> List[SearchResult]:
        url = "https://html.duckduckgo.com/html/"
        data = urllib.parse.urlencode({"q": query, "b": ""}).encode("utf-8")
        headers = {
            "User-Agent": (
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
            ),
            "Content-Type": "application/x-www-form-urlencoded",
            "Accept": "text/html,application/xhtml+xml",
        }
        req = urllib.request.Request(url, data=data, headers=headers, method="POST")
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                content = resp.read(1048577).decode("utf-8", errors="replace")
                if resp.status == 202 or re.search(r"challenge-form|anomaly-modal|captcha", content, re.IGNORECASE):
                    raise SearchUnavailable("DuckDuckGo requires human verification; search is unavailable")
        except SearchUnavailable:
            raise
        except Exception:
            raise SearchUnavailable("DuckDuckGo request failed") from None

        # Parse links and snippets from HTML
        results: List[SearchResult] = []
        # Pattern matches result snippets in DuckDuckGo HTML
        blocks = re.findall(
            r'<a[^>]*class="[^"]*result__snippet[^"]*"[^>]*href="([^"]+)"[^>]*>(.*?)</a>',
            content,
            re.DOTALL | re.IGNORECASE,
        )
        if not blocks:
            # Alternate pattern matching result title links
            links = re.findall(
                r'<a[^>]*class="[^"]*result__url[^"]*"[^>]*href="([^"]+)"[^>]*>(.*?)</a>',
                content,
                re.DOTALL | re.IGNORECASE,
            )
            for href, text in links[:k]:
                clean_url = urllib.parse.unquote(href)
                if "uddg=" in clean_url:
                    clean_url = clean_url.split("uddg=")[-1].split("&")[0]
                if _is_safe_url(clean_url):
                    results.append(
                        SearchResult(
                            title=_strip_html(text) or clean_url,
                            url=clean_url,
                            snippet=_strip_html(text),
                            vendor=WebSearchVendor.DUCKDUCKGO.value,
                        )
                    )
            return results[:k]

        for href, snippet_html in blocks[:k]:
            clean_url = urllib.parse.unquote(href)
            if "uddg=" in clean_url:
                clean_url = clean_url.split("uddg=")[-1].split("&")[0]
            if not _is_safe_url(clean_url):
                continue
            snippet = _strip_html(snippet_html)
            results.append(
                SearchResult(
                    title=snippet[:80] + ("..." if len(snippet) > 80 else ""),
                    url=clean_url,
                    snippet=snippet[:1500],
                    vendor=WebSearchVendor.DUCKDUCKGO.value,
                )
            )
        return results[:k]


class CustomSearchProvider:
    """Configurable provider for custom search endpoints or arbitrary vendors.

    Supports custom HTTP methods, headers, authentication prefixes,
    query parameter names, and flexible response JSON extraction.
    """

    def __init__(
        self,
        endpoint: str,
        api_key: Optional[str] = None,
        header_name: str = "Authorization",
        auth_prefix: str = "Bearer ",
        method: str = "GET",
        query_param: str = "q",
        count_param: str = "k",
        custom_headers: Optional[Dict[str, str]] = None,
        results_path: Optional[str] = None,
        title_key: str = "title",
        url_key: str = "url",
        snippet_key: str = "snippet",
        timeout: int = 15,
        vendor_name: str = "custom",
    ):
        self.endpoint = endpoint.rstrip("/")
        self.api_key = api_key
        self.header_name = header_name
        self.auth_prefix = auth_prefix
        self.method = method.upper()
        self.query_param = query_param
        self.count_param = count_param
        self.custom_headers = custom_headers or {}
        self.results_path = results_path
        self.title_key = title_key
        self.url_key = url_key
        self.snippet_key = snippet_key
        self.timeout = timeout
        self.vendor_name = vendor_name

    def search(self, query: str, k: int = 5) -> List[SearchResult]:
        query = query.strip()
        if not query:
            return []

        headers = {
            "Accept": "application/json",
            "User-Agent": "MeshlitUniversalSearch/2.0",
        }
        headers.update(self.custom_headers)
        if self.api_key:
            auth_val = f"{self.auth_prefix}{self.api_key}".strip()
            headers[self.header_name] = auth_val

        if self.method == "POST":
            headers["Content-Type"] = "application/json"
            payload = {
                self.query_param: query,
                self.count_param: k,
            }
            data = json.dumps(payload).encode("utf-8")
            req = urllib.request.Request(self.endpoint, data=data, headers=headers, method="POST")
        else:
            sep = "&" if "?" in self.endpoint else "?"
            params = urllib.parse.urlencode({self.query_param: query, self.count_param: k})
            url = f"{self.endpoint}{sep}{params}"
            req = urllib.request.Request(url, headers=headers, method="GET")

        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                if resp.status >= 400:
                    return []
                body = resp.read().decode("utf-8", errors="replace")
                data = json.loads(body)
                return self._parse_json(data, k)
        except Exception:
            return []

    def _parse_json(self, data: Any, k: int) -> List[SearchResult]:
        items: List[Any] = []
        if isinstance(data, list):
            items = data
        elif isinstance(data, dict):
            if self.results_path and self.results_path in data:
                val = data[self.results_path]
                if isinstance(val, list):
                    items = val
            else:
                for candidate in ("results", "organic", "items", "webPages", "web"):
                    if candidate in data:
                        val = data[candidate]
                        if isinstance(val, list):
                            items = val
                            break
                        elif isinstance(val, dict):
                            for sub in ("results", "value"):
                                if sub in val and isinstance(val[sub], list):
                                    items = val[sub]
                                    break
                            if items:
                                break

        results: List[SearchResult] = []
        for item in items[:k]:
            if not isinstance(item, dict):
                continue
            title = str(
                item.get(self.title_key)
                or item.get("name")
                or item.get("title")
                or ""
            )
            url = str(
                item.get(self.url_key)
                or item.get("link")
                or item.get("href")
                or item.get("url")
                or ""
            )
            snippet = str(
                item.get(self.snippet_key)
                or item.get("description")
                or item.get("content")
                or item.get("body")
                or ""
            )
            score = item.get("score")
            if score is not None:
                try:
                    score = float(score)
                except (ValueError, TypeError):
                    score = None

            if url and _is_safe_url(url):
                results.append(
                    SearchResult(
                        title=_strip_html(title)[:300] or url,
                        url=url,
                        snippet=_strip_html(snippet)[:1500],
                        score=score,
                        vendor=self.vendor_name,
                    )
                )
        return results[:k]


class UniversalSearchProvider:
    """Universal search manager for Meshlit.

    - Default: DuckDuckGo free search (zero-setup, no API key).
    - Multi-vendor API key registration: Brave, Serper, Tavily, Bing, Google CSE, Perplexity, SearXNG.
    - Custom providers: add any custom endpoint and API key.
    - Fallback: if configured vendor fails, automatically falls back to DuckDuckGo.
    """

    def __init__(self, default_vendor: WebSearchVendor = WebSearchVendor.DUCKDUCKGO):
        self.default_vendor = default_vendor
        self._duckduckgo = DuckDuckGoProvider()
        self._keys: Dict[str, str] = {}
        self._custom_providers: Dict[str, CustomSearchProvider] = {}
        self._extra_configs: Dict[str, Dict[str, Any]] = {}
        self._load_from_environment()

    def _load_from_environment(self) -> None:
        """Load API keys from environment if present."""
        env_map = {
            "MESHLIT_SEARCH_BRAVE_KEY": WebSearchVendor.BRAVE.value,
            "BRAVE_SEARCH_API_KEY": WebSearchVendor.BRAVE.value,
            "MESHLIT_SEARCH_SERPER_KEY": WebSearchVendor.SERPER.value,
            "SERPER_API_KEY": WebSearchVendor.SERPER.value,
            "MESHLIT_SEARCH_TAVILY_KEY": WebSearchVendor.TAVILY.value,
            "TAVILY_API_KEY": WebSearchVendor.TAVILY.value,
            "MESHLIT_SEARCH_BING_KEY": WebSearchVendor.BING.value,
            "BING_API_KEY": WebSearchVendor.BING.value,
            "MESHLIT_SEARCH_GOOGLE_KEY": WebSearchVendor.GOOGLE_CSE.value,
            "GOOGLE_CSE_KEY": WebSearchVendor.GOOGLE_CSE.value,
            "MESHLIT_SEARCH_PERPLEXITY_KEY": WebSearchVendor.PERPLEXITY.value,
            "PERPLEXITY_API_KEY": WebSearchVendor.PERPLEXITY.value,
        }
        for env_var, vendor in env_map.items():
            val = os.environ.get(env_var)
            if val and val.strip():
                self._keys[vendor] = val.strip()

        if "GOOGLE_CSE_CX" in os.environ:
            self._extra_configs.setdefault(WebSearchVendor.GOOGLE_CSE.value, {})["cx"] = os.environ["GOOGLE_CSE_CX"].strip()

    def set_api_key(self, vendor: str | WebSearchVendor, api_key: str, **kwargs: Any) -> UniversalSearchProvider:
        """Register or update an API key for any vendor."""
        v_str = vendor.value if isinstance(vendor, WebSearchVendor) else str(vendor).lower()
        if api_key.strip():
            self._keys[v_str] = api_key.strip()
            if kwargs:
                self._extra_configs.setdefault(v_str, {}).update(kwargs)
        else:
            self._keys.pop(v_str, None)
            self._extra_configs.pop(v_str, None)
        return self

    def register_custom_provider(self, name: str, provider: CustomSearchProvider) -> UniversalSearchProvider:
        """Register a custom search provider instance."""
        self._custom_providers[name.lower()] = provider
        return self

    def add_custom_endpoint(
        self,
        name: str,
        endpoint: str,
        api_key: Optional[str] = None,
        header_name: str = "Authorization",
        auth_prefix: str = "Bearer ",
        method: str = "GET",
        query_param: str = "q",
        count_param: str = "k",
        custom_headers: Optional[Dict[str, str]] = None,
    ) -> UniversalSearchProvider:
        """Add any custom search API endpoint with optional API key."""
        provider = CustomSearchProvider(
            endpoint=endpoint,
            api_key=api_key,
            header_name=header_name,
            auth_prefix=auth_prefix,
            method=method,
            query_param=query_param,
            count_param=count_param,
            custom_headers=custom_headers,
            vendor_name=name,
        )
        return self.register_custom_provider(name, provider)

    def search(
        self,
        query: str,
        k: int = 5,
        vendor: Optional[str | WebSearchVendor] = None,
        fallback_to_duckduckgo: bool = True,
    ) -> List[SearchResult]:
        """Universal search across configured vendors, defaulting to DuckDuckGo."""
        target_vendor = vendor or self.default_vendor
        v_str = target_vendor.value if isinstance(target_vendor, WebSearchVendor) else str(target_vendor).lower()

        results: List[SearchResult] = []
        try:
            if v_str in (WebSearchVendor.DUCKDUCKGO.value, "free", "default"):
                results = self._duckduckgo.search(query, k)
            elif v_str in self._custom_providers:
                results = self._custom_providers[v_str].search(query, k)
            elif v_str == WebSearchVendor.BRAVE.value:
                results = self._dispatch_brave(query, k)
            elif v_str == WebSearchVendor.SERPER.value:
                results = self._dispatch_serper(query, k)
            elif v_str == WebSearchVendor.TAVILY.value:
                results = self._dispatch_tavily(query, k)
            elif v_str == WebSearchVendor.BING.value:
                results = self._dispatch_bing(query, k)
            elif v_str == WebSearchVendor.GOOGLE_CSE.value:
                results = self._dispatch_google_cse(query, k)
            elif v_str == WebSearchVendor.PERPLEXITY.value:
                results = self._dispatch_perplexity(query, k)
            elif v_str == WebSearchVendor.SEARXNG.value:
                results = self._dispatch_searxng(query, k)
            else:
                # Check if it's a registered custom provider
                if v_str in self._custom_providers:
                    results = self._custom_providers[v_str].search(query, k)
                else:
                    results = self._duckduckgo.search(query, k)
        except SearchUnavailable:
            raise
        except Exception:
            results = []

        if not results and fallback_to_duckduckgo and v_str != WebSearchVendor.DUCKDUCKGO.value:
            results = self._duckduckgo.search(query, k)

        return results

    def _dispatch_brave(self, query: str, k: int) -> List[SearchResult]:
        api_key = self._keys.get(WebSearchVendor.BRAVE.value)
        if not api_key:
            return []
        p = CustomSearchProvider(
            endpoint="https://api.search.brave.com/res/v1/web/search",
            api_key=api_key,
            header_name="X-Subscription-Token",
            auth_prefix="",
            query_param="q",
            count_param="count",
            results_path="web",
            title_key="title",
            url_key="url",
            snippet_key="description",
            vendor_name=WebSearchVendor.BRAVE.value,
        )
        return p.search(query, k)

    def _dispatch_serper(self, query: str, k: int) -> List[SearchResult]:
        api_key = self._keys.get(WebSearchVendor.SERPER.value)
        if not api_key:
            return []
        p = CustomSearchProvider(
            endpoint="https://google.serper.dev/search",
            api_key=api_key,
            header_name="X-API-KEY",
            auth_prefix="",
            method="POST",
            query_param="q",
            count_param="n",
            results_path="organic",
            title_key="title",
            url_key="link",
            snippet_key="snippet",
            vendor_name=WebSearchVendor.SERPER.value,
        )
        return p.search(query, k)

    def _dispatch_tavily(self, query: str, k: int) -> List[SearchResult]:
        api_key = self._keys.get(WebSearchVendor.TAVILY.value)
        if not api_key:
            return []
        p = CustomSearchProvider(
            endpoint="https://api.tavily.com/search",
            api_key=api_key,
            header_name="Authorization",
            auth_prefix="Bearer ",
            method="POST",
            query_param="query",
            count_param="max_results",
            results_path="results",
            title_key="title",
            url_key="url",
            snippet_key="content",
            vendor_name=WebSearchVendor.TAVILY.value,
        )
        return p.search(query, k)

    def _dispatch_bing(self, query: str, k: int) -> List[SearchResult]:
        api_key = self._keys.get(WebSearchVendor.BING.value)
        if not api_key:
            return []
        endpoint = self._extra_configs.get(WebSearchVendor.BING.value, {}).get(
            "endpoint", "https://api.bing.microsoft.com/v7.0/search"
        )
        p = CustomSearchProvider(
            endpoint=endpoint,
            api_key=api_key,
            header_name="Ocp-Apim-Subscription-Key",
            auth_prefix="",
            query_param="q",
            count_param="count",
            results_path="webPages",
            title_key="name",
            url_key="url",
            snippet_key="snippet",
            vendor_name=WebSearchVendor.BING.value,
        )
        return p.search(query, k)

    def _dispatch_google_cse(self, query: str, k: int) -> List[SearchResult]:
        api_key = self._keys.get(WebSearchVendor.GOOGLE_CSE.value)
        cx = self._extra_configs.get(WebSearchVendor.GOOGLE_CSE.value, {}).get("cx")
        if not api_key or not cx:
            return []
        endpoint = f"https://www.googleapis.com/customsearch/v1?key={urllib.parse.quote(api_key)}&cx={urllib.parse.quote(cx)}"
        p = CustomSearchProvider(
            endpoint=endpoint,
            query_param="q",
            count_param="num",
            results_path="items",
            title_key="title",
            url_key="link",
            snippet_key="snippet",
            vendor_name=WebSearchVendor.GOOGLE_CSE.value,
        )
        return p.search(query, k)

    def _dispatch_perplexity(self, query: str, k: int) -> List[SearchResult]:
        api_key = self._keys.get(WebSearchVendor.PERPLEXITY.value)
        if not api_key:
            return []
        endpoint = self._extra_configs.get(WebSearchVendor.PERPLEXITY.value, {}).get(
            "endpoint", "https://api.perplexity.ai/chat/completions"
        )
        headers = {
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        }
        body = {
            "model": "sonar",
            "messages": [
                {"role": "system", "content": "You are a concise search assistant. Provide factual search results with title and URL sources."},
                {"role": "user", "content": query},
            ],
            "max_tokens": 500,
        }
        try:
            req = urllib.request.Request(endpoint, data=json.dumps(body).encode("utf-8"), headers=headers, method="POST")
            with urllib.request.urlopen(req, timeout=15) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                citations = data.get("citations", [])
                content = data.get("choices", [{}])[0].get("message", {}).get("content", "")
                results: List[SearchResult] = []
                for i, url in enumerate(citations[:k]):
                    if _is_safe_url(url):
                        results.append(
                            SearchResult(
                                title=f"Source {i+1}",
                                url=url,
                                snippet=content[:500],
                                vendor=WebSearchVendor.PERPLEXITY.value,
                            )
                        )
                if not results and citations:
                    return results
                if not results:
                    results.append(SearchResult(title=query, url="https://perplexity.ai", snippet=content[:1000], vendor=WebSearchVendor.PERPLEXITY.value))
                return results
        except Exception:
            return []

    def _dispatch_searxng(self, query: str, k: int) -> List[SearchResult]:
        endpoint = self._extra_configs.get(WebSearchVendor.SEARXNG.value, {}).get(
            "endpoint", "https://searx.be/search"
        )
        api_key = self._keys.get(WebSearchVendor.SEARXNG.value)
        p = CustomSearchProvider(
            endpoint=endpoint,
            api_key=api_key,
            query_param="q",
            custom_headers={"Accept": "application/json"},
            results_path="results",
            title_key="title",
            url_key="url",
            snippet_key="content",
            vendor_name=WebSearchVendor.SEARXNG.value,
        )
        return p.search(query, k)


# Module-level default instance for zero-setup convenience:
_default_instance = UniversalSearchProvider()


def search(
    query: str,
    k: int = 5,
    vendor: Optional[str | WebSearchVendor] = None,
    fallback: bool = True,
) -> List[SearchResult]:
    """Instant search using Meshlit's universal search provider.

    By default, uses DuckDuckGo with zero setup and no API key required.
    """
    return _default_instance.search(query, k=k, vendor=vendor, fallback_to_duckduckgo=fallback)


if __name__ == "__main__":
    import argparse
    import sys

    parser = argparse.ArgumentParser(description="Meshlit Universal Search Provider CLI")
    parser.add_argument("query", help="Search query string")
    parser.add_argument("--k", "-k", type=int, default=5, help="Number of results to return")
    parser.add_argument("--vendor", "-v", default="duckduckgo", help="Vendor (duckduckgo, brave, serper, tavily, custom, etc.)")
    parser.add_argument("--key", help="API key for chosen vendor")
    parser.add_argument("--endpoint", help="Custom endpoint URL")
    args = parser.parse_args()

    provider = UniversalSearchProvider()
    if args.key:
        provider.set_api_key(args.vendor, args.key)
    if args.endpoint:
        provider.add_custom_endpoint(args.vendor, args.endpoint, api_key=args.key)

    rows = provider.search(args.query, k=args.k, vendor=args.vendor)
    print(f"Meshlit Search [{args.vendor}]: Found {len(rows)} results for '{args.query}':\n")
    for idx, r in enumerate(rows, 1):
        print(f"{idx}. {r.title}")
        print(f"   URL: {r.url}")
        print(f"   Snippet: {r.snippet[:200]}")
        print()
