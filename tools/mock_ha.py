"""A stand-in Home Assistant for testing Glow's Home card and doorbell pop-up without a real one.

Answers the few REST calls Glow makes (/api/, /api/states, /api/states/<id>, /api/camera_proxy/<id>) for the token
"test-token". Ring the doorbell with:  curl -X POST http://<address>:<port>/mock/ring
Run:  python3 tools/mock_ha.py 8123
Then on the phone remote ▸ More ▸ Home Assistant: http://<this computer's address>:8123 and token test-token.
"""
import json
import struct
import sys
import time
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TOKEN = "test-token"
rang_at = 0.0


def states():
    now = time.time()
    ding = "on" if now - rang_at < 6 else "off"
    temp = 23.0 + (int(now) // 60 % 10) / 10
    def s(eid, state, name, unit=""):
        a = {"friendly_name": name}
        if unit:
            a["unit_of_measurement"] = unit
        return {"entity_id": eid, "state": state, "attributes": a}
    return [
        s("sensor.living_room_temperature", f"{temp:.1f}", "Living room", "°C"),
        s("sensor.outside_humidity", "78", "Outside humidity", "%"),
        s("lock.front_door", "locked", "Front door"),
        s("light.kitchen", "on", "Kitchen light"),
        s("binary_sensor.front_doorbell", ding, "Front doorbell"),
        s("camera.front_door", "idle", "Front door camera"),
        s("automation.ignored", "on", "Not shown in Glow's picker"),
    ]


def png(w=640, h=360):
    """A plain test picture (blue-to-orange gradient with a grey 'door'), as PNG with no image library."""
    rows = []
    for y in range(h):
        row = bytearray([0])
        for x in range(w):
            if 260 < x < 380 and 80 < y < 340:
                row += bytes((90, 90, 100))
            else:
                t = y / h
                row += bytes((int(40 + 200 * t), int(70 + 60 * t), int(160 - 120 * t)))
        rows.append(bytes(row))
    raw = zlib.compress(b"".join(rows), 6)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)) + chunk(b"IDAT", raw) + chunk(b"IEND", b"")


PICTURE = png()


class Handler(BaseHTTPRequestHandler):
    def send(self, code, body, kind="application/json"):
        data = body if isinstance(body, bytes) else json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", kind)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_POST(self):
        global rang_at
        if self.path == "/mock/ring":
            rang_at = time.time()
            return self.send(200, {"rang": True})
        self.send(404, {"message": "not found"})

    def do_GET(self):
        if self.headers.get("Authorization") != "Bearer " + TOKEN:
            return self.send(401, {"message": "401: Unauthorized"})
        p = self.path
        if p == "/api/":
            return self.send(200, {"message": "API running."})
        if p == "/api/states":
            return self.send(200, states())
        if p.startswith("/api/states/"):
            for s in states():
                if s["entity_id"] == p[len("/api/states/"):]:
                    return self.send(200, s)
            return self.send(404, {"message": "Entity not found."})
        if p.startswith("/api/camera_proxy/"):
            return self.send(200, PICTURE, "image/png")
        self.send(404, {"message": "not found"})

    def log_message(self, fmt, *args):
        sys.stderr.write("mock-ha: " + fmt % args + "\n")


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8123
    print(f"mock Home Assistant on port {port}, token {TOKEN}")
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
