"""Serve only the calibration board on a chosen LAN address; no directory access."""
import argparse
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit

PAGE = (Path(__file__).resolve().parents[2] / "app/src/main/assets/calibration-board.html").read_bytes()
STATE_PATH = None

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        path = urlsplit(self.path).path
        if path == "/favicon.ico":
            self.send_response(204)
            self.end_headers()
            return
        if path not in ("/", "/index.html", "/calibration", "/state"):
            self.send_error(404)
            return
        self.send_response(200)
        data = PAGE
        if path == "/state":
            data = STATE_PATH.read_bytes() if STATE_PATH else json.dumps({"active":False,"stage":0,"baseline":False,"done":False,"session":"preview","ageMs":0,"status":"本机页面预览，真实步骤由 Quest 提供"},ensure_ascii=False).encode()
        self.send_header("Content-Type", ("application/json" if path == "/state" else "text/html")+"; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'")
        self.end_headers()
        self.wfile.write(data)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--bind", required=True)
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--state-file", type=Path)
    args = parser.parse_args()
    STATE_PATH = args.state_file
    ThreadingHTTPServer((args.bind, args.port), Handler).serve_forever()
