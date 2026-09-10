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
