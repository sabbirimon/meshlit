"""Meshlit Universal Search Companion Service.

Exposes a lightweight REST and MCP-compatible API for agents and companions.
Defaults to DuckDuckGo (zero setup), with support for multi-vendor and custom endpoints.
"""
from __future__ import annotations

import os
from typing import Any, Dict, List, Optional
from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from .universal_search import UniversalSearchProvider, WebSearchVendor, SearchUnavailable


class SearchRequest(BaseModel):
    query: str = Field(min_length=1, max_length=600)
    k: int = Field(default=5, ge=1, le=25)
    vendor: Optional[str] = None
    api_key: Optional[str] = None
    custom_endpoint: Optional[str] = None


class SearchResponseRow(BaseModel):
    title: str
    url: str
    snippet: str
    score: Optional[float] = None
    vendor: str


class SearchResponse(BaseModel):
    ok: bool = True
    provider: str
    count: int
    results: List[SearchResponseRow]
    error: Optional[str] = None


def create_app(provider: Optional[UniversalSearchProvider] = None) -> FastAPI:
    app = FastAPI(title="Meshlit Universal Search API", version="2.0.0")
    engine = provider or UniversalSearchProvider()

    @app.exception_handler(SearchUnavailable)
    async def search_unavailable(request, exc):
        return JSONResponse(status_code=503, content={"ok": False, "error": str(exc)})

    @app.get("/health")
    def health():
        return {
            "status": "ok",
            "service": "meshlit-search",
            "default_vendor": "duckduckgo",
            "zero_setup": True,
        }

    @app.get("/vendors")
    def list_vendors():
        return {
            "vendors": [v.value for v in WebSearchVendor],
            "default": "duckduckgo",
            "note": "DuckDuckGo requires zero setup and no API key. Other vendors accept any user API key.",
        }

    @app.get("/search", response_model=SearchResponse)
    def search_get(
        q: str = Query(..., min_length=1, max_length=600),
        k: int = Query(5, ge=1, le=25),
        vendor: Optional[str] = Query(None),
    ):
        rows = engine.search(q, k=k, vendor=vendor)
        return SearchResponse(
            ok=True,
            provider=vendor or "duckduckgo",
            count=len(rows),
            results=[
                SearchResponseRow(
                    title=r.title,
                    url=r.url,
                    snippet=r.snippet,
                    score=r.score,
                    vendor=r.vendor,
                )
                for r in rows
            ],
        )

    @app.post("/search", response_model=SearchResponse)
    def search_post(req: SearchRequest):
        search_engine = engine
        if req.api_key and req.vendor:
            search_engine = UniversalSearchProvider()
            search_engine.set_api_key(req.vendor, req.api_key)
        if req.custom_endpoint and req.vendor:
            search_engine.add_custom_endpoint(req.vendor, req.custom_endpoint, api_key=req.api_key)

        rows = search_engine.search(req.query, k=req.k, vendor=req.vendor)
        return SearchResponse(
            ok=True,
            provider=req.vendor or "duckduckgo",
            count=len(rows),
            results=[
                SearchResponseRow(
                    title=r.title,
                    url=r.url,
                    snippet=r.snippet,
                    score=r.score,
                    vendor=r.vendor,
                )
                for r in rows
            ],
        )

    return app


app = create_app()
