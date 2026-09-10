$ErrorActionPreference = "Stop"
$env:JAVA_HOME = "C:\Program Files\Java\latest\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

Write-Host "Loading .env file..."
Get-Content ".env" | ForEach-Object {
    if ($_ -match "^(#.*|\s*)$") { return }
    $name, $value = $_ -split '=', 2
    if ($name -and $value) {
        [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim())
    }
}

Write-Host "Skipping build..."
# mvn clean install -DskipTests
# if ($LASTEXITCODE -ne 0) {
#     Write-Error "Maven build failed."
#     exit $LASTEXITCODE
# }

# Config Server is intentionally NOT started — no other service actually
# consumes it (no spring-cloud-starter-config dependency, no bootstrap.yml
# anywhere). See Config_Server_Reuse.md for why and how to bring it back.

Write-Host "Starting Redis..."
Start-Process "D:\redis\redis-server.exe" -ArgumentList "redis.conf", "--maxmemory", "256mb", "--maxmemory-policy", "allkeys-lru" -WorkingDirectory "D:\redis" -WindowStyle Minimized -PassThru | Export-Csv -Path "backend-pids.csv" -NoTypeInformation -Append

Write-Host "Starting Kafka..."
$env:KAFKA_HEAP_OPTS = "-Xmx256m -Xms128m"
# Start-Process on a .bat captures the cmd.exe wrapper PID, not the real
# java.exe broker process it spawns. Capture the actual child java.exe PID
# instead so stop-backend.ps1 stops the real process, not just the wrapper.
$kafkaWrapper = Start-Process "D:\kafka\bin\windows\kafka-server-start.bat" -ArgumentList "D:\kafka\config\kraft\server.properties" -WorkingDirectory "D:\kafka" -WindowStyle Minimized -PassThru
Start-Sleep -Seconds 6
$kafkaJava = Get-CimInstance Win32_Process -Filter "ParentProcessId=$($kafkaWrapper.Id)" | Where-Object { $_.Name -eq "java.exe" }
if ($kafkaJava) {
    [PSCustomObject]@{ Id = $kafkaJava.ProcessId } | Export-Csv -Path "backend-pids.csv" -NoTypeInformation -Append -Force
} else {
    Write-Warning "Could not find Kafka java.exe child process - it may need to be stopped manually if stop-backend.ps1 does not catch it."
    [PSCustomObject]@{ Id = $kafkaWrapper.Id } | Export-Csv -Path "backend-pids.csv" -NoTypeInformation -Append -Force
}
Start-Sleep -Seconds 4

# Zipkin tracing. Every service already tries to export spans here by default
# (localhost:9411, see application.yml) - without this running they just log
# harmless "Connection refused" noise. Toggle off if RAM gets tight; nothing
# else needs to change since services already degrade gracefully without it.
$startZipkin = $true
if ($startZipkin) {
    Write-Host "Starting Zipkin..."
    Start-Process java -ArgumentList "-Xmx300m", "-jar", "D:\zipkin\zipkin-server-exec.jar" -WorkingDirectory "D:\zipkin" -WindowStyle Minimized -PassThru | Export-Csv -Path "backend-pids.csv" -NoTypeInformation -Append
    Start-Sleep -Seconds 5
}

Write-Host "Starting Service Registry..."
Start-Process java -ArgumentList "-Xmx300m", "-jar", "service-registry/target/service-registry-1.0.0.jar" -WindowStyle Minimized -PassThru | Export-Csv -Path "backend-pids.csv" -NoTypeInformation -Append
Start-Sleep -Seconds 20

$services = @(
    "api-gateway",
    "auth-service",
    "room-service",
    "message-service",
    "media-service",
    "presence-service",
    "notification-service",
    "websocket-service",
    "payment-service",
    "admin-server"
)

foreach ($service in $services) {
    Write-Host "Starting $service..."
    Start-Process java -ArgumentList "-Xmx300m", "-jar", "$service/target/$service-1.0.0.jar" -WindowStyle Minimized -PassThru | Export-Csv -Path "backend-pids.csv" -NoTypeInformation -Append
}

Write-Host "All services started!"
Write-Host "Use stop-backend.ps1 to shut them down."
