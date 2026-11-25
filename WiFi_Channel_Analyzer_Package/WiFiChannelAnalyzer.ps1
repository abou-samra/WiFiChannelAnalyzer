$ErrorActionPreference = "Stop"

function Get-WifiScan {
    $raw = netsh.exe wlan show networks mode=bssid 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0 -and [string]::IsNullOrWhiteSpace($raw)) {
        throw "netsh could not scan Wi-Fi networks. Check that the WLAN service and Wi-Fi adapter are enabled."
    }

    $items = New-Object System.Collections.Generic.List[object]
    $ssid = "(hidden or unknown)"
    $signal = $null
    $channel = $null
    $bssid = $null

    foreach ($line in ($raw -split "`r?`n")) {
        # English Windows output. The SSID line starts a new network section.
        if ($line -match '^\s*SSID\s+\d+\s*:\s*(.*)$') {
            $ssid = $Matches[1].Trim()
            $signal = $null
            $channel = $null
            $bssid = $null
            continue
        }
        if ($line -match '^\s*BSSID\s+\d+\s*:\s*(.*)$') {
            # Flush previous BSSID entry if it has enough data.
            if ($null -ne $channel -and $null -ne $signal) {
                $items.Add([pscustomobject]@{
                    SSID = $ssid; BSSID = $bssid; Signal = [int]$signal; Channel = [int]$channel
                })
            }
            $bssid = $Matches[1].Trim()
            $signal = $null
            $channel = $null
            continue
        }
        if ($line -match '^\s*Signal\s*:\s*(\d+)\s*%') {
            $signal = [int]$Matches[1]
            continue
        }
        if ($line -match '^\s*Channel\s*:\s*(\d+)') {
            $channel = [int]$Matches[1]
            continue
        }
    }

    # Flush last BSSID.
    if ($null -ne $channel -and $null -ne $signal) {
        $items.Add([pscustomobject]@{
            SSID = $ssid; BSSID = $bssid; Signal = [int]$signal; Channel = [int]$channel
        })
    }
    return $items
}

function Get-24Score([int]$target, $networks) {
    # Approximate 20 MHz 2.4 GHz overlap. Channel centers are 5 MHz apart;
    # interference contribution falls to zero at a 25 MHz separation.
    $score = 0.0
    foreach ($n in $networks | Where-Object { $_.Channel -ge 1 -and $_.Channel -le 14 }) {
        $distanceMHz = [math]::Abs($target - $n.Channel) * 5
        $weight = [math]::Max(0.0, 1.0 - ($distanceMHz / 25.0))
        $score += ($n.Signal / 100.0) * $weight
    }
    return [math]::Round($score, 3)
}

function Get-5Score([int]$target, $networks) {
    # Simple 20 MHz estimate: same-channel activity is weighted most;
    # adjacent 20 MHz channels receive a small penalty. This is not an RF measurement.
    $score = 0.0
    foreach ($n in $networks | Where-Object { $_.Channel -gt 14 }) {
        $d = [math]::Abs($target - $n.Channel)
        if ($d -eq 0) { $weight = 1.0 }
        elseif ($d -le 4) { $weight = 0.15 }
        else { $weight = 0.0 }
        $score += ($n.Signal / 100.0) * $weight
    }
    return [math]::Round($score, 3)
}

try {
    Write-Host "`n========== WI-FI CHANNEL ANALYZER ==========" -ForegroundColor Cyan
    Write-Host "Scanning nearby access points..." -ForegroundColor Gray
    $networks = @(Get-WifiScan)

    if ($networks.Count -eq 0) {
        Write-Host "`nNo networks were parsed." -ForegroundColor Yellow
        Write-Host "Make sure Wi-Fi is enabled. This script expects English Windows netsh output."
        Write-Host "If Windows is in another language, the labels may need adjusting."
        Read-Host "`nPress Enter to close"
        exit
    }

    Write-Host "`nDetected access points:" -ForegroundColor Cyan
    $networks |
        Sort-Object Channel, @{Expression="Signal";Descending=$true} |
        Format-Table SSID, Signal, Channel, BSSID -AutoSize

    $net24 = @($networks | Where-Object { $_.Channel -ge 1 -and $_.Channel -le 14 })
    $net5 = @($networks | Where-Object { $_.Channel -gt 14 })

    if ($net24.Count -gt 0) {
        Write-Host "`n2.4 GHz (20 MHz) channel ranking — lower score is better:" -ForegroundColor Cyan
        $rank24 = @(1, 6, 11 | ForEach-Object {
            [pscustomobject]@{ Channel = $_; Score = (Get-24Score $_ $net24) }
        } | Sort-Object Score, Channel)
        $rank24 | Format-Table -AutoSize
        Write-Host ("Suggested 2.4 GHz channel: {0}" -f $rank24[0].Channel) -ForegroundColor Green
    } else {
        Write-Host "`nNo 2.4 GHz access points were detected." -ForegroundColor Yellow
    }

    if ($net5.Count -gt 0) {
        # Common 5 GHz channels. Actual availability depends on country, router and DFS support.
        $candidates5 = @(36, 40, 44, 48, 52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 144, 149, 153, 157, 161, 165)
        Write-Host "`n5 GHz channel ranking (simplified 20 MHz estimate) — lower score is better:" -ForegroundColor Cyan
        $rank5 = @($candidates5 | ForEach-Object {
            [pscustomobject]@{ Channel = $_; Score = (Get-5Score $_ $net5) }
        } | Sort-Object Score, Channel)
        $rank5 | Select-Object -First 10 | Format-Table -AutoSize
        Write-Host ("Lowest-scoring 5 GHz candidate: {0}" -f $rank5[0].Channel) -ForegroundColor Green
        Write-Host "Check that this channel is permitted by the modem's country/region and supported by the modem." -ForegroundColor Yellow
    } else {
        Write-Host "`nNo 5 GHz access points were detected (or the adapter cannot scan 5 GHz)." -ForegroundColor Yellow
    }

    Write-Host "`nImportant: this is an estimate based on visible Wi-Fi networks and their reported signal."
    Write-Host "It cannot detect non-Wi-Fi interference, hidden networks, traffic/load, or actual airtime use."
    Write-Host "For 2.4 GHz, use 20 MHz width where possible. Re-scan at the modem's location."
    Write-Host "After changing the modem channel, scan/test again; the PC's location matters."
}
catch {
    Write-Host "`nERROR: $($_.Exception.Message)" -ForegroundColor Red
}
Read-Host "`nPress Enter to close"
