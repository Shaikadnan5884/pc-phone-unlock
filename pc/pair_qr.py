"""
pair_qr.py - PC pairing window.
Pick a transport, click 'Generate QR', scan it with the phone app.
"""
import json, os, secrets, socket, tkinter as tk
from tkinter import messagebox, ttk

import qrcode
from PIL import ImageTk

BASE = os.path.dirname(os.path.abspath(__file__))
CFG_FILE = os.path.join(BASE, "config.json")
TOKEN_FILE = os.path.join(BASE, "pair_token.txt")
PAIRED_FILE = os.path.join(BASE, "paired_phone.der")

DEFAULT_CFG = {
    "transport": "wifi", "port": 8765, "bt_channel": 4,
    "firebase_db_url": "https://YOUR-PROJECT-default-rtdb.firebaseio.com",
    "pair_id": "my-laptop-1", "windows_user": "YourLocalUsername",
}


def load_cfg():
    cfg = dict(DEFAULT_CFG)
    if os.path.exists(CFG_FILE):
        cfg.update(json.load(open(CFG_FILE)))
    return cfg


def local_ip():
    """Find this PC's LAN IP (no data is actually sent)."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


class App(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("PC Unlock - Pair your phone")
        self.resizable(False, False)
        self.cfg = load_cfg()
        self.qr_img = None

        pad = {"padx": 10, "pady": 4}

        ttk.Label(self, text="1. Choose pairing method",
                  font=("Segoe UI", 11, "bold")).grid(row=0, column=0, sticky="w", **pad)
        self.method = tk.StringVar(value=self.cfg["transport"])
        for i, (val, text) in enumerate([
                ("wifi", "Wi-Fi (same router)"),
                ("hotspot", "Hotspot (phone or PC shares network)"),
                ("bluetooth", "Bluetooth"),
                ("firebase", "Firebase (cloud / internet)")]):
            ttk.Radiobutton(self, text=text, value=val, variable=self.method,
                            command=self.on_method).grid(row=1 + i, column=0, sticky="w", padx=25)

        ttk.Label(self, text="2. Connection details",
                  font=("Segoe UI", 11, "bold")).grid(row=5, column=0, sticky="w", **pad)
        form = ttk.Frame(self); form.grid(row=6, column=0, sticky="w", padx=25)

        self.ip = tk.StringVar(value=local_ip())
        self.port = tk.StringVar(value=str(self.cfg["port"]))
        self.fb_url = tk.StringVar(value=self.cfg["firebase_db_url"])
        self.pair_id = tk.StringVar(value=self.cfg["pair_id"])
        self.win_user = tk.StringVar(value=self.cfg["windows_user"])

        self.rows = {}
        for r, (key, label, var) in enumerate([
                ("ip", "PC IP address", self.ip),
                ("port", "Port", self.port),
                ("fb", "Firebase DB URL", self.fb_url),
                ("pid", "Pair ID", self.pair_id),
                ("usr", "Windows username", self.win_user)]):
            lab = ttk.Label(form, text=label); ent = ttk.Entry(form, textvariable=var, width=38)
            lab.grid(row=r, column=0, sticky="w"); ent.grid(row=r, column=1, pady=2)
            self.rows[key] = (lab, ent)

        btns = ttk.Frame(self); btns.grid(row=7, column=0, pady=8)
        ttk.Button(btns, text="Generate QR", command=self.generate).grid(row=0, column=0, padx=4)
        ttk.Button(btns, text="Unpair phone", command=self.unpair).grid(row=0, column=1, padx=4)

        self.qr_label = ttk.Label(self); self.qr_label.grid(row=8, column=0, padx=10)
        self.status = ttk.Label(self, text="", foreground="#0a5"); self.status.grid(row=9, column=0, pady=6)
        self.refresh_status()
        self.on_method()

    # show only the fields that matter for the chosen transport
    def on_method(self):
        m = self.method.get()
        show = {"ip": m in ("wifi", "hotspot"), "port": m in ("wifi", "hotspot"),
                "fb": m == "firebase", "pid": m == "firebase", "usr": True}
        for key, (lab, ent) in self.rows.items():
            (lab.grid if show[key] else lab.grid_remove)()
            (ent.grid if show[key] else ent.grid_remove)()

    def refresh_status(self):
        paired = os.path.exists(PAIRED_FILE)
        self.status.config(text="Status: phone PAIRED" if paired else "Status: not paired",
                           foreground="#0a5" if paired else "#a50")
        self.after(2000, self.refresh_status)    # updates automatically after scanning

    def generate(self):
        m = self.method.get()
        try:
            port = int(self.port.get())
        except ValueError:
            return messagebox.showerror("Error", "Port must be a number.")

        # save settings for the service
        self.cfg.update({"transport": m, "port": port,
                         "firebase_db_url": self.fb_url.get().strip(),
                         "pair_id": self.pair_id.get().strip(),
                         "windows_user": self.win_user.get().strip()})
        json.dump(self.cfg, open(CFG_FILE, "w"), indent=2)

        # one-time token, shared with service.py through a file
        token = secrets.token_hex(4)
        open(TOKEN_FILE, "w").write(token)

        payload = {"v": 1, "transport": m, "token": token}
        if m in ("wifi", "hotspot"):
            payload.update({"ip": self.ip.get().strip(), "port": port})
        elif m == "firebase":
            payload.update({"db_url": self.cfg["firebase_db_url"], "pair_id": self.cfg["pair_id"]})
        elif m == "bluetooth":
            payload.update({"bt_name": socket.gethostname(), "bt_channel": self.cfg["bt_channel"]})

        img = qrcode.make(json.dumps(payload)).get_image().resize((280, 280))
        self.qr_img = ImageTk.PhotoImage(img)
        self.qr_label.config(image=self.qr_img)
        messagebox.showinfo("Restart needed",
            "Settings saved.\nRestart the PC Unlock service so it uses the new transport\n"
            "(close and rerun service.py, or run: nssm restart PCUnlock).\n\n"
            "The QR code is valid for 5 minutes.")

    def unpair(self):
        if os.path.exists(PAIRED_FILE) and messagebox.askyesno("Unpair", "Remove the paired phone?"):
            os.remove(PAIRED_FILE)


App().mainloop()