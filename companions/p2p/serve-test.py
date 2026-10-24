#!/usr/bin/env python3
"""Explicit loopback-only test pages; no production signaling or public server."""
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
class Page(BaseHTTPRequestHandler):
    def do_GET(self):
        files = {'/': ROOT/'companions/p2p/browser-test.html', '/test.html': ROOT/'companions/p2p/browser-test.html', '/index.html': ROOT/'app/src/main/assets/p2p/index.html'}
        path = files.get(self.path)
        if path is None: self.send_error(404); return
        content = path.read_bytes()
        self.send_response(200); self.send_header('Content-Type','text/html; charset=utf-8'); self.send_header('Content-Length', str(len(content))); self.send_header('X-Content-Type-Options', 'nosniff'); self.end_headers(); self.wfile.write(content)
    def log_message(self, *args): pass
if __name__ == '__main__': HTTPServer(('127.0.0.1', 8796), Page).serve_forever()
