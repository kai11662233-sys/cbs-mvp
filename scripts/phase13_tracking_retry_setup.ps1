param(
    [string]$PsqlPath = "psql",
    [string]$DbHost = "localhost",
    [string]$DbPort = "5432",
    [string]$DbUser = "cbs",
    [string]$DbName = "cbs_mvp",
    [string]$DbPassword = "",
    [int]$RetryMaxAttempts = 5,
    [int]$RetryMaxAgeMinutes = 60,
    [int]$RetryBaseDelaySeconds = 60,
    [int]$RetryMaxDelaySeconds = 900,
    [int]$RetryBatchLimit = 20
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

$ddl = @"
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tracking_retry_count INT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tracking_retry_started_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tracking_next_retry_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tracking_retry_last_error TEXT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tracking_retry_terminal_at TIMESTAMP;
"@

Invoke-Psql $ddl | Out-Null

Set-Flag "EBAY_TRACKING_RETRY_MAX_ATTEMPTS" $RetryMaxAttempts
Set-Flag "EBAY_TRACKING_RETRY_MAX_AGE_MINUTES" $RetryMaxAgeMinutes
Set-Flag "EBAY_TRACKING_RETRY_BASE_DELAY_SECONDS" $RetryBaseDelaySeconds
Set-Flag "EBAY_TRACKING_RETRY_MAX_DELAY_SECONDS" $RetryMaxDelaySeconds
Set-Flag "EBAY_TRACKING_RETRY_BATCH_LIMIT" $RetryBatchLimit

"tracking retry schema/flags applied"
