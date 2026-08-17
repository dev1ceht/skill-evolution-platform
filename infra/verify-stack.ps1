param(
    [string]$EnvFile = (Join-Path $PSScriptRoot ".env"),
    [string]$EvidenceName = "smart-canteen-runtime-latest.json"
)

$ErrorActionPreference = "Stop"
$evidenceRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\outputs\verification"))
if ($EvidenceName -notmatch "^smart-canteen-runtime-[A-Za-z0-9_-]+\.json$") {
    throw "EvidenceName must be a smart-canteen-runtime-*.json file name."
}
$evidencePath = Join-Path $evidenceRoot $EvidenceName
$composeArgs = @("compose", "--env-file", $EnvFile, "-f", (Join-Path $PSScriptRoot "compose.yaml"))

$stage = "input-validation"
$result = "failed"
$containers = @()
$images = @()
$runError = $null
try {
    if (-not (Test-Path -LiteralPath $EnvFile)) {
        throw "Environment file not found: $EnvFile. Copy .env.example to .env and replace every placeholder first."
    }

    $environmentText = Get-Content -Raw -LiteralPath $EnvFile -Encoding utf8
    if ($environmentText -match "replace-with-") {
        throw "Refusing to start with placeholder credentials. Replace every replace-with-* value in $EnvFile."
    }

    $stage = "compose-up"
    & docker @composeArgs up --detach --build --wait
    if ($LASTEXITCODE -ne 0) {
        throw "Middleware stack failed to build or become healthy."
    }

    $stage = "status-collection"
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
    $stage = "complete"
    $result = "passed"
} catch {
    $runError = $_
}

$failureStage = if ($null -eq $runError) { $null } else { $stage }
$failureMessage = if ($null -eq $runError) { $null } else { $runError.Exception.Message }
$evidenceError = $null
try {
    New-Item -ItemType Directory -Force -Path $evidenceRoot | Out-Null
    [ordered]@{
        schemaVersion = 1
        recordedAt = (Get-Date).ToUniversalTime().ToString("o")
        result = $result
        failureStage = $failureStage
        error = $failureMessage
        composeProject = "smart-canteen-infra"
        containers = $containers
        images = $images
    } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $evidencePath -Encoding utf8
    Write-Output "Runtime evidence: $evidencePath"
} catch {
    $evidenceError = $_
}

if ($null -ne $runError -and $null -ne $evidenceError) {
    throw "$($runError.Exception.Message) Evidence write also failed: $($evidenceError.Exception.Message)"
}
if ($null -ne $runError) {
    throw $runError
}
if ($null -ne $evidenceError) {
    throw $evidenceError
}
