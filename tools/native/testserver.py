#!/usr/bin/env python3
"""Tiny HTTP server for the Kotlin/Native transport tests. Prints its port, then serves until killed."""
import gzip
import http.server
import sys


PORT = 0
HEADLINES = [
    "Council approves a new tram line after a decade of argument",
    "Farmers report the best barley harvest since records began",
    "Central bank holds rates as inflation edges lower again",
    "Astronomers spot a faint comet that may be visible next month",
]
FEED = ""
ARTICLE = """<html><head><title>Story {n}</title></head><body><article><h1>Story {n}</h1>
<p>The first paragraph of story {n} sets out what happened and why it matters to the people involved.</p>
<p>The second paragraph of story {n} adds detail, quotes an official, and describes the reaction in the town.</p>
<p>The third paragraph of story {n} looks ahead to what comes next and who will be watching closely.</p>
</article></body></html>"""


class Handler(http.server.BaseHTTPRequestHandler):
    def _send(self, code, body, ctype="text/plain", extra=None):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/hello":
            self._send(200, b"hello native")
        elif self.path == "/gzip":
            self._send(200, gzip.compress(b"compressed text"), extra={"Content-Encoding": "gzip"})
        elif self.path == "/redirect":
            self._send(302, b"", extra={"Location": "/hello"})
        elif self.path == "/feed.xml":
            self._send(200, FEED.encode(), "application/rss+xml")
        elif self.path.startswith("/story/"):
            n = self.path.rsplit("/", 1)[1]
            self._send(200, ARTICLE.format(n=n).encode(), "text/html; charset=utf-8")
        else:
            self._send(404, b"nope")

    def do_POST(self):
        data = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        self._send(200, f"POST|{self.headers.get('X-Test')}|{data.decode()}".encode())

    def log_message(self, *args):
        pass


server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
import email.utils, time
port = server.server_address[1]
items = "".join(
    f"<item><title>{h}</title><link>http://127.0.0.1:{port}/story/{i}</link><guid>story-{i}</guid>"
    f"<description>{h}. Officials gave their reaction on Tuesday.</description>"
    f"<pubDate>{email.utils.formatdate(time.time() - 1200 * i, usegmt=True)}</pubDate></item>"
    for i, h in enumerate(HEADLINES)
)
FEED = f'<?xml version="1.0"?><rss version="2.0"><channel><title>Test Wire</title><link>http://127.0.0.1:{port}/</link>{items}</channel></rss>'
print(server.server_address[1], flush=True)
server.serve_forever()
