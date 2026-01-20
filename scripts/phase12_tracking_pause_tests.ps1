param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$OpsKey = "dev-ops-key",
    [string]$PsqlPath = "psql",
    [string]$DbHost = "localhost",
    [string]$DbPort = "5432",
    [string]$DbUser = "cbs",
    [string]$DbName = "cbs_mvp",
    [string]$DbPassword = "",
    [switch]$Cleanup = $true
)

$ErrorActionPreference = "Stop"

function Invoke-Psql {
    param([string]$Sql)
    if (-not (Get-Command $PsqlPath -ErrorAction SilentlyContinue)) {
        throw "psql not found. Set -PsqlPath or add psql to PATH."
    }
    if ($DbPassword -ne "") { $env:PGPASSWORD = $DbPassword }
    & $PsqlPath -h $DbHost -p $DbPort -U $DbUser -d $DbName -t -A -c $Sql
}

function Set-Flags {
    param([string]$FailPrefix, [string]$MissingPrefix)
    $sql = @"
INSERT INTO system_flags(key,value) VALUES
('EBAY_STUB_TRACKING_FAIL_ORDER_PREFIX','$FailPrefix'),
('EBAY_STUB_TRACKING_MISSING_ORDER_PREFIX','$MissingPrefix')
ON CONFLICT (key) DO UPDATE SET value=EXCLUDED.value, updated_at=CURRENT_TIMESTAMP;
"@
    Invoke-Psql $sql | Out-Null
}

function New-SoldOrder {
    param([string]$OrderKey, [decimal]$Usd, [decimal]$FxRate)
    $body = @{
        ebayOrderKey = $OrderKey
        draftId = $null
        soldPriceUsd = $Usd
        fxRate = $FxRate
    } | ConvertTo-Json
    $res = Invoke-RestMethod -Method Post -Uri "$BaseUrl/orders/sold" -ContentType "application/json" -Body $body
    return [int64]$res.orderId
}

function Import-Tracking {
    param([int64]$OrderId, [string]$Carrier, [string]$Tracking)
    $csv = "order_id,outbound_carrier,outbound_tracking`n$OrderId,$Carrier,$Tracking`n"
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/3pl/import-tracking" -ContentType "text/plain" -Body $csv | Out-Null
}

function Upload-Tracking {
    param([int64]$OrderId)
    try {
        Invoke-RestMethod -Method Post -Uri "$BaseUrl/ebay/tracking/$OrderId/upload" | Out-Null
        return $true
    } catch {
        return $false
    }
}

function Resume-Ops {
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/ops/resume" -Headers @{ "X-OPS-KEY" = $OpsKey } | Out-Null
}

function Count-Failed {
    param([int64]$OrderId)
    $raw = Invoke-Psql "SELECT COUNT(*) FROM state_transitions WHERE entity_type='ORDER' AND entity_id=$OrderId AND reason_code='EBAY_TRACKING_UPLOAD_FAILED';"
    return [int](($raw | Out-String).Trim())
}

$flagKeys = @("EBAY_STUB_TRACKING_FAIL_ORDER_PREFIX", "EBAY_STUB_TRACKING_MISSING_ORDER_PREFIX")
$flagState = @{}
foreach ($k in $flagKeys) {
    $cnt = [int](($(Invoke-Psql "SELECT COUNT(*) FROM system_flags WHERE key='$k';") | Out-String).Trim())
    $val = ($(Invoke-Psql "SELECT value FROM system_flags WHERE key='$k';") | Out-String).Trim()
    $flagState[$k] = @{ exists = ($cnt -gt 0); value = $val }
}

$status = Invoke-RestMethod -Method Get -Uri "$BaseUrl/ops/status"
$wasPaused = [bool]$status.paused
$pauseReason = [string]$status.reason
if ($wasPaused) { Resume-Ops }

$orderIds = @()

try {
    Resume-Ops

    # A: recovered (no FAILED log)
    Set-Flags "ORDER-KEY-A" ""
    $aId = New-SoldOrder "ORDER-KEY-A001" 100 150
    $orderIds += $aId
    Import-Tracking $aId "JapanPost" "TRACK-A001"
    $aOk = Upload-Tracking $aId
    $aFailed = Count-Failed $aId
    "TEST A ok=$aOk failedCount=$aFailed"

    # B: unrecovered (FAILED log)
    Set-Flags "ORDER-KEY-B" "ORDER-KEY-B"
    $bId = New-SoldOrder "ORDER-KEY-B001" 100 150
    $orderIds += $bId
    Import-Tracking $bId "JapanPost" "TRACK-B001"
    $bOk = Upload-Tracking $bId
    $bFailed = Count-Failed $bId
    "TEST B ok=$bOk failedCount=$bFailed"

    # C: 5 distinct failures -> pause
    Set-Flags "ORDER-KEY-C" "ORDER-KEY-C"
    1..5 | ForEach-Object {
        $key = ("ORDER-KEY-C{0:000}" -f $_)
        $id = New-SoldOrder $key 100 150
        $orderIds += $id
        Import-Tracking $id "JapanPost" ("TRACK-C{0:000}" -f $_)
        $ok = Upload-Tracking $id
        "TEST C order=$id ok=$ok"
    }

    Start-Sleep -Seconds 12
    $after = Invoke-RestMethod -Method Get -Uri "$BaseUrl/ops/status"
    "TEST C paused=$($after.paused) reason=$($after.reason)"
} finally {
    foreach ($k in $flagKeys) {
        $state = $flagState[$k]
        if ($state.exists) {
            $val = $state.value.Replace("'", "''")
            Invoke-Psql "INSERT INTO system_flags(key, value, updated_at) VALUES ('$k', '$val', CURRENT_TIMESTAMP) ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at;" | Out-Null
        } else {
            Invoke-Psql "DELETE FROM system_flags WHERE key='$k';" | Out-Null
        }
    }

    if ($wasPaused) {
        $body = @{ reason = $pauseReason } | ConvertTo-Json -Compress
        Invoke-RestMethod -Method Post -Uri "$BaseUrl/ops/pause" -Headers @{ "X-OPS-KEY" = $OpsKey } -ContentType "application/json" -Body $body | Out-Null
    } else {
        Invoke-RestMethod -Method Post -Uri "$BaseUrl/ops/resume" -Headers @{ "X-OPS-KEY" = $OpsKey } | Out-Null
    }

    if ($Cleanup -and $orderIds.Count -gt 0) {
        $idList = ($orderIds -join ",")
        Invoke-Psql "DELETE FROM state_transitions WHERE entity_type='ORDER' AND entity_id IN ($idList);" | Out-Null
        Invoke-Psql "DELETE FROM fulfillment WHERE order_id IN ($idList);" | Out-Null
        Invoke-Psql "DELETE FROM orders WHERE order_id IN ($idList);" | Out-Null
    }
}
