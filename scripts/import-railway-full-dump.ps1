[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$HostName,

    [int]$Port = 3306,
    [string]$Database = "king_yurina",
    [string]$User = "king_yurina",
    [string]$Password = "",
    [string]$SecretsFile = "target\railway-migration-secrets.json",
    [string]$MariaDbBin = ".tools\mariadb-11.8.2-winx64\bin",
    [string]$InputFile = "target\railway-full.sql",
    [string]$ManifestFile = "target\railway-full-manifest.json",
    [string]$VerificationFile = "target\railway-import-verification.json",
    [switch]$AllowOverwrite
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$mysql = Join-Path $MariaDbBin "mariadb.exe"
if (-not (Test-Path -LiteralPath $mysql -PathType Leaf)) {
    throw "mariadb.exe not found: $mysql"
}

foreach ($requiredFile in @($InputFile, $ManifestFile)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Migration file not found: $requiredFile"
    }
}

if ($Password -eq "" -and (Test-Path -LiteralPath $SecretsFile -PathType Leaf)) {
    $secrets = Get-Content -LiteralPath $SecretsFile -Raw | ConvertFrom-Json
    if ($null -ne $secrets.appPassword) {
        $Password = $secrets.appPassword.ToString()
    }
}
if ($Password -eq "") {
    throw "Database password is required. Pass -Password or provide appPassword in $SecretsFile."
}

$inputPath = [System.IO.Path]::GetFullPath($InputFile)
$manifestPath = [System.IO.Path]::GetFullPath($ManifestFile)
$verificationPath = [System.IO.Path]::GetFullPath($VerificationFile)
$sourceCommandPath = $inputPath.Replace('\', '/')
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json

$actualHash = (Get-FileHash -LiteralPath $inputPath -Algorithm SHA256).Hash.ToLowerInvariant()
$expectedHash = $manifest.dump.sha256.ToString().ToLowerInvariant()
if ($actualHash -ne $expectedHash) {
    throw "Dump SHA-256 mismatch. Expected $expectedHash but found $actualHash."
}

$databaseLiteral = $Database.Replace("'", "''")
$databaseIdentifier = $Database.Replace('`', '``')
$oldPassword = $env:MYSQL_PWD

function Invoke-TargetQuery {
    param([Parameter(Mandatory = $true)][string]$Sql)

    $queryOutput = & $mysql `
        "--host=$HostName" `
        "--port=$Port" `
        --protocol=tcp `
        --ssl=0 `
        --compress `
        "--user=$User" `
        --batch `
        --raw `
        --skip-column-names `
        "--execute=$Sql" 2>&1

    if ($LASTEXITCODE -ne 0) {
        throw "Railway MariaDB query failed: $($queryOutput -join [Environment]::NewLine)"
    }

    return @($queryOutput)
}

try {
    $env:MYSQL_PWD = $Password

    $existingTableCount = [int](Invoke-TargetQuery -Sql "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = '$databaseLiteral';" | Select-Object -First 1)
    if ($existingTableCount -gt 0 -and -not $AllowOverwrite) {
        throw "Target database already contains $existingTableCount tables. Use -AllowOverwrite only when replacing a partial or intentional prior import."
    }

    Write-Host "Importing verified dump into $HostName`:$Port/$Database using MariaDB protocol compression..."
    & $mysql `
        "--host=$HostName" `
        "--port=$Port" `
        --protocol=tcp `
        --ssl=0 `
        --compress `
        --reconnect `
        "--user=$User" `
        --default-character-set=utf8mb4 `
        --max-allowed-packet=1G `
        $Database `
        "--execute=source $sourceCommandPath"

    if ($LASTEXITCODE -ne 0) {
        throw "Full dump import failed. The target may be partially populated; retry with -AllowOverwrite after resolving the error."
    }

    $targetTables = @(Invoke-TargetQuery -Sql @"
SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = '$databaseLiteral'
  AND TABLE_TYPE = 'BASE TABLE'
ORDER BY TABLE_NAME;
"@)
    $sourceTableNames = @($manifest.tables | ForEach-Object { $_.name.ToString() })
    $missingTables = @($sourceTableNames | Where-Object { $_ -notin $targetTables })
    $extraTables = @($targetTables | Where-Object { $_ -notin $sourceTableNames })
    $rowCountMismatches = @()
    $verifiedTables = @()
    $tableNumber = 0

    foreach ($sourceTable in $manifest.tables) {
        $tableNumber++
        $tableName = $sourceTable.name.ToString()
        if ($tableName -in $missingTables) {
            continue
        }

        Write-Progress -Activity "Verifying Railway row counts" -Status $tableName -PercentComplete (($tableNumber / $manifest.tables.Count) * 100)
        $tableIdentifier = $tableName.Replace('`', '``')
        $targetRowCount = [long](Invoke-TargetQuery -Sql "SELECT COUNT(*) FROM ``$databaseIdentifier``.``$tableIdentifier``;" | Select-Object -First 1)
        $sourceRowCount = [long]$sourceTable.rowCount

        $verifiedTables += [pscustomobject][ordered]@{
            name = $tableName
            sourceRowCount = $sourceRowCount
            targetRowCount = $targetRowCount
        }

        if ($sourceRowCount -ne $targetRowCount) {
            $rowCountMismatches += [pscustomobject][ordered]@{
                name = $tableName
                sourceRowCount = $sourceRowCount
                targetRowCount = $targetRowCount
            }
        }
    }
    Write-Progress -Activity "Verifying Railway row counts" -Completed

    $verification = [ordered]@{
        verifiedAtUtc = [DateTime]::UtcNow.ToString("o")
        target = [ordered]@{
            host = $HostName
            port = $Port
            database = $Database
        }
        dumpSha256 = $actualHash
        expectedTableCount = $sourceTableNames.Count
        actualTableCount = $targetTables.Count
        missingTables = $missingTables
        extraTables = $extraTables
        rowCountMismatches = $rowCountMismatches
        tables = $verifiedTables
    }

    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $verificationPath) | Out-Null
    $verification | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $verificationPath -Encoding UTF8

    if ($missingTables.Count -gt 0 -or $extraTables.Count -gt 0 -or $rowCountMismatches.Count -gt 0) {
        throw "Import verification failed. See $verificationPath for table and row-count differences."
    }

    $verifiedRows = [long](($verifiedTables | Measure-Object -Property targetRowCount -Sum).Sum)
    Write-Host "Import verified: $($targetTables.Count) tables and $verifiedRows rows match the source manifest."
    Write-Host "Verification report: $verificationPath"
} finally {
    if ($null -eq $oldPassword) {
        Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    } else {
        $env:MYSQL_PWD = $oldPassword
    }
}
