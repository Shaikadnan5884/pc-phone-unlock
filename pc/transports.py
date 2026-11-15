import asyncio
import json
from abc import ABC, abstractmethod


class Transport(ABC):
    """Every transport receives messages via on_message(msg, reply_fn)
    and sends messages to the phone with send(msg)."""

    def __init__(self, cfg, on_message):
        self.cfg = cfg
        self.on_message = on_message

    @abstractmethod
    async def start(self): ...

    @abstractmethod
    async def send(self, msg: dict): ...

    def client_count(self) -> int:
        """How many phones are currently reachable (used for logging)."""
        return 0


# ---------------------------------------------------------------------------
# Wi-Fi (router) and Hotspot: same code, only the network is different
# ---------------------------------------------------------------------------
class WiFiTransport(Transport):
    async def start(self):
        import websockets

        self.clients = set()

        async def handler(ws):
            self.clients.add(ws)
            print(f"[wifi] client connected ({len(self.clients)} total)")
            try:
                async for raw in ws:
                    try:
                        msg = json.loads(raw)
                    except json.JSONDecodeError:
                        print("[wifi] ignored non-JSON message")
                        continue
                    # reply() sends back on this same connection
                    await self.on_message(msg, lambda m, ws=ws: ws.send(json.dumps(m)))
            except Exception as e:
                print("[wifi] connection error:", e)
            finally:
                self.clients.discard(ws)
                print(f"[wifi] client disconnected ({len(self.clients)} left)")

        await websockets.serve(
            handler, "0.0.0.0", self.cfg["port"],
            ping_interval=10, ping_timeout=10,   # detects dead phones quickly
        )
        print(f"[wifi] listening on port {self.cfg['port']}")

    async def send(self, msg):
        dead = []
        for ws in list(self.clients):
            try:
                await ws.send(json.dumps(msg))
            except Exception:
                dead.append(ws)              # remove broken connections
        for ws in dead:
            self.clients.discard(ws)

    def client_count(self):
        return len(getattr(self, "clients", ()))


# ---------------------------------------------------------------------------
# Bluetooth RFCOMM (experimental, add this last)
# ---------------------------------------------------------------------------
class BluetoothTransport(Transport):
    async def start(self):
        self.loop = asyncio.get_running_loop()
        self.conn = None
        asyncio.create_task(asyncio.to_thread(self._serve))

    def _serve(self):
        import socket
        srv = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM,
                            socket.BTPROTO_RFCOMM)
        srv.bind((socket.BDADDR_ANY, self.cfg["bt_channel"]))
        srv.listen(1)
        print(f"[bluetooth] listening on RFCOMM channel {self.cfg['bt_channel']}")
        while True:
            try:
                conn, addr = srv.accept()
            except OSError as e:
                print("[bluetooth] accept failed:", e)
                continue
            self.conn = conn
            print("[bluetooth] connected:", addr)
            buf = b""
            try:
                while True:
                    data = conn.recv(4096)
                    if not data:
                        break
                    buf += data
                    while b"\n" in buf:
                        line, buf = buf.split(b"\n", 1)
                        try:
                            msg = json.loads(line)
                        except json.JSONDecodeError:
                            continue
                        asyncio.run_coroutine_threadsafe(
                            self.on_message(msg, self.send), self.loop)
            except OSError as e:
                print("[bluetooth] connection error:", e)
            finally:
                self.conn = None
                conn.close()
                print("[bluetooth] disconnected")

    async def send(self, msg):
        conn = self.conn
        if conn:
            try:
                await asyncio.to_thread(conn.sendall,
                                        json.dumps(msg).encode() + b"\n")
            except OSError as e:
                print("[bluetooth] send failed:", e)

    def client_count(self):
        return 1 if getattr(self, "conn", None) else 0


# ---------------------------------------------------------------------------
# Firebase Realtime Database used as a mailbox
#   to_phone : PC writes, phone reads and deletes
#   to_pc    : phone writes, PC reads and deletes
# ---------------------------------------------------------------------------
class FirebaseTransport(Transport):
    async def start(self):
        self.base = (f"{self.cfg['firebase_db_url'].rstrip('/')}"
                     f"/requests/{self.cfg['pair_id']}")
        print("[firebase] mailbox:", self.base)
        asyncio.create_task(self._poll())

    async def send(self, msg):
        import requests
        try:
            r = await asyncio.to_thread(
                requests.put, f"{self.base}/to_phone.json", json=msg, timeout=10)
            if not r.ok:
                print(f"[firebase] send failed: HTTP {r.status_code} {r.text[:100]}")
        except Exception as e:
            print("[firebase] send error:", e)

    async def _poll(self):
        import requests
        while True:
            try:
                r = await asyncio.to_thread(
                    requests.get, f"{self.base}/to_pc.json", timeout=10)
                if not r.ok:
                    print(f"[firebase] read failed: HTTP {r.status_code} "
                          "(check the database Rules)")
                else:
                    data = r.json()
                    if data:
                        await asyncio.to_thread(
                            requests.delete, f"{self.base}/to_pc.json", timeout=10)
                        await self.on_message(data, self.send)
            except Exception as e:
                print("[firebase] poll error:", e)
            await asyncio.sleep(1)

    def client_count(self):
        return 1   # a mailbox has no live connection to count


# ---------------------------------------------------------------------------
def get_transport(cfg, on_message):
    kinds = {
        "wifi": WiFiTransport,
        "hotspot": WiFiTransport,
        "bluetooth": BluetoothTransport,
        "firebase": FirebaseTransport,
    }
    if cfg["transport"] not in kinds:
        raise ValueError(f"Unknown transport: {cfg['transport']}")
    return kinds[cfg["transport"]](cfg, on_message)