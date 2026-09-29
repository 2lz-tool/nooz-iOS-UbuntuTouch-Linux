#!/usr/bin/env python3
"""Tiny HTTP server for the Kotlin/Native transport tests. Prints its port, then serves until killed."""
import gzip
import http.server
import sys


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
        else:
            self._send(404, b"nope")

    def do_POST(self):
        data = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        self._send(200, f"POST|{self.headers.get('X-Test')}|{data.decode()}".encode())

    def log_message(self, *args):
        pass


server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
print(server.server_address[1], flush=True)
server.serve_forever()
