$body = @{ username="admin"; password="cbs" } | ConvertTo-Json
$res = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/auth/login" -Body $body -ContentType "application/json" -SkipHttpErrorCheck
if ($res.token) {
    $token = $res.token
    $detail = Invoke-RestMethod -Uri "http://localhost:8080/discovery/items/54" -Headers @{ Authorization="Bearer $token" } -SkipHttpErrorCheck
    $detail | ConvertTo-Json -Depth 10
} else {
    Write-Host "Login Failed. Using admin/admin instead."
    $body2 = @{ username="admin"; password="admin" } | ConvertTo-Json
    $res2 = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/auth/login" -Body $body2 -ContentType "application/json" -SkipHttpErrorCheck
    if ($res2.token) {
        $token = $res2.token
        $detail = Invoke-RestMethod -Uri "http://localhost:8080/discovery/items/54" -Headers @{ Authorization="Bearer $token" } -SkipHttpErrorCheck
        $detail | ConvertTo-Json -Depth 10
    } else {
        Write-Host "Both logins failed."
    }
}
