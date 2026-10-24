"""A stand-in Xtream Codes server for testing Glow's Live TV without a real subscription.

Accepts any username/password and serves a few public test streams.
Run:  python3 tools/mock_xtream.py <this-computer-ip> 8090
Then sign in on the box with server http://<that address>:<port>.
"""
import json
import sys
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlparse

STREAMS = [
    # (id, category, name, public HLS test stream)
    (101, "1", "Big Buck Bunny", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"),
    (102, "1", "Tears of Steel", "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8"),
    (103, "2", "Apple Bip-Bop", "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8"),
    (105, "3", "Dead Channel", "https://example.invalid/none.m3u8"),
    (106, "3", "Hanging Channel", "HANG"),  # accepts the connection, never answers (tests timeouts)
]
CATEGORIES = [("1", "Movies"), ("2", "Demo"), ("3", "Broken (test errors)")]


class Handler(BaseHTTPRequestHandler):
    def send_json(self, obj):
        body = json.dumps(obj).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        url = urlparse(self.path)
        q = {k: v[0] for k, v in parse_qs(url.query).items()}
        if url.path == "/player_api.php":
            action = q.get("action")
            if action is None:
                return self.send_json({
                    "user_info": {"auth": 1, "status": "Active", "username": q.get("username", ""),
                                  "exp_date": str(int(time.time()) + 30 * 86400), "max_connections": "1"},
                    "server_info": {"url": self.headers.get("Host", ""), "timezone": "Asia/Bangkok"},
                })
            if action == "get_live_categories":
                return self.send_json([{"category_id": c, "category_name": n, "parent_id": 0} for c, n in CATEGORIES])
            if action == "get_live_streams":
                cat = q.get("category_id")
                return self.send_json([
                    {"num": i + 1, "name": name, "stream_type": "live", "stream_id": sid, "stream_icon": "",
                     "category_id": c}
                    for i, (sid, c, name, _) in enumerate(STREAMS) if cat in (None, c)
                ])
            return self.send_json([])
        if url.path.startswith("/live/"):
            sid = url.path.rsplit("/", 1)[-1].split(".")[0]
            for s, _, _, target in STREAMS:
                if str(s) == sid and target == "HANG":
                    time.sleep(120)
                    return
                if str(s) == sid:
                    self.send_response(302)
                    self.send_header("Location", target)
                    self.end_headers()
                    return
        self.send_response(404)
        self.end_headers()


if __name__ == "__main__":
    host, port = sys.argv[1], int(sys.argv[2])
    print(f"mock Xtream on http://{host}:{port}", flush=True)
    from http.server import ThreadingHTTPServer
    ThreadingHTTPServer((host, port), Handler).serve_forever()
