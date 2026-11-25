$ErrorActionPreference = "Stop"
$script = Join-Path $PSScriptRoot "WiFiChannelAnalyzer.ps1"
$exe = Join-Path $PSScriptRoot "WiFiChannelAnalyzer.exe"

if (-not (Test-Path $script)) {
    throw "WiFiChannelAnalyzer.ps1 must be in the same folder as this builder."
}

if (-not (Get-Module -ListAvailable -Name ps2exe)) {
    Install-Module ps2exe -Scope CurrentUser -Force
}

Import-Module ps2exe
Invoke-PS2EXE -InputFile $script -OutputFile $exe -NoConsole:$false -Title "Wi-Fi Channel Analyzer" -Description "Local Wi-Fi channel scan and estimate"

Write-Host "Created: $exe" -ForegroundColor Green
