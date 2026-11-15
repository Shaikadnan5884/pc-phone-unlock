import getpass
import win32crypt

pw = getpass.getpass("Windows password for this VM account: ")
if not pw:
    raise SystemExit("Password cannot be empty.")

# flag 4 = CRYPTPROTECT_LOCAL_MACHINE, so the SYSTEM account can decrypt it later
blob = win32crypt.CryptProtectData(pw.encode("utf-8"), "pcunlock", None, None, None, 4)

with open(r"C:\pcunlock\cred.bin", "wb") as f:
    f.write(blob)

print("Saved C:\\pcunlock\\cred.bin (%d bytes)" % len(blob))