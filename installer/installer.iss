[Setup]
AppId={{B4E7C1A2-5D3F-4A8B-9C60-2F1E8D7A3B55}
AppName=PC Unlock (phone fingerprint)
AppVersion=1.0
DefaultDirName=C:\pcunlock
DisableDirPage=yes
DisableProgramGroupPage=yes
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir=C:\pcbuild\output
OutputBaseFilename=PCUnlock-Setup
Compression=lzma
SolidCompression=yes
SetupLogging=yes
UninstallDisplayName=PC Unlock

[Files]
Source: "C:\pcbuild\dist\pcunlock_service.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "C:\pcbuild\dist\pair_qr.exe";          DestDir: "{app}"; Flags: ignoreversion
Source: "C:\pcbuild\dist\setup_tool.exe";       DestDir: "{app}"; Flags: ignoreversion
Source: "C:\pcbuild\install_task.ps1";          DestDir: "{app}"; Flags: ignoreversion
Source: "C:\pcbuild\config.default.json";       DestDir: "{app}"; DestName: "config.json"; Flags: onlyifdoesntexist
Source: "C:\pcbuild\PCUnlockProvider.dll";      DestDir: "{sys}"; Flags: ignoreversion restartreplace uninsrestartdelete

[Registry]
Root: HKCR; Subkey: "CLSID\{{7A3F5C2E-91B4-4D6A-8E0F-3C1B2A9D4F57}"; ValueType: string; ValueName: ""; ValueData: "PCUnlock Credential Provider"; Flags: uninsdeletekey
Root: HKCR; Subkey: "CLSID\{{7A3F5C2E-91B4-4D6A-8E0F-3C1B2A9D4F57}\InprocServer32"; ValueType: string; ValueName: ""; ValueData: "PCUnlockProvider.dll"
Root: HKCR; Subkey: "CLSID\{{7A3F5C2E-91B4-4D6A-8E0F-3C1B2A9D4F57}\InprocServer32"; ValueType: string; ValueName: "ThreadingModel"; ValueData: "Apartment"
Root: HKLM; Subkey: "SOFTWARE\Microsoft\Windows\CurrentVersion\Authentication\Credential Providers\{{7A3F5C2E-91B4-4D6A-8E0F-3C1B2A9D4F57}"; ValueType: string; ValueName: ""; ValueData: "PCUnlock Credential Provider"; Flags: uninsdeletekey

[Run]
Filename: "netsh"; Parameters: "advfirewall firewall delete rule name=""PCUnlock 8765"""; Flags: runhidden
Filename: "netsh"; Parameters: "advfirewall firewall add rule name=""PCUnlock 8765"" dir=in action=allow protocol=TCP localport=8765 profile=any"; Flags: runhidden
Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\install_task.ps1"""; Flags: runhidden
Filename: "{app}\setup_tool.exe"; Description: "Save my Windows password and pair my phone"; Flags: postinstall nowait skipifsilent
Filename: "{app}\setup_tool.exe"; Description: "Save my Windows password and pair my phone"; Flags: postinstall nowait skipifsilent shellexec

[UninstallRun]
Filename: "schtasks"; Parameters: "/End /TN PCUnlock"; Flags: runhidden; RunOnceId: "EndTask"
Filename: "schtasks"; Parameters: "/Delete /TN PCUnlock /F"; Flags: runhidden; RunOnceId: "DelTask"
Filename: "taskkill"; Parameters: "/IM pcunlock_service.exe /F"; Flags: runhidden; RunOnceId: "KillSvc"
Filename: "netsh"; Parameters: "advfirewall firewall delete rule name=""PCUnlock 8765"""; Flags: runhidden; RunOnceId: "DelFw"

[UninstallDelete]
Type: files; Name: "{app}\cred.bin"
Type: files; Name: "{app}\paired_phone.der"
Type: files; Name: "{app}\pair_token.txt"
Type: files; Name: "{app}\config.json"
Type: files; Name: "{app}\install_task.ps1"
Type: files; Name: "{app}\*.log"
Type: dirifempty; Name: "{app}"