if (Test-Path "backend-pids.csv") {
    $processes = Import-Csv -Path "backend-pids.csv"
    foreach ($proc in $processes) {
        $id = $proc.Id
        Write-Host "Stopping process ID $id"
        Stop-Process -Id $id -Force -ErrorAction SilentlyContinue
    }
    Remove-Item "backend-pids.csv" -Force
    Write-Host "All backend services stopped."
} else {
    Write-Host "backend-pids.csv not found. Are the services running?"
}
