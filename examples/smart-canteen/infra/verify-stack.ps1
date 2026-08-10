param(
    [string]$EnvFile = (Join-Path $PSScriptRoot ".env")
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $EnvFile)) {
    throw "Environment file not found: $EnvFile. Copy .env.example to .env and replace every placeholder first."
}

docker compose --env-file $EnvFile -f (Join-Path $PSScriptRoot "compose.yaml") `
    up --detach --build --wait
if ($LASTEXITCODE -ne 0) {
    throw "Middleware stack failed to build or become healthy."
}

docker compose --env-file $EnvFile -f (Join-Path $PSScriptRoot "compose.yaml") ps
if ($LASTEXITCODE -ne 0) {
    throw "Unable to read middleware container status."
}
