<#
  Starts the whole ConnectHub backend natively (no Docker):
    Redis -> Kafka -> Zipkin (optional) -> service-registry -> the 10 other Spring Boot services

  Usage (from anywhere):
    .\start-backend.ps1              build only if a jar is missing or older than its sources
    .\start-backend.ps1 -Build       force a rebuild first
    .\start-backend.ps1 -SkipBuild   never build (start whatever jars exist)

  Fails fast: if Redis/Kafka/the registry/any service does not become healthy, the script
  says which one and exits 1. Run .\stop-backend.ps1 to clean up.
#>
param([switch]$Build, [switch]$SkipBuild)

# ====================== EDIT THESE FOR YOUR MACHINE ======================
$JavaHome    = "C:\Program Files\Java\latest\jdk-21"
$RedisDir    = "D:\redis"      # contains redis-server.exe, redis-cli.exe, redis.conf
$KafkaDir    = "D:\kafka"      # standard Kafka distribution, KRaft config at config\kraft\server.properties
$ZipkinDir   = "D:\zipkin"     # contains zipkin-server-exec.jar
$StartZipkin = $true           # set $false to save ~300MB RAM; nothing depends on it
# =========================================================================

$ErrorActionPreference = "Stop"
$root    = $PSScriptRoot
$PidFile = Join-Path $root "backend-pids.csv"
Set-Location $root
$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;$env:Path"

$Modules = @("service-registry", "api-gateway", "auth-service", "room-service", "message-service",
             "media-service", "presence-service", "notification-service", "websocket-service",
             "payment-service", "admin-server")
$Ports = @{ "service-registry" = 8761; "api-gateway" = 8080; "auth-service" = 8081; "room-service" = 8082;
            "message-service" = 8083; "media-service" = 8084; "presence-service" = 8085;
            "notification-service" = 8086; "websocket-service" = 8087; "payment-service" = 8088;
            "admin-server" = 9090 }

function Register-Pid([int]$ProcId, [string]$Name) {
    if (-not (Test-Path $PidFile)) { "Id,Name" | Set-Content $PidFile }
    "$ProcId,$Name" | Add-Content $PidFile
}

function Get-HttpStatus([string]$Url) {
    # 10s: notification-service's health probe contacts Gmail SMTP and legitimately takes ~5s.
    try { return [int](Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 10).StatusCode }
    catch [System.Net.WebException] { if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode } else { return 0 } }
    catch { return 0 }
}

function Test-Kafka {
    $bat = Join-Path $KafkaDir "bin\windows\kafka-broker-api-versions.bat"
    $out = (& cmd /c "`"$bat`" --bootstrap-server localhost:9092 2>&1") | Out-String
    return ($LASTEXITCODE -eq 0 -and $out -match "id: \d+")
}

function Wait-Until([string]$Label, [scriptblock]$Test, [int]$TimeoutSec, [scriptblock]$Abort) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
        if (& $Test) { Write-Host ("  OK   {0} ({1:N0}s)" -f $Label, $sw.Elapsed.TotalSeconds); return }
        if ($Abort -and (& $Abort)) { throw "$Label failed: its process exited during startup." }
        Start-Sleep -Seconds 2
    }
    throw "$Label did not become ready within ${TimeoutSec}s."
}

function Test-BuildNeeded {
    $jars = $Modules | ForEach-Object { Join-Path $root "$_\target\$_-1.0.0.jar" }
    $missing = $jars | Where-Object { -not (Test-Path $_) }
    if ($missing) { return "jar missing: $(Split-Path $missing[0] -Leaf)" }
    $oldestJar = ($jars | ForEach-Object { (Get-Item $_).LastWriteTime } | Sort-Object | Select-Object -First 1)
    $srcDirs = @($Modules + "common-lib") | ForEach-Object { Join-Path $root "$_\src" } | Where-Object { Test-Path $_ }
    $newest = Get-ChildItem -Recurse -File -Path $srcDirs | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    $poms = Get-ChildItem -File -Path (@("pom.xml") + ($Modules + "common-lib" | ForEach-Object { "$_\pom.xml" }) | ForEach-Object { Join-Path $root $_ }) -ErrorAction SilentlyContinue
    $newestPom = $poms | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($newestPom -and $newestPom.LastWriteTime -gt $newest.LastWriteTime) { $newest = $newestPom }
    if ($newest.LastWriteTime -gt $oldestJar) { return "source newer than jars: $($newest.FullName.Substring($root.Length + 1))" }
    return $null
}

try {
    # ---- refuse to double-start ----
    if (Test-Path $PidFile) {
        $alive = @(Import-Csv $PidFile | ForEach-Object { Get-Process -Id $_.Id -ErrorAction SilentlyContinue })
        if ($alive.Count -gt 0) { throw "Backend already running ($($alive.Count) tracked processes). Run .\stop-backend.ps1 first." }
        Remove-Item $PidFile -Force
    }

    # ---- build if needed ----
    if (-not $SkipBuild) {
        $reason = if ($Build) { "-Build requested" } else { Test-BuildNeeded }
        if ($reason) {
            Write-Host "Building backend ($reason) - about 1-2 minutes..."
            if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { throw "mvn not found on PATH; install Maven or use -SkipBuild." }
            $out = (& cmd /c "mvn clean install -DskipTests -q 2>&1") | Out-String
            if ($LASTEXITCODE -ne 0) { Write-Host ($out -split "`n" | Select-Object -Last 40 | Out-String); throw "Maven build failed." }
            Write-Host "  OK   build finished"
        } else { Write-Host "Jars are up to date - skipping build (use -Build to force)." }
    }

    # ---- environment ----
    Write-Host "Loading .env ..."
    if (-not (Test-Path (Join-Path $root ".env"))) { throw ".env not found - copy .env.example to .env and fill it in (see SETUP.md)." }
    Get-Content (Join-Path $root ".env") | ForEach-Object {
        if ($_ -match "^(#.*|\s*)$") { return }
        $name, $value = $_ -split '=', 2
        if ($name -and $value) { [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim()) }
    }

    # ---- internal service secret ----
    # Shared by the gateway and every service so they can tell each other from anything else that reaches
    # a service port. Generated once and kept in .env (gitignored) so restarts and manual runs agree.
    if (-not [Environment]::GetEnvironmentVariable("INTERNAL_SERVICE_SECRET")) {
        $bytes = New-Object byte[] 48
        (New-Object System.Security.Cryptography.RNGCryptoServiceProvider).GetBytes($bytes)
        $generated = [Convert]::ToBase64String($bytes)
        Add-Content -Path (Join-Path $root ".env") -Value ("{0}# Shared gateway<->service secret (generated by start-backend.ps1){0}INTERNAL_SERVICE_SECRET={1}" -f [Environment]::NewLine, $generated)
        [Environment]::SetEnvironmentVariable("INTERNAL_SERVICE_SECRET", $generated)
        Write-Host "  Generated INTERNAL_SERVICE_SECRET and saved it to .env"
    }

    # ---- Redis ----
    Write-Host "Starting Redis ..."
    $redis = Start-Process "$RedisDir\redis-server.exe" -ArgumentList "redis.conf", "--maxmemory", "256mb", "--maxmemory-policy", "allkeys-lru" `
             -WorkingDirectory $RedisDir -WindowStyle Minimized -PassThru
    Register-Pid $redis.Id "redis"
    Wait-Until "Redis" { (& "$RedisDir\redis-cli.exe" -h localhost ping 2>$null) -eq "PONG" } 30 { $redis.HasExited }

    # ---- Kafka ----
    Write-Host "Starting Kafka ..."
    $env:KAFKA_HEAP_OPTS = "-Xmx256m -Xms128m"
    $wrapper = Start-Process "$KafkaDir\bin\windows\kafka-server-start.bat" -ArgumentList "$KafkaDir\config\kraft\server.properties" `
               -WorkingDirectory $KafkaDir -WindowStyle Minimized -PassThru
    Register-Pid $wrapper.Id "kafka-wrapper"
    # The .bat spawns the real java.exe as a child; wait (up to 45s) until it exists instead of guessing a fixed delay.
    $kafkaJava = $null
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while (-not $kafkaJava -and $sw.Elapsed.TotalSeconds -lt 45) {
        $kafkaJava = Get-CimInstance Win32_Process -Filter "ParentProcessId=$($wrapper.Id)" | Where-Object { $_.Name -eq "java.exe" } | Select-Object -First 1
        if (-not $kafkaJava) { $kafkaJava = Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -match "kafka\.Kafka" } | Select-Object -First 1 }
        if (-not $kafkaJava) { Start-Sleep -Milliseconds 500 }
    }
    if (-not $kafkaJava) { throw "Kafka's java process never appeared. Check $KafkaDir\config\kraft\server.properties and $KafkaDir\logs\." }
    $kafkaPid = [int]$kafkaJava.ProcessId
    Register-Pid $kafkaPid "kafka"
    Wait-Until "Kafka broker" { Test-Kafka } 120 { -not (Get-Process -Id $kafkaPid -ErrorAction SilentlyContinue) }

    # ---- Zipkin (optional) ----
    if ($StartZipkin) {
        if (Test-Path "$ZipkinDir\zipkin-server-exec.jar") {
            Write-Host "Starting Zipkin ..."
            $zip = Start-Process java -ArgumentList "-Xmx300m", "-jar", "$ZipkinDir\zipkin-server-exec.jar" -WorkingDirectory $ZipkinDir -WindowStyle Minimized -PassThru
            Register-Pid $zip.Id "zipkin"
            try { Wait-Until "Zipkin" { (Get-HttpStatus "http://localhost:9411/health") -eq 200 } 60 { $zip.HasExited } }
            catch { Write-Warning "Zipkin is optional - continuing without it ($($_.Exception.Message))" }
        } else { Write-Warning "Zipkin jar not found at $ZipkinDir - skipping (set `$StartZipkin = `$false to silence)." }
    }

    # ---- service-registry first, then everything else ----
    $procs = @{}
    function Start-Backend([string]$Name) {
        $p = Start-Process java -ArgumentList "-Xmx300m", "-jar", "$Name\target\$Name-1.0.0.jar" -WorkingDirectory $root -WindowStyle Minimized -PassThru
        Register-Pid $p.Id $Name
        $procs[$Name] = $p
    }
    Write-Host "Starting service-registry ..."
    Start-Backend "service-registry"
    Wait-Until "service-registry" { (Get-HttpStatus "http://localhost:8761/actuator/health") -eq 200 } 120 { $procs["service-registry"].HasExited }

    Write-Host "Starting services ..."
    foreach ($m in ($Modules | Where-Object { $_ -ne "service-registry" })) { Start-Backend $m }

    # ---- wait for all of them (in parallel) ----
    $pending = @($Modules | Where-Object { $_ -ne "service-registry" })
    $degraded = @()
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($pending.Count -gt 0 -and $sw.Elapsed.TotalSeconds -lt 360) {
        foreach ($m in @($pending)) {
            if ($procs[$m].HasExited) { throw "$m exited during startup (exit code $($procs[$m].ExitCode)). See logs\$m\$m.log." }
            $code = Get-HttpStatus "http://localhost:$($Ports[$m])/actuator/health"
            if ($code -eq 200) { Write-Host ("  OK   {0} ({1:N0}s)" -f $m, $sw.Elapsed.TotalSeconds); $pending = @($pending | Where-Object { $_ -ne $m }) }
            elseif ($code -eq 503) { Write-Warning "$m is up but reports DEGRADED (a dependency such as mail is down) - check http://localhost:$($Ports[$m])/actuator/health"; $degraded += $m; $pending = @($pending | Where-Object { $_ -ne $m }) }
        }
        if ($pending.Count -gt 0) { Start-Sleep -Seconds 3 }
    }
    if ($pending.Count -gt 0) { throw "Not healthy after 360s: $($pending -join ', '). See logs\<service>\<service>.log." }

    # ---- Kafka must still be alive now (it used to die ~40s after boot) ----
    if (-not (Test-Kafka)) { throw "Kafka stopped responding while services were starting. See $KafkaDir\logs\server.log and Native_Redis_Kafka.md." }
    Write-Host "  OK   Kafka still healthy"

    $free = [math]::Round((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB, 1)
    Write-Host ""
    Write-Host "Backend is up: gateway http://localhost:8080 | Eureka http://localhost:8761 | Zipkin http://localhost:9411 | free RAM ~${free} GB"
    if ($degraded.Count -gt 0) { Write-Host "Degraded: $($degraded -join ', ')" }
    Write-Host "Stop everything with .\stop-backend.ps1"
}
catch {
    Write-Host ""
    Write-Host "STARTUP FAILED: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Anything already started is still running - run .\stop-backend.ps1 to clean up." -ForegroundColor Yellow
    exit 1
}
