# PC Unlock: unlock Windows with your phone's fingerprint

Many laptops and PCs have no fingerprint sensor. **PC Unlock** turns your Android phone into one.

When the Windows sign-in screen appears, your PC sends an unlock request to your phone. You touch the phone's fingerprint sensor, the phone signs the request, the PC checks the signature, and Windows signs you in. You never type the password.

Built as a **Computer Networking** project. The connection type is pluggable: Wi-Fi, phone hotspot, Firebase (cloud) and Bluetooth.

> **Early-stage software that handles credentials.** Please try it on a spare PC or a virtual machine first, and read the [Security](#security-and-your-password) section before installing.

## Features

- Fingerprint unlock from the Windows sign-in screen, including right after a reboot
- Pair once with a QR code, then only the fingerprint is needed
- Choose how the phone and PC talk: Wi-Fi, hotspot, Firebase or Bluetooth
- Live progress on the phone: connected, signal received, fingerprint authenticated, response sent, PC verified, unlock released
- Your normal password and PIN sign-in always stay available
- One-click Windows installer, no Python needed

## How it works

```
 ANDROID PHONE                                   WINDOWS PC
+-----------------------+                       +----------------------------------+
| UnlockService         |   Wi-Fi / Hotspot /   | Sign-in screen (LogonUI)         |
| BiometricPrompt       |   Firebase / Bluetooth|   PCUnlockProvider.dll           |
| Android Keystore key  | <-------------------> |        | named pipe              |
| Live stage view       |  CHALLENGE / RESPONSE |        v                         |
+-----------------------+                       | pcunlock_service.exe (SYSTEM)    |
                                                +----------------------------------+
```

1. The sign-in screen starts. The provider DLL asks the background service for approval.
2. The service sends a **CHALLENGE** (random nonce plus timestamp) to the phone.
3. The phone shows "Unlock your PC?". You touch the fingerprint sensor.
4. The Android Keystore signs the nonce with a private key that needs your fingerprint and never leaves the phone.
5. The phone sends the **RESPONSE**. The service verifies the ECDSA signature with the public key saved during pairing and checks that the challenge is fresh.
6. On success the service releases the stored credentials to the sign-in screen, and Windows signs in.

### Connection types

| Type | How it works | Concepts |
|---|---|---|
| Wi-Fi | Phone connects to a WebSocket server on the PC (TCP 8765) on the same router | TCP, client-server, DHCP, firewall rules |
| Hotspot | Same code, but the phone or PC provides the network | NAT, gateway, private subnet |
| Firebase | Both sides use a cloud mailbox, so they need no direct route | Brokered messaging, works across networks |
| Bluetooth | RFCOMM link (experimental, not finished) | Short range, proximity |

### Message protocol

All transports use the same JSON messages: `PAIR_REQUEST`, `PAIR_RESULT`, `CHALLENGE`, `RESPONSE`, `RESULT`.

## Requirements

- Windows 10 or 11 (64-bit)
- An Android phone (Android 8.0 or newer) with a fingerprint enrolled
- A Windows account **with a password**. A PIN alone does not work.
  - **Microsoft account:** enter the Microsoft account password, not your PIN.
  - If Windows is set to "only allow Windows Hello sign-in", the installer offers to turn that off. Without this, Windows blocks password sign-in and the unlock fails.
- Phone and PC on the same network (Wi-Fi or hotspot), or internet access for Firebase

## Installation

### Before you install

1. Create a Windows restore point.
2. Create a second local administrator account and check that you can sign in with it.
3. If your drive uses BitLocker, save the recovery key.
4. Keep `unregister.reg` (in `pc/tools/`) on a USB drive.

### Windows

1. Download `PCUnlock-Setup.exe` from the [Releases](../../releases) page. Check the SHA-256 checksum in the release notes.
2. Run it and accept the administrator prompt. Windows may show an "unknown publisher" warning because the installer is not code-signed. Choose **More info > Run anyway**.
3. Tick the option to allow password sign-in if asked, then click **Install**.
4. On the last page keep the setup option ticked and click **Finish**.
5. Enter your Windows account password. A wrong password is rejected.
6. The pairing window opens. Choose a connection type and click **Generate QR**.

### Android

1. Download `app-release.apk` from the [Releases](../../releases) page and install it. Allow installs from your browser or file manager when asked.
2. Open the app, allow notifications and camera access.
3. Tap **Scan QR from PC** and scan the QR code from the pairing window.
4. The first stage, "Connected to PC", turns green.

### Try it

Lock the PC (Start > your user icon > Lock), or restart it and wait at the sign-in screen. The phone shows the request. Touch the sensor and the PC signs in.

## Everyday notes

- **Changed your Windows password?** Run `C:\pcunlock\setup_tool.exe` as administrator and enter the new one.
- **Different network or new IP address?** Open `C:\pcunlock\pair_qr.exe`, generate a new QR code and pair again.
- **Added a new fingerprint on the phone?** Android invalidates the key on purpose. Tap **Unpair** in the app and pair again.
- **Phone clock and PC clock** must be roughly in sync (a minute or so), because requests carry a timestamp.

## Uninstall and emergency recovery

**Normal uninstall:** Settings > Apps > Installed apps > PC Unlock > Uninstall. This removes the sign-in tile, the background task, the firewall rule and the saved password.

**If something goes wrong at the sign-in screen:**

1. Use the normal password or PIN tile. It is always there.
2. Run `unregister.reg` and delete `C:\Windows\System32\PCUnlockProvider.dll`.
3. If you cannot sign in at all: hold **Shift** while clicking **Restart**, choose **Troubleshoot > Startup Settings > Restart**, press **4** for Safe Mode, then repeat step 2.

## What the installer changes on your PC

| Location | Change |
|---|---|
| `C:\pcunlock\` | Program files, `config.json`, and after setup `cred.bin` and `paired_phone.der` |
| `C:\Windows\System32\PCUnlockProvider.dll` | The sign-in screen provider |
| Registry (`HKCR\CLSID\{7A3F5C2E-91B4-4D6A-8E0F-3C1B2A9D4F57}` and `HKLM\...\Authentication\Credential Providers`) | Registers the provider |
| Task Scheduler: `PCUnlock` | Starts the service at boot as SYSTEM |
| Firewall: `PCUnlock 8765` | Allows the phone to connect on TCP 8765 |
| `HKLM\...\PasswordLess\Device` | Only if you agreed: allows password sign-in on Microsoft-account PCs |
| `C:\Windows\Temp\provider.log` | Sign-in log (never contains passwords) |

## Security and your password

This project handles a Windows password, so here is exactly what happens to it. The full source is in this repository, so you can check every statement below.

- During setup, `pc/setup_tool.py` asks for the password, checks it with Windows on your own PC, and stores it encrypted with Windows DPAPI (machine scope) in `C:\pcunlock\cred.bin`. The file is limited to SYSTEM and Administrators.
- The password is **never sent over the network** and never sent to the phone, a website, GitHub or the author.
- The phone only ever sends a **signature** over a one-time challenge. The signing key is in the Android Keystore, needs your fingerprint for every use, and is invalidated if a new fingerprint is added.
- After a verified fingerprint, `pc/service.py` and `pc/pipe_server.py` read the file and pass the credentials through a local named pipe to the provider (`provider/PCUnlockProvider/dllmain.cpp`), which hands them to Windows.
- You can confirm that setup and unlock make no outside connections with Wireshark or Process Monitor.

### Known limitations

- Anyone with administrator rights on the same PC can decrypt `cred.bin`. Do not use this on shared or high-security machines.
- Wi-Fi and hotspot traffic uses `ws://` (not encrypted) on your local network. Signatures and fresh challenges prevent replay, but the messages are readable on the LAN.
- The PC does not yet prove its identity to the phone (no mutual authentication).
- The named pipe uses default permissions and carries the credentials locally.
- The Bluetooth transport is not finished. Firebase relies on polling, so a sleeping phone can be slow.
- The PC address is saved at pairing time. A new DHCP address means pairing again.
- One phone per PC. Passwordless and PIN-only accounts are not supported.
- The installer and DLL are not code-signed, so SmartScreen and some antivirus tools may warn.

Found a security problem? See [SECURITY.md](SECURITY.md).

## Project structure

```
pc-phone-unlock/
+-- pc/            Python service, transports, pairing and setup tools
|   +-- tools/     fake_phone.py, pipe_test.py, .reg files (development helpers)
+-- provider/      C++ Windows credential provider (Visual Studio solution)
+-- android/       Android Studio project (Java)
+-- installer/     Inno Setup script
+-- docs/          Project document and screenshots
```

## Build from source

**PC service and tools (Python 3.12 or newer)**

```
pip install -r pc/requirements.txt
cd pc
pyinstaller --onefile --name pcunlock_service --hidden-import pipe_server --hidden-import win32timezone --hidden-import win32pipe --hidden-import win32file --hidden-import win32crypt --hidden-import pywintypes --collect-submodules websockets service.py
pyinstaller --onefile --noconsole --name pair_qr --hidden-import PIL.ImageTk pair_qr.py
pyinstaller --onefile --noconsole --uac-admin --name setup_tool --hidden-import win32timezone setup_tool.py
```

**Credential provider:** open `provider/PCUnlockProvider.sln` in Visual Studio 2022 (workload: Desktop development with C++), choose **Release** and **x64**, then Build > Rebuild Solution.

**Installer:** install [Inno Setup 6](https://jrsoftware.org/isinfo.php), put the built exes, `PCUnlockProvider.dll`, `install_task.ps1` and `config.default.json` where `installer/installer.iss` expects them, then compile it.

**Android app:** open the `android/` folder in Android Studio and run it on a phone, or use Build > Generate Signed App Bundle / APK.

**Test without a phone:** `pc/tools/fake_phone.py` simulates the phone, and `pc/tools/pipe_test.py` simulates the sign-in screen.

## Troubleshooting

| Problem | What to check |
|---|---|
| Phone shows "Disconnected" | Same network? Firewall rule `PCUnlock 8765` present? `schtasks /Query /TN PCUnlock` shows the task? PC address changed? Pair again |
| Tile does not appear | `PCUnlockProvider.dll` is in `C:\Windows\System32`, built as x64, registry keys exist, restart the PC |
| Tile shows "No approval received" | The service is not running or the phone is not connected. Run `schtasks /Run /TN PCUnlock` |
| Phone says unlocked but Windows does not sign in | Run `setup_tool.exe` again with the correct account password, check that "only allow Windows Hello sign-in" is off, and read `C:\Windows\Temp\provider.log` |
| "Request expired" on the phone | Phone and PC clocks differ. Turn on automatic time on both |
| Windows rejects the password in setup | Use the account password, not the PIN. Passwordless accounts are not supported |
| Port 8765 already in use | Another copy of the service is running. End `pcunlock_service.exe` in Task Manager |

## Contributing

Issues and pull requests are welcome. Good first areas: Bluetooth transport, `wss://` with certificate pinning, mutual authentication, mDNS discovery, a restricted named pipe, multiple phones.

## Licence

MIT. See [LICENSE](LICENSE).

## Author

Built by **Shaik Adnan** as a Computer Networking project.
