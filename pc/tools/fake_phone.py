"""
fake_phone.py - pretends to be the Android app, for testing the PC side.

Usage:
  python fake_phone.py pair <TOKEN>              pair with the PC service
  python fake_phone.py listen                    wait for unlock requests

Options:
  --transport wifi|firebase    (default: wifi; use wifi for hotspot too)
  --host 192.168.x.x           PC's IP address (default: 127.0.0.1)
"""
import argparse, asyncio, base64, json, os, sys, time

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

KEY_FILE = "phone_private.pem"
CFG = json.load(open("config.json")) if os.path.exists("config.json") else {}
b64 = lambda b: base64.b64encode(b).decode()
unb64 = lambda s: base64.b64decode(s)


# ------------------------------------------------------------------ crypto
def make_key():
    """Create the phone key pair (on a real phone this lives in the Keystore)."""
    key = ec.generate_private_key(ec.SECP256R1())
    with open(KEY_FILE, "wb") as f:
        f.write(key.private_bytes(serialization.Encoding.PEM,
                                  serialization.PrivateFormat.PKCS8,
                                  serialization.NoEncryption()))
    return key


def load_key():
    if not os.path.exists(KEY_FILE):
        sys.exit("No key found. Run 'pair' first.")
    return serialization.load_pem_private_key(open(KEY_FILE, "rb").read(), None)


def public_der(key):
    return key.public_key().public_bytes(serialization.Encoding.DER,
                                         serialization.PublicFormat.SubjectPublicKeyInfo)


def sign(key, data):
    """DER-encoded ECDSA/SHA-256, same format Android's Signature class produces."""
    return key.sign(data, ec.ECDSA(hashes.SHA256()))


def fingerprint_prompt():
    """Simulates the BiometricPrompt. Returns the key to sign with, or None."""
    print("\n>>> UNLOCK REQUEST RECEIVED <<<")
    choice = input("  [Enter] = correct fingerprint | [w] = wrong fingerprint "
                   "| [i] = ignore/cancel : ").strip().lower()
    if choice == "i":
        return None
    if choice == "w":
        print("  (simulating a wrong finger -> signing with a different key)")
        return ec.generate_private_key(ec.SECP256R1())
    return load_key()


def build_response(challenge_msg):
    """Ask for 'fingerprint', then build the RESPONSE message (or None)."""
    key = fingerprint_prompt()
    if key is None:
        print("  Cancelled.")
        return None
    sig = sign(key, unb64(challenge_msg["nonce"]))
    return {"type": "RESPONSE", "id": challenge_msg["id"], "sig": b64(sig)}


# ------------------------------------------------------------------ Wi-Fi / Hotspot
def ws_uri(host):
    return f"ws://{host}:{CFG.get('port', 8765)}"


async def wifi_pair(host, token):
    import websockets
    key = make_key()
    async with websockets.connect(ws_uri(host)) as ws:
        await ws.send(json.dumps({"type": "PAIR_REQUEST", "token": token,
                                  "phone_pubkey": b64(public_der(key))}))
        result = json.loads(await asyncio.wait_for(ws.recv(), 10))
        print("Pairing result:", result)
        if not result.get("ok"):
            os.remove(KEY_FILE)   # pairing failed, discard the key


async def wifi_listen(host):
    import websockets
    key_check = load_key()  # fail early if not paired
    while True:                                   # auto-reconnect loop
        try:
            print(f"Connecting to {ws_uri(host)} ...")
            async with websockets.connect(ws_uri(host)) as ws:
                print("Connected. Waiting for unlock requests (Ctrl+C to quit).")
                async for raw in ws:
                    msg = json.loads(raw)
                    if msg.get("type") == "CHALLENGE":
                        resp = await asyncio.to_thread(build_response, msg)
                        if resp:
                            await ws.send(json.dumps(resp))
                            print("  Response sent.")
        except (OSError, websockets.ConnectionClosed) as e:
            print("Connection lost:", e, "- retrying in 3 s")
            await asyncio.sleep(3)


# ------------------------------------------------------------------ Firebase
def fb_base():
    url = CFG["firebase_db_url"].rstrip("/")
    return f"{url}/requests/{CFG['pair_id']}"


def fb_put(name, data):
    import requests
    requests.put(f"{fb_base()}/{name}.json", json=data, timeout=10)


def fb_take(name):
    """Read and delete a mailbox slot. Returns the message or None."""
    import requests
    data = requests.get(f"{fb_base()}/{name}.json", timeout=10).json()
    if data:
        requests.delete(f"{fb_base()}/{name}.json", timeout=10)
    return data


def firebase_pair(token):
    key = make_key()
    fb_put("to_pc", {"type": "PAIR_REQUEST", "token": token,
                     "phone_pubkey": b64(public_der(key))})
    print("Pair request posted, waiting for PC reply...")
    deadline = time.time() + 20
    while time.time() < deadline:
        msg = fb_take("to_phone")
        if msg and msg.get("type") == "PAIR_RESULT":
            print("Pairing result:", msg)
            if not msg.get("ok"):
                os.remove(KEY_FILE)
            return
        time.sleep(1)
    print("Timed out waiting for the PC.")
    os.remove(KEY_FILE)


def firebase_listen():
    load_key()
    print("Polling Firebase for unlock requests (Ctrl+C to quit)...")
    while True:
        try:
            msg = fb_take("to_phone")
            if msg and msg.get("type") == "CHALLENGE":
                resp = build_response(msg)
                if resp:
                    fb_put("to_pc", resp)
                    print("  Response sent.")
        except Exception as e:
            print("Firebase error:", e)
        time.sleep(1)


# ------------------------------------------------------------------ main
def main():
    p = argparse.ArgumentParser()
    p.add_argument("command", choices=["pair", "listen"])
    p.add_argument("token", nargs="?", help="pairing token (for 'pair')")
    p.add_argument("--transport", choices=["wifi", "firebase"], default="wifi")
    p.add_argument("--host", default="127.0.0.1")
    a = p.parse_args()

    if a.command == "pair":
        if not a.token:
            sys.exit("Usage: python fake_phone.py pair <TOKEN>")
        if a.transport == "wifi":
            asyncio.run(wifi_pair(a.host, a.token))
        else:
            firebase_pair(a.token)
    else:
        try:
            if a.transport == "wifi":
                asyncio.run(wifi_listen(a.host))
            else:
                firebase_listen()
        except KeyboardInterrupt:
            print("\nBye.")


if __name__ == "__main__":
    main()