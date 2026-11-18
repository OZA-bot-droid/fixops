param([string]$HostName='192.168.0.84')
$test = Test-NetConnection -ComputerName $HostName -Port 5432 -WarningAction SilentlyContinue
if (-not $test.TcpTestSucceeded) { Write-Error "DB not reachable at ${HostName}:5432"; exit 1 }
Write-Host "OK: ${HostName}:5432 reachable. Authenticate with assigned service DB credentials next."
