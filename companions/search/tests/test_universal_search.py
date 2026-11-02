"""Isolated search contracts: mocked DDG and real loopback custom endpoints.

These tests do not assert current live-provider availability.
"""
import json
import unittest
from unittest.mock import patch, MagicMock
from http.server import BaseHTTPRequestHandler, HTTPServer
import threading

from companions.search.universal_search import (
    UniversalSearchProvider,
    SearchResult,
    WebSearchVendor,
    CustomSearchProvider,
    DuckDuckGoProvider,
    SearchUnavailable,
)


class MockCustomSearchHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass

    def do_GET(self):
        auth = self.headers.get("Authorization", "")
        if "secret-key" not in auth:
            self.send_response(401)
            self.end_headers()
            return

        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        body = {
            "results": [
                {
                    "title": "Custom Result 1",
                    "url": "https://custom.example/1",
                    "snippet": "Custom snippet 1",
                    "score": 0.98,
                }
            ]
        }
        self.wfile.write(json.dumps(body).encode("utf-8"))

    def do_POST(self):
        content_len = int(self.headers.get("Content-Length", 0))
        post_body = self.rfile.read(content_len).decode("utf-8")
        req_json = json.loads(post_body)

        api_key = self.headers.get("X-API-KEY", "")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        body = {
            "organic": [
                {
                    "title": f"Post Result for {req_json.get('q')}",
                    "link": "https://post.example/item",
                    "snippet": f"Key used was: {bool(api_key)}",
                }
            ]
        }
        self.wfile.write(json.dumps(body).encode("utf-8"))


class TestUniversalSearch(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = HTTPServer(("127.0.0.1", 0), MockCustomSearchHandler)
        cls.port = cls.server.server_address[1]
        cls.thread = threading.Thread(target=cls.server.serve_forever)
        cls.thread.daemon = True
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()

    def test_challenge_is_unavailable_not_empty_search(self):
        response = MagicMock()
        response.__enter__.return_value = response
        response.status = 202
        response.read.return_value = b'<form id="challenge-form">Verification required</form>'
        with patch('urllib.request.urlopen', return_value=response):
            with self.assertRaisesRegex(SearchUnavailable, 'human verification'):
                DuckDuckGoProvider()._search_via_http('test', 2)

    def test_duckduckgo_zero_setup(self):
        ddg = DuckDuckGoProvider()
        response = MagicMock()
        response.__enter__.return_value = response
        response.status = 200
        response.read.return_value = b'<a class="result__snippet" href="https://example.com/python">Python &amp; programming</a>'
        with patch.object(ddg, '_search_via_library', return_value=[]), patch('urllib.request.urlopen', return_value=response) as request:
            results = ddg.search("python programming", k=2)
        self.assertIsNone(request.call_args.args[0].get_header('Authorization'))
        self.assertIn(b'q=python+programming', request.call_args.args[0].data)
        self.assertTrue(len(results) > 0)
        self.assertTrue(results[0].title)
        self.assertTrue(results[0].url.startswith("http"))
        self.assertTrue(results[0].snippet)
        self.assertEqual(results[0].vendor, "duckduckgo")

    def test_universal_search_default_is_duckduckgo(self):
        provider = UniversalSearchProvider()
        fixture = [SearchResult(title="Fixture", url="https://example.com", snippet="Fixture result", vendor="duckduckgo")]
        with patch.object(DuckDuckGoProvider, 'search', return_value=fixture) as ddg:
            results = provider.search("linux kernel", k=2)
        ddg.assert_called_once_with("linux kernel", 2)
        self.assertTrue(len(results) > 0)
        self.assertEqual(results[0].vendor, "duckduckgo")

    def test_default_search_preserves_human_verification_failure(self):
        with patch.object(DuckDuckGoProvider, 'search', side_effect=SearchUnavailable('human verification')):
            with self.assertRaisesRegex(SearchUnavailable, 'human verification'):
                UniversalSearchProvider().search('fixture query')

    def test_custom_provider_with_api_key_and_headers(self):
        provider = UniversalSearchProvider()
        endpoint = f"http://127.0.0.1:{self.port}/search"
        provider.add_custom_endpoint(
            name="my-service",
            endpoint=endpoint,
            api_key="secret-key",
            header_name="Authorization",
            auth_prefix="Bearer ",
        )

        results = provider.search("test query", k=1, vendor="my-service")
        self.assertEqual(len(results), 1)
        self.assertEqual(results[0].title, "Custom Result 1")
        self.assertEqual(results[0].url, "https://custom.example/1")
        self.assertEqual(results[0].vendor, "my-service")
        self.assertAlmostEqual(results[0].score, 0.98, places=2)

    def test_custom_post_provider(self):
        provider = UniversalSearchProvider()
        endpoint = f"http://127.0.0.1:{self.port}/post_search"
        custom = CustomSearchProvider(
            endpoint=endpoint,
            api_key="custom-post-key",
            header_name="X-API-KEY",
            auth_prefix="",
            method="POST",
            query_param="q",
            count_param="n",
            results_path="organic",
            title_key="title",
            url_key="link",
            snippet_key="snippet",
            vendor_name="my-post-vendor",
        )
        provider.register_custom_provider("my-post-vendor", custom)

        results = provider.search("hello world", k=1, vendor="my-post-vendor")
        self.assertEqual(len(results), 1)
        self.assertEqual(results[0].title, "Post Result for hello world")
        self.assertEqual(results[0].url, "https://post.example/item")

    def test_fallback_to_duckduckgo_on_unauthorized(self):
        provider = UniversalSearchProvider()
        endpoint = f"http://127.0.0.1:{self.port}/search"
        # Wrong key returns 401, should fall back to DuckDuckGo
        provider.add_custom_endpoint(
            name="failing-service",
            endpoint=endpoint,
            api_key="wrong-key",
        )
        fixture = [SearchResult(title="Fixture", url="https://example.com", snippet="Fixture result", vendor="duckduckgo")]
        with patch.object(DuckDuckGoProvider, 'search', return_value=fixture) as ddg:
            results = provider.search("python", k=2, vendor="failing-service", fallback_to_duckduckgo=True)
        ddg.assert_called_once_with("python", 2)
        self.assertTrue(len(results) > 0)
        self.assertEqual(results[0].vendor, "duckduckgo")


if __name__ == "__main__":
    unittest.main()
