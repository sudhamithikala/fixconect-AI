# FixConnect AI - turn on HTTPS on the Windows EC2 server (run once).
# Puts Caddy in front of the Spring Boot app (port 8080). Caddy gets a free Let's Encrypt
# certificate automatically, renews it by itself, and starts with Windows.
#
# Run on the server in PowerShell opened with "Run as administrator":
#     powershell -ExecutionPolicy Bypass -File .\setup-https.ps1
# (If you buy a domain later:  powershell -ExecutionPolicy Bypass -File .\setup-https.ps1 -Domain my.domain.com)

param([string]$Domain = "3-27-230-240.sslip.io")

$ErrorActionPreference = "Stop"
$Dir = "C:\caddy"
$Exe = Join-Path $Dir "caddy.exe"
$Conf = Join-Path $Dir "Caddyfile"

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "Please open PowerShell with 'Run as administrator' and run this script again." -ForegroundColor Red
    exit 1
}

Write-Host ">> Setting up HTTPS for https://$Domain"
New-Item -ItemType Directory -Force -Path $Dir | Out-Null

# 1. Download Caddy (one file, no installer)
if (-not (Test-Path $Exe)) {
    Write-Host ">> Downloading Caddy"
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $ProgressPreference = "SilentlyContinue"
    $zip = Join-Path $Dir "caddy.zip"
    Invoke-WebRequest -UseBasicParsing -OutFile $zip `
        -Uri "https://github.com/caddyserver/caddy/releases/download/v2.8.4/caddy_2.8.4_windows_amd64.zip"
    Expand-Archive -Path $zip -DestinationPath $Dir -Force
    Remove-Item $zip
}

# 2. Config: https://<domain>  ->  the app on localhost:8080
$caddyfile = "$Domain {`r`n    encode gzip`r`n    reverse_proxy 127.0.0.1:8080`r`n}`r`n"
Set-Content -Path $Conf -Value $caddyfile -Encoding Ascii -NoNewline
& $Exe validate --config $Conf --adapter caddyfile
if ($LASTEXITCODE -ne 0) { Write-Host "Caddyfile is not valid." -ForegroundColor Red; exit 1 }

# 3. Windows Firewall: allow 80 (certificate check + redirect) and 443 (HTTPS)
if (-not (Get-NetFirewallRule -DisplayName "FixConnect HTTPS (80, 443)" -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule -DisplayName "FixConnect HTTPS (80, 443)" -Direction Inbound -Protocol TCP `
        -LocalPort 80, 443 -Action Allow | Out-Null
    Write-Host ">> Opened ports 80 and 443 in Windows Firewall"
}

# 4. Run Caddy as a Windows service that starts automatically with the server
if (Get-Service -Name caddy -ErrorAction SilentlyContinue) {
    Stop-Service -Name caddy -Force -ErrorAction SilentlyContinue
    sc.exe delete caddy | Out-Null
    Start-Sleep -Seconds 2
}
sc.exe create caddy start= auto DisplayName= "Caddy - HTTPS for FixConnect AI" `
    binPath= "$Exe run --config $Conf --adapter caddyfile" | Out-Null
sc.exe failure caddy reset= 86400 actions= restart/5000/restart/5000/restart/5000 | Out-Null
Start-Service -Name caddy
Start-Sleep -Seconds 8

if ((Get-Service -Name caddy).Status -eq "Running") {
    Write-Host ""
    Write-Host ">> Done. Open https://$Domain" -ForegroundColor Green
    Write-Host "   (the first visit can take about 30 seconds while the certificate is issued)"
    Write-Host "   If it does not open: check ports 80 and 443 in the EC2 security group, and that"
    Write-Host "   nothing else (for example IIS) is already using port 80 on this server."
} else {
    Write-Host "Caddy did not start. Run this to see why:" -ForegroundColor Red
    Write-Host "   C:\caddy\caddy.exe run --config C:\caddy\Caddyfile --adapter caddyfile"
    exit 1
}
