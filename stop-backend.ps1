<#
  Stops everything start-backend.ps1 started (it reads the PIDs from backend-pids.csv).

  Kafka and the Spring Boot services get a Ctrl+C first, so they shut down cleanly (Kafka closes
  its logs, services deregister from Eureka and leave their Kafka consumer groups). Anything still
  alive after the grace period is force-killed. Redis and Zipkin are simply killed.
  Needs no edits for your machine - it only acts on the recorded PIDs.
#>
$PidFile = Join-Path $PSScriptRoot "backend-pids.csv"
if (-not (Test-Path $PidFile)) { Write-Host "backend-pids.csv not found. Are the services running?"; exit 0 }

$entries = @(Import-Csv $PidFile | ForEach-Object { [PSCustomObject]@{ Id = [int]$_.Id; Name = "$($_.Name)" } })
$forceOnly = @("redis", "redis-server", "zipkin", "kafka-wrapper")
$alive = @($entries | Where-Object { Get-Process -Id $_.Id -ErrorAction SilentlyContinue })
$graceful = @($alive | Where-Object { $forceOnly -notcontains $_.Name })

if ($graceful.Count -gt 0) {
    Write-Host "Sending Ctrl+C to $($graceful.Count) processes (clean shutdown) ..."
    # Attaching to another process's console detaches us from our own, so do it in a throwaway child process.
    $helper = Join-Path $env:TEMP "connecthub-send-ctrl-c.ps1"
    @'
param([string]$PidList)
Add-Type -Namespace Win32 -Name ConsoleNative -MemberDefinition @"
[DllImport("kernel32.dll", SetLastError=true)] public static extern bool AttachConsole(uint dwProcessId);
[DllImport("kernel32.dll", SetLastError=true, ExactSpelling=true)] public static extern bool FreeConsole();
[DllImport("kernel32.dll")] public static extern bool SetConsoleCtrlHandler(IntPtr handler, bool add);
[DllImport("kernel32.dll", SetLastError=true)] public static extern bool GenerateConsoleCtrlEvent(uint dwCtrlEvent, uint dwProcessGroupId);
"@
[Win32.ConsoleNative]::SetConsoleCtrlHandler([IntPtr]::Zero, $true) | Out-Null
foreach ($p in ($PidList -split ",")) {
    [Win32.ConsoleNative]::FreeConsole() | Out-Null
    if ([Win32.ConsoleNative]::AttachConsole([uint32]$p)) {
        [Win32.ConsoleNative]::GenerateConsoleCtrlEvent(0, 0) | Out-Null
        Start-Sleep -Milliseconds 150
    }
}
[Win32.ConsoleNative]::FreeConsole() | Out-Null
'@ | Set-Content $helper
    Start-Process powershell.exe -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $helper, "-PidList", ($graceful.Id -join ",") -WindowStyle Hidden -Wait

    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt 30 -and (@($graceful | Where-Object { Get-Process -Id $_.Id -ErrorAction SilentlyContinue }).Count -gt 0)) { Start-Sleep -Milliseconds 500 }
}

foreach ($e in $entries) {
    $p = Get-Process -Id $e.Id -ErrorAction SilentlyContinue
    if ($p) {
        Stop-Process -Id $e.Id -Force -ErrorAction SilentlyContinue
        Write-Host ("  killed   {0} (pid {1})" -f $e.Name, $e.Id)
    } elseif ($graceful.Id -contains $e.Id) {
        Write-Host ("  stopped  {0} (pid {1}) cleanly" -f $e.Name, $e.Id)
    }
}

# Safety net: a Kafka broker that isn't in the file (e.g. started by hand) would otherwise keep the port.
Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -match "kafka\.Kafka" } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue; Write-Host "  killed   stray Kafka broker (pid $($_.ProcessId))" }

Remove-Item $PidFile -Force
Write-Host "All backend services stopped."
