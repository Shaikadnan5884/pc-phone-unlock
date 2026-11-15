import getpass, json, os, subprocess, tkinter as tk
from tkinter import messagebox, simpledialog

import win32con, win32crypt, win32security

BASE = r"C:\pcunlock"
NOWIN = 0x08000000   # run helper commands without a console window


def main():
    root = tk.Tk()
    root.withdraw()
    user = getpass.getuser()

    pw = simpledialog.askstring(
        "PC Unlock setup",
        f"Enter the Windows password for account '{user}'.\n"
        "It is stored encrypted on this PC only.",
        show="*", parent=root)
    if not pw:
        messagebox.showinfo("PC Unlock", "Setup cancelled. Run setup_tool.exe again later.")
        return

    # Check the password is correct before saving it
    try:
        h = win32security.LogonUser(user, ".", pw,
                                    win32con.LOGON32_LOGON_INTERACTIVE,
                                    win32con.LOGON32_PROVIDER_DEFAULT)
        h.Close()
    except Exception:
        messagebox.showerror(
            "PC Unlock",
            "Windows rejected that password.\n\nCheck it and run setup_tool.exe again.\n"
            "Note: PIN-only accounts are not supported.")
        return

    # Machine-scope encryption (flag 4) so the SYSTEM account can decrypt it at the sign-in screen
    blob = win32crypt.CryptProtectData(pw.encode("utf-8"), "pcunlock", None, None, None, 4)
    cred = os.path.join(BASE, "cred.bin")
    with open(cred, "wb") as f:
        f.write(blob)
    # Only SYSTEM and Administrators may touch it (SIDs work on any Windows language)
    subprocess.run(["icacls", cred, "/inheritance:r", "/grant",
                    "*S-1-5-18:F", "*S-1-5-32-544:F"], creationflags=NOWIN)

    cfg_path = os.path.join(BASE, "config.json")
    cfg = json.load(open(cfg_path)) if os.path.exists(cfg_path) else {}
    cfg["windows_user"] = user
    json.dump(cfg, open(cfg_path, "w"), indent=2)

    # Restart the background task so it picks up the new settings
    subprocess.run(["schtasks", "/End", "/TN", "PCUnlock"], creationflags=NOWIN)
    subprocess.run(["schtasks", "/Run", "/TN", "PCUnlock"], creationflags=NOWIN)

    messagebox.showinfo("PC Unlock", "Password saved.\nThe pairing window opens next.")
    subprocess.Popen([os.path.join(BASE, "pair_qr.exe")])


main()