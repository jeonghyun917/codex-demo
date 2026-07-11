[CmdletBinding()]
param(
    [string]$MariaDbBin = ".tools\mariadb-11.8.2-winx64\bin",
    [string]$SourceHost = "127.0.0.1",
    [int]$SourcePort = 3306,
    [string]$SourceDatabase = "king_yurina",
    [string]$SourceUser = "root",
    [string]$SourcePassword = "",
    [string]$OutputFile = "target\railway-full.sql",
    [string]$ManifestFile = "target\railway-full-manifest.json",
    [switch]$Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$mysql = Join-Path $MariaDbBin "mariadb.exe"
$dump = Join-Path $MariaDbBin "mariadb-dump.exe"

foreach ($requiredFile in @($mysql, $dump)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Required MariaDB executable not found: $requiredFile"
    }
}

$outputPath = [System.IO.Path]::GetFullPath($OutputFile)
$manifestPath = [System.IO.Path]::GetFullPath($ManifestFile)
$partialPath = "$outputPath.partial"

foreach ($path in @($outputPath, $manifestPath, $partialPath)) {
    if ((Test-Path -LiteralPath $path) -and -not $Force) {
        throw "Output already exists: $path. Use -Force to replace generated migration files."
    }
}

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $outputPath) | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $manifestPath) | Out-Null

if ($Force) {
    Remove-Item -LiteralPath $outputPath, $manifestPath, $partialPath -Force -ErrorAction SilentlyContinue
}

$databaseLiteral = $SourceDatabase.Replace("'", "''")
$databaseIdentifier = $SourceDatabase.Replace('`', '``')
$oldPassword = $env:MYSQL_PWD

function Invoke-SourceQuery {
    param([Parameter(Mandatory = $true)][string]$Sql)

    $queryOutput = & $mysql `
        "--host=$SourceHost" `
        "--port=$SourcePort" `
        --protocol=tcp `
        --ssl=0 `
        "--user=$SourceUser" `
        --batch `
        --raw `
        --skip-column-names `
        "--execute=$Sql" 2>&1

    if ($LASTEXITCODE -ne 0) {
        throw "MariaDB query failed: $($queryOutput -join [Environment]::NewLine)"
    }

    return @($queryOutput)
}

try {
    if ($SourcePassword -ne "") {
        $env:MYSQL_PWD = $SourcePassword
    } else {
        Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    }

    $serverVersion = (Invoke-SourceQuery -Sql "SELECT VERSION();" | Select-Object -First 1).ToString()
    $metadataLines = Invoke-SourceQuery -Sql @"
SELECT TABLE_NAME, ENGINE, COALESCE(DATA_LENGTH, 0), COALESCE(INDEX_LENGTH, 0)
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = '$databaseLiteral'
  AND TABLE_TYPE = 'BASE TABLE'
ORDER BY TABLE_NAME;
"@

    if ($metadataLines.Count -eq 0) {
        throw "Source database has no base tables: $SourceDatabase"
    }

    $tables = @()
    $tableNumber = 0
    foreach ($line in $metadataLines) {
        $columns = $line.ToString().Split("`t")
        if ($columns.Count -ne 4) {
            throw "Unexpected metadata row: $line"
        }

        $tableNumber++
        $tableName = $columns[0]
        $engine = $columns[1]
        if ($engine -ne "InnoDB") {
            throw "Table $tableName uses $engine. A lock-free consistent dump requires every table to use InnoDB."
        }

        Write-Progress -Activity "Counting source rows" -Status $tableName -PercentComplete (($tableNumber / $metadataLines.Count) * 100)
        $tableIdentifier = $tableName.Replace('`', '``')
        $rowCount = (Invoke-SourceQuery -Sql "SELECT COUNT(*) FROM ``$databaseIdentifier``.``$tableIdentifier``;" | Select-Object -First 1)

        $tables += [pscustomobject][ordered]@{
            name = $tableName
            engine = $engine
            rowCount = [long]$rowCount
            dataBytes = [long]$columns[2]
            indexBytes = [long]$columns[3]
        }
    }
    Write-Progress -Activity "Counting source rows" -Completed

    Write-Host "Dumping $($tables.Count) InnoDB tables from $SourceDatabase..."
    & $dump `
        "--host=$SourceHost" `
        "--port=$SourcePort" `
        --protocol=tcp `
        --ssl=0 `
        "--user=$SourceUser" `
        --default-character-set=utf8mb4 `
        --single-transaction `
        --quick `
        --hex-blob `
        --skip-lock-tables `
        --skip-add-locks `
        --skip-dump-date `
        --no-tablespaces `
        --max-allowed-packet=1G `
        $SourceDatabase `
        "--result-file=$partialPath"

    if ($LASTEXITCODE -ne 0) {
        throw "Failed to dump source database: $SourceDatabase. Partial output remains at $partialPath"
    }

    Move-Item -LiteralPath $partialPath -Destination $outputPath -Force
    $dumpFile = Get-Item -LiteralPath $outputPath
    $sha256 = (Get-FileHash -LiteralPath $outputPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $totalRows = [long](($tables | Measure-Object -Property rowCount -Sum).Sum)
    $dataBytes = [long](($tables | Measure-Object -Property dataBytes -Sum).Sum)
    $indexBytes = [long](($tables | Measure-Object -Property indexBytes -Sum).Sum)

    $manifest = [ordered]@{
        formatVersion = 1
        generatedAtUtc = [DateTime]::UtcNow.ToString("o")
        source = [ordered]@{
            host = $SourceHost
            port = $SourcePort
            database = $SourceDatabase
            serverVersion = $serverVersion
        }
        dump = [ordered]@{
            fileName = $dumpFile.Name
            sizeBytes = [long]$dumpFile.Length
            sha256 = $sha256
        }
        totals = [ordered]@{
            tableCount = $tables.Count
            rowCount = $totalRows
            dataBytes = $dataBytes
            indexBytes = $indexBytes
        }
        tables = $tables
    }

    $manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $manifestPath -Encoding UTF8

    $sizeGiB = [Math]::Round($dumpFile.Length / 1GB, 3)
    Write-Host "Created full dump: $outputPath ($sizeGiB GiB)"
    Write-Host "Created manifest: $manifestPath ($($tables.Count) tables, $totalRows rows)"
    Write-Host "SHA-256: $sha256"
} finally {
    if ($null -eq $oldPassword) {
        Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    } else {
        $env:MYSQL_PWD = $oldPassword
    }
}
