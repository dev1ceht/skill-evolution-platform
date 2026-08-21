[CmdletBinding()]
param(
    [string]$EnvFile = (Join-Path $PSScriptRoot '..\infra\.env'),
    [string]$SeedFile = (Join-Path $PSScriptRoot '..\data\study\smart-canteen-study-seed.sql')
)

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$infraDir = Join-Path $repoRoot 'infra'
$composeFile = Join-Path $infraDir 'compose.yaml'

if (-not (Test-Path -LiteralPath $EnvFile)) {
    throw "Environment file not found: $EnvFile. Copy infra/.env.example to infra/.env first."
}
if (-not (Test-Path -LiteralPath $SeedFile)) {
    throw "Seed file not found: $SeedFile"
}

$composeArguments = @(
    '--env-file', (Resolve-Path -LiteralPath $EnvFile).Path,
    '-f', $composeFile,
    'exec', '-T', 'mysql', 'sh', '-c',
    'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --user="$MYSQL_USER" "$MYSQL_DATABASE"'
)

Write-Host "Loading study dataset from $SeedFile"
Get-Content -LiteralPath $SeedFile -Raw | & docker compose @composeArguments
if ($LASTEXITCODE -ne 0) {
    throw "Study dataset load failed with exit code $LASTEXITCODE"
}

Write-Host 'Study dataset loaded successfully.'
