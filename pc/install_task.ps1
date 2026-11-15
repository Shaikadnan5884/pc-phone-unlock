$a = New-ScheduledTaskAction -Execute 'C:\pcunlock\pcunlock_service.exe' -Argument '--pipe' -WorkingDirectory 'C:\pcunlock'
$t = New-ScheduledTaskTrigger -AtStartup
$s = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -RestartCount 99 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit (New-TimeSpan -Seconds 0)
Register-ScheduledTask -TaskName PCUnlock -Action $a -Trigger $t -Settings $s -User SYSTEM -RunLevel Highest -Force | Out-Null
Start-ScheduledTask -TaskName PCUnlock