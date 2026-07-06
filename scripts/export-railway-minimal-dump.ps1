param(
    [string]$MariaDbBin = ".tools\mariadb-11.8.2-winx64\bin",
    [string]$SourceDatabase = "king_yurina",
    [string]$SourceUser = "root",
    [string]$SourcePassword = "",
    [string]$StagingDatabase = "king_yurina_railway_export",
    [string]$OutputFile = "target\railway-minimal-seed.sql"
)

$ErrorActionPreference = "Stop"

$mysql = Join-Path $MariaDbBin "mariadb.exe"
$dump = Join-Path $MariaDbBin "mariadb-dump.exe"
$recipe = "scripts\create-railway-minimal-export-db.sql"

if (-not (Test-Path $mysql)) {
    throw "mariadb.exe not found: $mysql"
}
if (-not (Test-Path $dump)) {
    throw "mariadb-dump.exe not found: $dump"
}
if (-not (Test-Path $recipe)) {
    throw "Export recipe not found: $recipe"
}

New-Item -ItemType Directory -Force -Path (Split-Path $OutputFile) | Out-Null

$oldPassword = $env:MYSQL_PWD
try {
    if ($SourcePassword -ne "") {
        $env:MYSQL_PWD = $SourcePassword
    } else {
        Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    }

    & $mysql --host=127.0.0.1 --port=3306 --ssl=0 --user=$SourceUser $SourceDatabase --execute="source $recipe"
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create staging database: $StagingDatabase"
    }

    & $dump `
        --host=127.0.0.1 `
        --port=3306 `
        --ssl=0 `
        --user=$SourceUser `
        --no-create-info `
        --skip-triggers `
        --skip-add-locks `
        --skip-disable-keys `
        --complete-insert `
        --insert-ignore `
        --compact `
        $StagingDatabase `
        "--result-file=$OutputFile"

    if ($LASTEXITCODE -ne 0) {
        throw "Failed to dump staging database: $StagingDatabase"
    }

    $sizeMb = [Math]::Round((Get-Item $OutputFile).Length / 1MB, 2)
    Write-Host "Created $OutputFile ($sizeMb MB)"
} finally {
    if ($null -eq $oldPassword) {
        Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    } else {
        $env:MYSQL_PWD = $oldPassword
    }
}
