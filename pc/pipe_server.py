import asyncio, traceback
import win32pipe, win32file, win32crypt, pywintypes

PIPE = r"\\.\pipe\pcunlock"

def start_pipe(loop, trigger_unlock, cfg):
    while True:
        h = win32pipe.CreateNamedPipe(
            PIPE, win32pipe.PIPE_ACCESS_DUPLEX,
            win32pipe.PIPE_TYPE_MESSAGE | win32pipe.PIPE_READMODE_MESSAGE | win32pipe.PIPE_WAIT,
            1, 65536, 65536, 0, None)
        try:
            try:
                win32pipe.ConnectNamedPipe(h, None)
            except pywintypes.error as e:
                if e.winerror != 535:      # 535 = client already connected, fine
                    raise
            win32file.ReadFile(h, 1024)
            print("[pipe] lock screen asked for unlock")
            ok = asyncio.run_coroutine_threadsafe(trigger_unlock(), loop).result()
            if ok:
                blob = open(r"C:\pcunlock\cred.bin", "rb").read()
                pw = win32crypt.CryptUnprotectData(blob, None, None, None, 0)[1]
                win32file.WriteFile(h, b"OK\n" + cfg["windows_user"].encode("utf-8") + b"\n" + pw)
                print("[pipe] credentials released")
            else:
                win32file.WriteFile(h, b"NO")
                print("[pipe] not approved")
        except Exception:
            traceback.print_exc()
        finally:
            try:
                win32file.FlushFileBuffers(h)   # wait until the client has read the reply
            except Exception:
                pass
            try:
                win32pipe.DisconnectNamedPipe(h)
            except Exception:
                pass
            win32file.CloseHandle(h)