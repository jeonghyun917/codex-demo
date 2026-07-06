param(
    [Parameter(Mandatory = $true)]
    [string]$HostName,

    [int]$Port = 3306,
    [string]$Database = "king_yurina",
    [string]$User = "king_yurina",
    [string]$Password = "",

    [string]$MariaDbBin = ".tools\mariadb-11.8.2-winx64\bin",
    [string]$InputFile = "target\railway-minimal-seed.sql"
)

$ErrorActionPreference = "Stop"

$mysql = Join-Path $MariaDbBin "mariadb.exe"

if (-not (Test-Path $mysql)) {
    throw "mariadb.exe not found: $mysql"
}
if (-not (Test-Path $InputFile)) {
    throw "Input dump not found: $InputFile"
}

$oldPassword = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = $Password

    & $mysql `
        --host=$HostName `
        --port=$Port `
        --ssl=0 `
        --user=$User `
        --default-character-set=utf8mb4 `
        $Database `
        "--execute=source $InputFile"

    if ($LASTEXITCODE -ne 0) {
        throw "Failed to import $InputFile into $HostName`:$Port/$Database"
    }

    Write-Host "Imported $InputFile into $HostName`:$Port/$Database"
} finally {
    if ($null -eq $oldPassword) {
        Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    } else {
        $env:MYSQL_PWD = $oldPassword
    }
}
