import asyncio
from pathlib import Path
import socket
import sys
from types import SimpleNamespace
import unittest
from unittest.mock import AsyncMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fastapi.testclient import TestClient
from policy import CrawlPolicy, PolicyError, StrictRobots
from service import create_app, normalize_result

TOKEN = "test-token-" + "x" * 32
HEADERS = {"Authorization": "Bearer " + TOKEN}


class PolicyTests(unittest.IsolatedAsyncioTestCase):
    def test_rejects_unapproved_schemes_credentials_ports_and_scope(self):
        policy = CrawlPolicy("example.com")
        for url in ("http://example.com", "file:///etc/passwd", "https://evil.example.com",
                    "https://example.com.evil.org", "https://user:pass@example.com",
                    "https://example.com:8443", "https://example.com\\@evil.org"):
            with self.assertRaises(PolicyError):
                policy.normalize(url)
        self.assertEqual("https://example.com/a?q=b", policy.normalize("https://example.com/a?q=b#fragment"))

    async def test_rejects_private_dns_and_mixed_answers(self):
        policy = CrawlPolicy("example.com")
        for ips in (("127.0.0.1",), ("169.254.169.254",), ("10.0.0.1",),
                    ("93.184.216.34", "192.168.1.1"), ("::1",)):
            records = [(socket.AF_INET, socket.SOCK_STREAM, 6, "", (ip, 443)) for ip in ips]
            with patch.object(asyncio.get_running_loop(), "getaddrinfo", AsyncMock(return_value=records)):
                with self.assertRaises(PolicyError):
                    await policy.validate("https://example.com/")

    async def test_robots_forbidden_redirect_and_missing_file(self):
        import httpx
        policy = CrawlPolicy("example.com")
        validate = AsyncMock(side_effect=lambda url: url)
        for status, body, expected in (
            (200, "User-agent: *\nDisallow: /private\n", False),
            (403, "", False), (302, "", False), (503, "", False), (404, "", True),
        ):
            transport = httpx.MockTransport(lambda request: httpx.Response(status, text=body))
            real_client = httpx.AsyncClient
            with patch.object(policy, "validate", validate), patch(
                "httpx.AsyncClient", side_effect=lambda **kwargs: real_client(transport=transport, **kwargs)
            ):
                self.assertEqual(expected, await StrictRobots(policy).can_fetch("https://example.com/private"))


class ServiceTests(unittest.TestCase):
    def setUp(self):
        self.engine = SimpleNamespace(crawl=AsyncMock(return_value={"state": "ok", "markdown": "hi"}))
        self.client = TestClient(create_app(TOKEN, "example.com", self.engine))

    def test_authentication_and_unknown_options(self):
        self.assertEqual(401, self.client.post("/crawl", json={"url": "https://example.com"}).status_code)
        self.assertEqual(401, self.client.get("/health").status_code)
        self.assertEqual(422, self.client.post("/crawl", headers=HEADERS,
            json={"url": "https://example.com", "stealth": True}).status_code)
        self.engine.crawl.assert_not_awaited()

    def test_crawl_success_and_policy_denial(self):
        with patch("policy.CrawlPolicy.validate", AsyncMock(return_value="https://example.com/")):
            response = self.client.post("/crawl", headers=HEADERS, json={"url": "https://example.com"})
            self.assertEqual(200, response.status_code)
            self.assertEqual("ok", response.json()["state"])
        with patch("policy.CrawlPolicy.validate", AsyncMock(side_effect=PolicyError("denied"))):
            response = self.client.post("/crawl", headers=HEADERS, json={"url": "https://example.com"})
            self.assertEqual(403, response.status_code)
        self.assertEqual(1, self.engine.crawl.await_count)

    def test_engine_failure_is_redacted(self):
        self.engine.crawl.side_effect = RuntimeError("secret upstream URL")
        with patch("policy.CrawlPolicy.validate", AsyncMock(return_value="https://example.com/")):
            response = self.client.post("/crawl", headers=HEADERS, json={"url": "https://example.com"})
            self.assertEqual(502, response.status_code)
            self.assertNotIn("secret", response.text)

    def test_timeout_is_explicit(self):
        self.engine.crawl.side_effect = TimeoutError()
        with patch("policy.CrawlPolicy.validate", AsyncMock(return_value="https://example.com/")):
            response = self.client.post("/crawl", headers=HEADERS, json={"url": "https://example.com"})
            self.assertEqual(504, response.status_code)

    def test_result_has_provenance_and_unicode_limit(self):
        result = SimpleNamespace(status_code=200, success=True, markdown="🙂" * 300,
                                 redirected_url=None, url="https://example.com/", metadata={"title": "Title"})
        response = normalize_result(result, result.url, 256)
        self.assertEqual(256, len(response["markdown"]))
        self.assertTrue(response["truncated"])
        self.assertTrue(response["untrusted_content"])
        for status, expected in ((401, "blocked"), (403, "blocked"), (429, "rate_limited"), (500, "failed")):
            result.status_code = status
            response = normalize_result(result, result.url, 256)
            self.assertEqual(expected, response["state"])
            self.assertEqual("", response["markdown"])

    def test_invalid_limits_and_configuration(self):
        for limit in (0, 100001, "3000", True):
            self.assertEqual(422, self.client.post("/crawl", headers=HEADERS,
                json={"url": "https://example.com/", "max_chars": limit}).status_code)
        for token, domains in (("short", "example.com"), (TOKEN, ""), (TOKEN, "*.example.com")):
            with self.assertRaises(ValueError):
                create_app(token, domains)


if __name__ == "__main__":
    unittest.main()
