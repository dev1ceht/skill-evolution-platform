param(
    [string]$EnvFile = (Join-Path $PSScriptRoot ".env")
)

$ErrorActionPreference = "Stop"
$EvidencePath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\outputs\verification\smart-canteen-mysql-workflow-latest.json"))

if (-not (Test-Path -LiteralPath $EnvFile)) {
    throw "Environment file not found: $EnvFile. Run verify-stack.ps1 first."
}

if ((Get-Content -Raw -LiteralPath $EnvFile -Encoding utf8) -match "replace-with-") {
    throw "Refusing to run integration tests with placeholder credentials in $EnvFile."
}

$composeFile = Join-Path $PSScriptRoot "compose.yaml"
$composeArgs = @("compose", "--env-file", $EnvFile, "-f", $composeFile)
$resolvedJson = & docker @composeArgs config --format json
if ($LASTEXITCODE -ne 0) {
    throw "Unable to resolve Docker Compose configuration from $EnvFile"
}
$resolvedJsonText = ($resolvedJson |
    Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) -join "`n"
$resolvedConfig = ConvertFrom-Json -InputObject $resolvedJsonText
$mysqlEnvironment = $resolvedConfig.services.mysql.environment
$mysqlPort = $resolvedConfig.services.mysql.ports |
    Where-Object { $_.target -eq 3306 } |
    Select-Object -First 1 -ExpandProperty published
$mysqlUser = [string]$mysqlEnvironment.MYSQL_USER
$mysqlPassword = [string]$mysqlEnvironment.MYSQL_PASSWORD
if ([string]::IsNullOrWhiteSpace($mysqlUser) -or [string]::IsNullOrWhiteSpace($mysqlPassword)) {
    throw "Resolved MySQL application credentials are incomplete."
}
if ([string]::IsNullOrWhiteSpace([string]$mysqlPort)) {
    throw "Resolved MySQL port mapping is missing."
}
if ($mysqlUser -notmatch "^[A-Za-z0-9_]+$") {
    throw "MYSQL_USER must contain only letters, digits, and underscores for integration provisioning."
}

$databaseName = "smart_canteen_it_" + ([guid]::NewGuid().ToString("N").Substring(0, 12))
if ($databaseName -notmatch "^smart_canteen_it_[a-f0-9]{12}$") {
    throw "Refusing to provision an unexpected database name: $databaseName"
}

$createSql = "CREATE DATABASE ``$databaseName`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL PRIVILEGES ON ``$databaseName``.* TO '$mysqlUser'@'%';"
$dropSql = "DROP DATABASE IF EXISTS ``$databaseName``;"

$previousEnvironment = @{}
$testEnvironment = @{
    SMART_CANTEEN_MYSQL_IT = "true"
    SMART_CANTEEN_DB_URL = "jdbc:mysql://127.0.0.1:$mysqlPort/$databaseName`?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&connectTimeout=5000&socketTimeout=30000"
    SMART_CANTEEN_DB_USERNAME = $mysqlUser
    SMART_CANTEEN_DB_PASSWORD = $mysqlPassword
}

$runError = $null
$cleanupError = $null
try {
    $createSql | & docker @composeArgs exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --user=root'
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to provision isolated MySQL integration database."
    }

    foreach ($entry in $testEnvironment.GetEnumerator()) {
        $previousEnvironment[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, "Process")
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, "Process")
    }

    Push-Location (Join-Path $PSScriptRoot "..\backend")
    try {
        & mvn -q "-Dtest=SmartCanteenMySqlIntegrationTest,AgentRuntimeMySqlIntegrationTest" test
        if ($LASTEXITCODE -ne 0) {
            throw "Real MySQL workflow and Agent Runtime integration tests failed."
        }
    } finally {
        Pop-Location
    }
} catch {
    $runError = $_
} finally {
    foreach ($entry in $previousEnvironment.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, "Process")
    }
    $dropSql | & docker @composeArgs exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --user=root'
    if ($LASTEXITCODE -ne 0) {
        $cleanupError = "Failed to remove isolated integration database $databaseName"
    }
}

if ($null -ne $runError -and $null -ne $cleanupError) {
    $result = "failed"
} elseif ($null -ne $runError -or $null -ne $cleanupError) {
    $result = "failed"
} else {
    $result = "passed"
}

$backendPom = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot "..\backend\pom.xml") -Encoding utf8
$flywayMatch = [regex]::Match($backendPom, "<flyway\.version>([^<]+)</flyway\.version>")
$flywayVersion = if ($flywayMatch.Success) { $flywayMatch.Groups[1].Value } else { "unknown" }
$evidenceError = $null
try {
    $evidenceDirectory = Split-Path -Parent $EvidencePath
    New-Item -ItemType Directory -Force -Path $evidenceDirectory | Out-Null
    [ordered]@{
        schemaVersion = 1
        recordedAt = (Get-Date).ToUniversalTime().ToString("o")
        result = $result
        database = $databaseName
        databaseRemoved = ($null -eq $cleanupError)
        mysqlImage = [string]$resolvedConfig.services.mysql.image
        flywayVersion = $flywayVersion
        testClasses = @(
            "SmartCanteenMySqlIntegrationTest"
            "AgentRuntimeMySqlIntegrationTest"
        )
        checks = @(
            "flyway migration on MySQL"
            "same-key concurrent receipt is applied once"
            "concurrent first-material receipts preserve both quantities"
            "incompatible unit rolls back receipt reservation"
            "state survives Spring application restart"
            "same-key concurrent Agent start is applied once"
            "Agent Run Step/Event evidence survives Spring application restart"
            "Agent plan audit evidence is durable"
            "same-transaction duplicate Agent start reuses the uncommitted winner"
            "outer transaction rollback leaves no Agent Run or audit orphan"
            "same-key different-payload Agent request is rejected"
            "concurrent Agent workers obtain one execution claim"
            "expired Agent claim can be replaced and the old token is fenced"
        )
    } | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $EvidencePath -Encoding utf8
    Write-Output "MySQL workflow evidence: $EvidencePath"
} catch {
    $evidenceError = $_
}

if ($null -ne $runError -and $null -ne $cleanupError) {
    $message = "$($runError.Exception.Message) Cleanup also failed: $cleanupError"
    if ($null -ne $evidenceError) {
        $message += " Evidence write also failed: $($evidenceError.Exception.Message)"
    }
    throw $message
}
if ($null -ne $runError) {
    if ($null -ne $evidenceError) {
        throw "$($runError.Exception.Message) Evidence write also failed: $($evidenceError.Exception.Message)"
    }
    throw $runError
}
if ($null -ne $cleanupError) {
    if ($null -ne $evidenceError) {
        throw "$cleanupError Evidence write also failed: $($evidenceError.Exception.Message)"
    }
    throw $cleanupError
}
if ($null -ne $evidenceError) {
    throw $evidenceError
}
