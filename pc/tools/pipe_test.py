import win32file
h = win32file.CreateFile(r"\\.\pipe\pcunlock",
        win32file.GENERIC_READ | win32file.GENERIC_WRITE, 0, None,
        win32file.OPEN_EXISTING, 0, None)
win32file.WriteFile(h, b"UNLOCK?")
_, data = win32file.ReadFile(h, 4096)
parts = bytes(data).split(b"\n", 2)
print("status  :", parts[0])
if len(parts) == 3:
    print("user    :", parts[1].decode())
    print("password: (length %d)" % len(parts[2]))