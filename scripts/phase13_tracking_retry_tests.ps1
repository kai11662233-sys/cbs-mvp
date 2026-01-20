param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$OpsKey = "dev-ops-key",
    [string]$RepoPath = "C:\Users\valo1\cbs-mvp",
    [string]$PsqlPath = "C:\Program Files\PostgreSQL\16\bin\psql.exe",
    [string]$DbHost = "localhost",
    [string]$DbPort = "5432",
    [string]$DbUser = "cbs",
    [string]$DbName = "cbs_mvp",
    [string]$DbPassword = "cbs",
    [switch]$Cleanup = $true
)

$ErrorActionPreference = "Stop"

function Invoke-Psql {
    param([string]$Sql)
    if (-not (Test-Path $PsqlPath)) {
        throw "psql not found: $PsqlPath"
    }
    if ($DbPassword -ne "") { $env:PGPASSWORD = $DbPassword }
    & $PsqlPath -h $DbHost -p $DbPort -U $DbUser -d $DbName -t -A -c $Sql
}

function Sql-Escape {
    param([string]$Value)
    if ($Value -eq $null) { return "" }
    return $Value.Replace("'", "''")
}

function Set-Flag {
    param([string]$Key, [string]$Value)
    $k = (Sql-Escape $Key)
    $v = (Sql-Escape $Value)
    $sql = "INSERT INTO system_flags(key, value) VALUES ('$k', '$v') " +
           "ON CONFLICT (key) DO UPDATE SET value=EXCLUDED.value, updated_at=CURRENT_TIMESTAMP;"
    Invoke-Psql $sql | Out-Null
}

function Get-Scalar {
    param([string]$Sql)
    return ((Invoke-Psql $Sql | Out-String).Trim())
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

function Try-Upload {
    param([int64]$OrderId)
    try {
        Invoke-RestMethod -Method Post -Uri "$BaseUrl/ebay/tracking/$OrderId/upload" | Out-Null
        return $true
    } catch {
        return $false
    }
}

function Wait-For {
    param(
        [scriptblock]$Predicate,
        [int]$MaxSeconds = 90
    )
    for ($i = 0; $i -lt $MaxSeconds; $i++) {
        if (& $Predicate) { return $true }
        Start-Sleep -Seconds 1
    }
    return $false
}

function Get-FailedCount {
    param([int64]$OrderId)
    return [int](Get-Scalar "SELECT COUNT(*) FROM state_transitions WHERE entity_type='ORDER' AND entity_id=$OrderId AND reason_code='EBAY_TRACKING_UPLOAD_FAILED';")
}

function Get-RetryingCount {
    param([int64]$OrderId)
    return [int](Get-Scalar "SELECT COUNT(*) FROM state_transitions WHERE entity_type='ORDER' AND entity_id=$OrderId AND reason_code='EBAY_TRACKING_UPLOAD_RETRYING';")
}

function Get-ReasonCount {
    param([int64]$OrderId, [string]$ReasonCode)
    return [int](Get-Scalar "SELECT COUNT(*) FROM state_transitions WHERE entity_type='ORDER' AND entity_id=$OrderId AND reason_code='$(Sql-Escape $ReasonCode)';")
}

function Get-LastReason {
    param([int64]$OrderId)
    return Get-Scalar "SELECT reason_code FROM state_transitions WHERE entity_type='ORDER' AND entity_id=$OrderId ORDER BY created_at DESC, log_id DESC LIMIT 1;"
}

function Get-OrderState {
    param([int64]$OrderId)
    return Get-Scalar "SELECT state FROM orders WHERE order_id=$OrderId;"
}

function Get-OrderRetry {
    param([int64]$OrderId)
    $row = Get-Scalar "SELECT tracking_retry_count || '|' || COALESCE(tracking_next_retry_at::TEXT,'') || '|' || COALESCE(tracking_retry_terminal_at::TEXT,'') FROM orders WHERE order_id=$OrderId;"
    return $row
}

$started = $false
$proc = $null
$running = $false
try {
    Invoke-RestMethod -Method Get -Uri "$BaseUrl/ops/status" | Out-Null
    $running = $true
} catch {
    $running = $false
}

if (-not $running) {
    $gradle = Join-Path $RepoPath "gradlew.bat"
    $proc = Start-Process -FilePath $gradle -ArgumentList "bootRun" -WorkingDirectory $RepoPath -PassThru -WindowStyle Hidden
    $started = $true
}

$ready = $false
for ($i = 0; $i -lt 60; $i++) {
    try {
        Invoke-RestMethod -Method Get -Uri "$BaseUrl/ops/status" | Out-Null
        $ready = $true
        break
    } catch {
        Start-Sleep -Seconds 2
    }
}
if (-not $ready) { throw "app not ready" }

$flagKeys = @(
    "EBAY_STUB_TRACKING_FAIL_ORDER_PREFIX",
    "EBAY_STUB_TRACKING_MISSING_ORDER_PREFIX",
    "EBAY_TRACKING_RETRY_MAX_ATTEMPTS",
    "EBAY_TRACKING_RETRY_MAX_AGE_MINUTES",
    "EBAY_TRACKING_RETRY_BASE_DELAY_SECONDS",
    "EBAY_TRACKING_RETRY_MAX_DELAY_SECONDS",
    "EBAY_TRACKING_RETRY_BATCH_LIMIT"
)

$flagState = @{}
foreach ($k in $flagKeys) {
    $cnt = [int](Get-Scalar "SELECT COUNT(*) FROM system_flags WHERE key='$(Sql-Escape $k)';")
    $val = Get-Scalar "SELECT value FROM system_flags WHERE key='$(Sql-Escape $k)';"
    $flagState[$k] = @{ exists = ($cnt -gt 0); value = $val }
}

$status = Invoke-RestMethod -Method Get -Uri "$BaseUrl/ops/status"
$wasPaused = [bool]$status.paused
$pauseReason = [string]$status.reason
if ($wasPaused) {
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/ops/resume" -Headers @{ "X-OPS-KEY" = $OpsKey } | Out-Null
}

$orderIds = @()
$pauseOrderIds = @()

try {
    # 1) Retry then success
    Set-Flag "EBAY_STUB_TRACKING_FAIL_ORDER_PREFIX" "ORDER-RETRY-SUCCESS"
    Set-Flag "EBAY_STUB_TRACKING_MISSING_ORDER_PREFIX" "ORDER-RETRY-SUCCESS"
    Set-Flag "EBAY_TRACKING_RETRY_MAX_ATTEMPTS" "3"
    Set-Flag "EBAY_TRACKING_RETRY_MAX_AGE_MINUTES" "10"
    Set-Flag "EBAY_TRACKING_RETRY_BASE_DELAY_SECONDS" "1"
    Set-Flag "EBAY_TRACKING_RETRY_MAX_DELAY_SECONDS" "1"
    Set-Flag "EBAY_TRACKING_RETRY_BATCH_LIMIT" "20"

    $orderKey = "ORDER-RETRY-SUCCESS-001"
    $id1 = New-SoldOrder $orderKey 100 150
    $orderIds += $id1
    Import-Tracking $id1 "JapanPost" "TRACK-RETRY-SUCCESS-001"
    $firstOk = Try-Upload $id1

    Set-Flag "EBAY_STUB_TRACKING_FAIL_ORDER_PREFIX" "NO_MATCH"
    Set-Flag "EBAY_STUB_TRACKING_MISSING_ORDER_PREFIX" "NO_MATCH"

    $ready1 = Wait-For { (Get-OrderState $id1) -eq "EBAY_TRACKING_UPLOADED" } 120
    $retry1 = Get-OrderRetry $id1
    $failed1 = Get-FailedCount $id1
    $lastReason1 = Get-LastReason $id1
    "TEST1 firstOk=$firstOk uploaded=$ready1 failed=$failed1 lastReason=$lastReason1 retry=$retry1"

    # 3) Idempotent success
    Set-Flag "EBAY_STUB_TRACKING_FAIL_ORDER_PREFIX" "NO_MATCH"
    Set-Flag "EBAY_STUB_TRACKING_MISSING_ORDER_PREFIX" "NO_MATCH"

    $id3s = New-SoldOrder "ORDER-IDEMP-SUCCESS-001" 100 150
    $orderIds += $id3s
    Import-Tracking $id3s "JapanPost" "TRACK-IDEMP-SUCCESS-001"
    $ok3s = Try-Upload $id3s
    $uploadedBefore = Get-ReasonCount $id3s "EBAY_TRACKING_UPLOADED"
    1..3 | ForEach-Object { [void](Try-Upload $id3s) }
    $failedAfter3s = Get-FailedCount $id3s
    $uploadedAfter = Get-ReasonCount $id3s "EBAY_TRACKING_UPLOADED"
    "TEST3-SUCCESS ok=$ok3s failed=$failedAfter3s uploadedBefore=$uploadedBefore uploadedAfter=$uploadedAfter"

    # 3) Idempotent failure after terminal
    Set-Flag "EBAY_STUB_TRACKING_FAIL_ORDER_PREFIX" "ORDER-IDEMP-FAIL"
    Set-Flag "EBAY_STUB_TRACKING_MISSING_ORDER_PREFIX" "ORDER-IDEMP-FAIL"
    Set-Flag "EBAY_TRACKING_RETRY_MAX_ATTEMPTS" "1"
    Set-Flag "EBAY_TRACKING_RETRY_BASE_DELAY_SECONDS" "1"
    Set-Flag "EBAY_TRACKING_RETRY_MAX_DELAY_SECONDS" "1"

    $id3f = New-SoldOrder "ORDER-IDEMP-FAIL-001" 100 150
    $orderIds += $id3f
    Import-Tracking $id3f "JapanPost" "TRACK-IDEMP-FAIL-001"
    [void](Try-Upload $id3f)
    $terminalReady = Wait-For { (Get-Scalar "SELECT tracking_retry_terminal_at FROM orders WHERE order_id=$id3f;") -ne "" } 120
    $failedBefore3f = Get-FailedCount $id3f
    1..3 | ForEach-Object { [void](Try-Upload $id3f) }
    $failedAfter3f = Get-FailedCount $id3f
    "TEST3-FAILED terminal=$terminalReady failedBefore=$failedBefore3f failedAfter=$failedAfter3f"

    # 4) Boundary check 4 vs 5
    Set-Flag "EBAY_STUB_TRACKING_FAIL_ORDER_PREFIX" "ORDER-PAUSE"
    Set-Flag "EBAY_STUB_TRACKING_MISSING_ORDER_PREFIX" "ORDER-PAUSE"
    Set-Flag "EBAY_TRACKING_RETRY_MAX_ATTEMPTS" "1"
    Set-Flag "EBAY_TRACKING_RETRY_BASE_DELAY_SECONDS" "1"
    Set-Flag "EBAY_TRACKING_RETRY_MAX_DELAY_SECONDS" "1"

    $baseline = [int](Get-Scalar "SELECT COUNT(DISTINCT entity_id) FROM state_transitions WHERE entity_type='ORDER' AND reason_code='EBAY_TRACKING_UPLOAD_FAILED' AND created_at >= NOW() - INTERVAL '15 minutes';")
    if ($baseline -ge 5) {
        "TEST4 SKIP baseline=$baseline already>=5"
    } else {
        $needNoPause = [Math]::Max(0, 4 - $baseline)
        $needPause = [Math]::Max(0, 5 - $baseline)

        for ($i = 1; $i -le $needNoPause; $i++) {
            $key = ("ORDER-PAUSE-{0:000}" -f $i)
            $id = New-SoldOrder $key 100 150
            $orderIds += $id
            $pauseOrderIds += $id
            Import-Tracking $id "JapanPost" ("TRACK-PAUSE-{0:000}" -f $i)
            [void](Try-Upload $id)
        }

        if ($needNoPause -gt 0) {
            [void](Wait-For {
                $cnt = [int](Get-Scalar "SELECT COUNT(*) FROM state_transitions WHERE entity_type='ORDER' AND reason_code='EBAY_TRACKING_UPLOAD_FAILED' AND entity_id IN ($([string]::Join(',', $pauseOrderIds)));")
                $cnt -ge $needNoPause
            } 120)
        }

        Start-Sleep -Seconds 12
        $status4 = Invoke-RestMethod -Method Get -Uri "$BaseUrl/ops/status"
        "TEST4-4 paused=$($status4.paused) reason=$($status4.reason) baseline=$baseline added=$needNoPause"

        for ($i = $needNoPause + 1; $i -le $needPause; $i++) {
            $key = ("ORDER-PAUSE-{0:000}" -f $i)
            $id = New-SoldOrder $key 100 150
            $orderIds += $id
            $pauseOrderIds += $id
            Import-Tracking $id "JapanPost" ("TRACK-PAUSE-{0:000}" -f $i)
            [void](Try-Upload $id)
        }

        if ($needPause -gt $needNoPause) {
            [void](Wait-For {
                $cnt = [int](Get-Scalar "SELECT COUNT(*) FROM state_transitions WHERE entity_type='ORDER' AND reason_code='EBAY_TRACKING_UPLOAD_FAILED' AND entity_id IN ($([string]::Join(',', $pauseOrderIds)));")
                $cnt -ge $needPause
            } 120)
        }

        $paused = Wait-For { (Invoke-RestMethod -Method Get -Uri "$BaseUrl/ops/status").paused } 60
        $status5 = Invoke-RestMethod -Method Get -Uri "$BaseUrl/ops/status"
        "TEST4-5 paused=$($status5.paused) reason=$($status5.reason) baseline=$baseline added=$needPause waited=$paused"
    }
} finally {
    foreach ($k in $flagKeys) {
        $state = $flagState[$k]
        if ($state.exists) {
            $val = (Sql-Escape $state.value)
            Invoke-Psql "INSERT INTO system_flags(key, value, updated_at) VALUES ('$k', '$val', CURRENT_TIMESTAMP) ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at;" | Out-Null
        } else {
            Invoke-Psql "DELETE FROM system_flags WHERE key='$(Sql-Escape $k)';" | Out-Null
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

    if ($started -and $proc -ne $null) {
        Stop-Process -Id $proc.Id -Force
    }
}
