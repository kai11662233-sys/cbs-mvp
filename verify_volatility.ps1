$ErrorActionPreference = "Stop"
$baseUrl = "http://localhost:8080"
$adminUser = "admin"
$adminPass = "password"

# 1. Login
Write-Host "1. Logging in..." -ForegroundColor Cyan
try {
    $loginBody = @{ username = $adminUser; password = $adminPass } | ConvertTo-Json
    $loginRes = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
    $token = $loginRes.token
    if (-not $token) { throw "No token received" }
    Write-Host "   Success. Token acquired." -ForegroundColor Green
} catch {
    Write-Error "Login failed: $_"
    exit 1
}

$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type"  = "application/json"
}

$opsHeaders = $headers.Clone()
$opsHeaders["X-OPS-KEY"] = "dev-ops-key"

# 2. Set FX Safe Rate (150.00)
Write-Host "`n2. Setting Safe FX Rate (150.00)..." -ForegroundColor Cyan
try {
    Invoke-RestMethod -Uri "$baseUrl/ops/flags/FX_MANUAL_RATE" -Method Post -Body (@{ value = "150.00" } | ConvertTo-Json) -Headers $opsHeaders | Out-Null
    Write-Host "   FX set to 150.00" -ForegroundColor Green
} catch {
    Write-Error "Failed to set FX safe: $_"
    exit 1
}

# 3. Create Candidate
Write-Host "`n3. Creating Test Candidate..." -ForegroundColor Cyan
try {
    $createBody = @{
        sourceUrl      = "http://test.com/volatility_verify"
        sourcePriceYen = 5000
        weightKg       = 1.0
        sizeTier       = "S"
        memo           = "VolatilityTest"
    } | ConvertTo-Json
    $cand = Invoke-RestMethod -Uri "$baseUrl/candidates" -Method Post -Body $createBody -Headers $headers
    $cid = $cand.candidateId
    Write-Host "   Created Candidate ID: $cid (State: $($cand.state))" -ForegroundColor Green
} catch {
    Write-Error "Create failed: $_"
    exit 1
}

# 4. Price Candidate (Initial Pricing)
Write-Host "`n4. Pricing Candidate (Profit Calculation)..." -ForegroundColor Cyan
try {
    $priceBody = @{
        fxRate        = 150.00
        targetSellUsd = $null # Auto calc
        autoDraft     = $false
    } | ConvertTo-Json
    $priceRes = Invoke-RestMethod -Uri "$baseUrl/candidates/$cid/pricing" -Method Post -Body $priceBody -Headers $headers
    
    if ($priceRes.gateProfitOk) {
        Write-Host "   Pricing Result: Profit OK. State should be DRAFT_READY." -ForegroundColor Green
    } else {
        Write-Error "   Pricing Failed unexpectedly! Profit Gate NG." 
        exit 1
    }
} catch {
    Write-Error "Pricing failed: $_"
    exit 1
}

# 5. Set Volatile FX Rate (100.00)
Write-Host "`n5. Setting Volatile FX Rate (100.00)..." -ForegroundColor Cyan
try {
    Invoke-RestMethod -Uri "$baseUrl/ops/flags/FX_MANUAL_RATE" -Method Post -Body (@{ value = "100.00" } | ConvertTo-Json) -Headers $opsHeaders | Out-Null
    Write-Host "   FX set to 100.00 (Drastic Drop)" -ForegroundColor Green
} catch {
    Write-Error "Failed to set FX volatile: $_"
    exit 1
}

# 6. Trigger FX Refresh (Auto-Recalc)
Write-Host "`n6. Triggering FX Refresh & Auto-Recalc..." -ForegroundColor Cyan
try {
    # Call refresh, which should trigger recalcAllActiveCandidates
    $refreshRes = Invoke-RestMethod -Uri "$baseUrl/fx/refresh" -Method Post -Headers $headers
    Write-Host "   Refresh Result: Success=$($refreshRes.success), Rate=$($refreshRes.rate)" -ForegroundColor Green
} catch {
    Write-Error "Refresh failed: $_"
    exit 1
}

# 7. Verify Candidate Status REJECTED
Write-Host "`n7. Verifying Candidate Status (Expect REJECTED)..." -ForegroundColor Cyan
try {
    # Fetch candidate list and find our ID
    $list = Invoke-RestMethod -Uri "$baseUrl/candidates?limit=10" -Method Get -Headers $headers
    $target = $list | Where-Object { $_.candidateId -eq $cid }
    
    if ($target) {
        Write-Host "   Candidate ID $cid State: $($target.state)"
        if ($target.state -match "REJECTED") {
             Write-Host "   SUCCESS: Candidate was auto-rejected as expected!" -ForegroundColor Green -BackgroundColor Black
        } else {
             Write-Error "   FAILURE: Candidate state is $($target.state), expected REJECTED."
             exit 1
        }
    } else {
        Write-Error "   Candidate ID $cid not found in list."
        exit 1
    }
} catch {
    Write-Error "Verification failed: $_"
    exit 1
}
