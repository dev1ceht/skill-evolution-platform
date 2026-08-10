param(
    [string]$EnvFile = (Join-Path $PSScriptRoot ".env")
)

$ErrorActionPreference = "Stop"
$EvidencePath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\..\..\outputs\verification\smart-canteen-runtime-latest.json"))

if (-not (Test-Path -LiteralPath $EnvFile)) {
    throw "Environment file not found: $EnvFile. Copy .env.example to .env and replace every placeholder first."
}

$environmentText = Get-Content -Raw -LiteralPath $EnvFile -Encoding utf8
if ($environmentText -match "replace-with-") {
    throw "Refusing to start with placeholder credentials. Replace every replace-with-* value in $EnvFile."
}

$composeArgs = @("compose", "--env-file", $EnvFile, "-f", (Join-Path $PSScriptRoot "compose.yaml"))
& docker @composeArgs up --detach --build --wait
if ($LASTEXITCODE -ne 0) {
    throw "Middleware stack failed to build or become healthy."
}

& docker @composeArgs ps
if ($LASTEXITCODE -ne 0) {
    throw "Unable to read middleware container status."
}

$containerLines = & docker @composeArgs ps --format json
if ($LASTEXITCODE -ne 0) {
    throw "Unable to collect middleware container evidence."
}
$imageJson = & docker @composeArgs images --format json
if ($LASTEXITCODE -ne 0) {
    throw "Unable to collect middleware image evidence."
}
$containers = @($containerLines |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        ForEach-Object { ConvertFrom-Json -InputObject $_ } |
        Select-Object Service, Image, State, Health, ExitCode, Publishers)
$imageJsonText = ($imageJson |
    Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) -join "`n"
$parsedImages = ConvertFrom-Json -InputObject $imageJsonText
$images = @(
    foreach ($image in $parsedImages) {
        [ordered]@{
            Repository = $image.Repository
            Tag = $image.Tag
            ID = $image.ID
            Platform = $image.Platform
            Size = $image.Size
        }
    }
)

$evidenceDirectory = Split-Path -Parent $EvidencePath
New-Item -ItemType Directory -Force -Path $evidenceDirectory | Out-Null
[ordered]@{
    schemaVersion = 1
    recordedAt = (Get-Date).ToUniversalTime().ToString("o")
    result = "passed"
    composeProject = "smart-canteen-infra"
    containers = $containers
    images = $images
} | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $EvidencePath -Encoding utf8
Write-Output "Runtime evidence: $EvidencePath"
