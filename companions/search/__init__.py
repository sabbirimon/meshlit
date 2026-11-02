"""Universal Search Provider for Meshlit.
Zero-setup free search with DuckDuckGo by default, plus multi-vendor and custom API key support.
"""
from .universal_search import (
    UniversalSearchProvider,
    SearchResult,
    WebSearchVendor,
    DuckDuckGoProvider,
    CustomSearchProvider,
    search,
)

__all__ = [
    "UniversalSearchProvider",
    "SearchResult",
    "WebSearchVendor",
    "DuckDuckGoProvider",
    "CustomSearchProvider",
    "search",
]
