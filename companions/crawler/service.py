"""Private, opt-in Crawl4AI companion. No stealth or access-control bypass."""
import asyncio
from datetime import datetime, timezone
import hmac
import os
import time
from urllib.parse import urlsplit

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, ConfigDict, Field

from policy import CrawlPolicy, PolicyError, StrictRobots, USER_AGENT


class CrawlRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    url: str = Field(min_length=1, max_length=4096)
    max_chars: int = Field(default=32000, ge=256, le=100000)


def normalize_result(result, requested_url: str, max_chars: int) -> dict:
    status = result.status_code or 0
    if status in (401, 403, 429):
        state = "rate_limited" if status == 429 else "blocked"
    elif result.success and 200 <= status < 300:
        state = "ok"
    else:
        state = "failed"
    markdown = getattr(result.markdown, "raw_markdown", result.markdown) or ""
    if state != "ok":
        markdown = ""
    return {
        "state": state,
        "status": status,
        "url": requested_url,
        "final_url": result.redirected_url or result.url or requested_url,
        "title": str((result.metadata or {}).get("title", ""))[:512],
        "markdown": markdown[:max_chars],
        "truncated": len(markdown) > max_chars,
        "retrieved_at": datetime.now(timezone.utc).isoformat(),
        "untrusted_content": True,
        "message": {
            "ok": "Retrieved public page content",
            "blocked": "Access denied; use an approved API, owner allowlist or user-provided content",
            "rate_limited": "Site rate limit reached; retry later",
            "failed": "Page retrieval failed",
        }[state],
    }


class CrawlEngine:
    def __init__(self, policy: CrawlPolicy):
        self.policy = policy

    async def crawl(self, url: str, max_chars: int) -> dict:
        # Lazy import keeps policy/API tests independent from Chromium.
        from crawl4ai import AsyncWebCrawler, BrowserConfig, CacheMode, CrawlerRunConfig

        config = BrowserConfig(
            headless=True, user_agent=USER_AGENT, enable_stealth=False,
            use_persistent_context=False, verbose=False,
        )
        run = CrawlerRunConfig(
            check_robots_txt=True, cache_mode=CacheMode.DISABLED,
            page_timeout=25000, wait_until="domcontentloaded", verbose=False,
            magic=False, simulate_user=False, override_navigator=False,
            max_retries=0,
        )
        async with AsyncWebCrawler(config=config) as crawler:
            robots = StrictRobots(self.policy)
            crawler.robots_parser = robots
            async def guard_page(page, context, **kwargs):
                # Context-level routing covers redirects and popup requests.
                async def guard_request(route):
                    try:
                        target = await self.policy.validate(route.request.url)
                        if route.request.is_navigation_request():
                            if not await robots.can_fetch(target):
                                raise PolicyError("Navigation denied by robots rules")
                            await robots.wait_for_delay(target)
                    except PolicyError:
                        await route.abort("blockedbyclient")
                    else:
                        if route.request.method != "GET":
                            await route.abort("blockedbyclient")
                        else:
                            await route.continue_()
                await context.route("**/*", guard_request)
                return page

            crawler.crawler_strategy.set_hook("on_page_context_created", guard_page)
            result = await crawler.arun(url=url, config=run)
            if result.redirected_url:
                await self.policy.validate(result.redirected_url)
            return normalize_result(result, url, max_chars)


def create_app(token: str, domains: str, engine=None) -> FastAPI:
    if len(token) < 32 or not token.isascii() or any(char.isspace() for char in token):
        raise ValueError("MESHLIT_CRAWL_TOKEN requires at least 32 ASCII characters without whitespace")
    policy = CrawlPolicy(domains)
    engine = engine or CrawlEngine(policy)
    slot = asyncio.Semaphore(1)
    last_request = {}

    async def authorize(authorization: str = Header(default="")):
        if not hmac.compare_digest(authorization.encode(), ("Bearer " + token).encode()):
            raise HTTPException(status_code=401, detail="Invalid crawler credential")

    app = FastAPI(title="Meshlit crawler", docs_url=None, redoc_url=None, openapi_url=None)

    @app.get("/health", dependencies=[Depends(authorize)])
    async def health():
        return {"status": "ok", "engine": "crawl4ai"}

    @app.post("/crawl", dependencies=[Depends(authorize)])
    async def crawl(request: CrawlRequest):
        try:
            # Total deadline includes queue, DNS, rate delay and browser lifecycle.
            async with asyncio.timeout(45):
                async with slot:
                    url = await policy.validate(request.url)
                    host = urlsplit(url).hostname
                    delay = max(0, 2 - (time.monotonic() - last_request.get(host, 0)))
                    await asyncio.sleep(delay)
                    last_request[host] = time.monotonic()
                    return await engine.crawl(url, request.max_chars)
        except PolicyError as exc:
            raise HTTPException(status_code=403, detail=str(exc)) from exc
        except TimeoutError as exc:
            raise HTTPException(status_code=504, detail="Crawler deadline exceeded") from exc
        except Exception as exc:
            # Do not return/log upstream exception text containing URLs or cookies.
            raise HTTPException(status_code=502, detail="Crawler engine failed") from exc

    return app


def from_env() -> FastAPI:
    return create_app(os.environ.get("MESHLIT_CRAWL_TOKEN", ""),
                      os.environ.get("MESHLIT_CRAWL_DOMAINS", ""))
