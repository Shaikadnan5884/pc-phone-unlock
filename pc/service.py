import asyncio, json, secrets, os, threading, sys
from core import *
from transports import get_transport

CFG = json.load(open("config.json"))
pending = {}          # challenge id -> (challenge bytes, future)
import time
TOKEN_FILE = "pair_token.txt"
TOKEN_MAX_AGE = 300   # token valid for 5 minutes

def current_token():
    """Return the pairing token if the file exists and is still fresh."""
    try:
        if time.time() - os.path.getmtime(TOKEN_FILE) > TOKEN_MAX_AGE:
            os.remove(TOKEN_FILE)
            return None
        return open(TOKEN_FILE).read().strip()
    except OSError:
        return None
transport = None

async def on_message(msg, reply):
    t = msg.get("type")
    print("[rx]", t, msg.get("id", ""))
    if t == "PAIR_REQUEST":
        tok = current_token()
        ok = bool(tok) and secrets.compare_digest(msg.get("token", ""), tok)
        if ok:
            open("paired_phone.der", "wb").write(unb64(msg["phone_pubkey"]))
            os.remove(TOKEN_FILE)
            print("[+] Phone paired")
        await reply({"type": "PAIR_RESULT", "ok": ok})
    elif t == "RESPONSE":
        item = pending.pop(msg.get("id"), None)
        if item is None:
            print("[!] RESPONSE has unknown or expired id; pending =", list(pending))
            return
        pub = load_pub()
        if pub is None:
            print("[!] no paired key on this PC")
            item[1].set_result(False)
            return
        ch, fut = item
        try:
            result = verify(pub, ch, unb64(msg["sig"]))
        except Exception as e:
            print("[!] verify crashed:", e)
            result = False
        print("[verify]", result)
        await transport.send({"type": "RESULT", "id": msg.get("id"),
                            "stage": "verified" if result else "denied"})
        if not fut.done():
            fut.set_result(result)

async def trigger_unlock(timeout=45):
    loop = asyncio.get_running_loop()
    ch = new_challenge()
    cid = ch[:8].hex()
    fut = loop.create_future()
    pending[cid] = (ch, fut)
    deadline = loop.time() + timeout
    try:
        while loop.time() < deadline:
            print("[tx] challenge sent to", transport.client_count(), "client(s)")
            await transport.send({"type": "CHALLENGE", "id": cid, "nonce": b64(ch)})
            try:
                res = await asyncio.wait_for(asyncio.shield(fut), 3)
                if res:
                    await transport.send({"type": "RESULT", "id": cid, "stage": "unlocked"})
                return res
            except asyncio.TimeoutError:
                pass
        return False
    finally:
        pending.pop(cid, None)
           
async def main():
    global transport
    transport = get_transport(CFG, on_message)
    await transport.start()
    loop = asyncio.get_running_loop()
    if "--pipe" in sys.argv:
        from pipe_server import start_pipe
        threading.Thread(target=start_pipe, args=(loop, trigger_unlock, CFG), daemon=True).start()
        print("Service mode: waiting for lock-screen requests")
        await asyncio.Future()                 # run forever
    while True:
        await loop.run_in_executor(None, input, "Press Enter to request unlock...\n")
        print("RESULT:", "APPROVED" if await trigger_unlock() else "DENIED")
asyncio.run(main())