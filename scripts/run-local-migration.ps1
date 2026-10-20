<# Local development only; does not print or persist database credentials. #>
param([Parameter(Mandatory=$true)][ValidatePattern('^\d{3}-[a-z0-9-]+\.sql$')][string]$File)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
$migrationRoot=[IO.Path]::GetFullPath((Join-Path $project 'database/migrations'))
$migrationPath=[IO.Path]::GetFullPath((Join-Path $migrationRoot $File))
if(-not $migrationPath.StartsWith($migrationRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Migration must stay in database/migrations'}
$local=[IO.File]::ReadAllText((Join-Path $project 'backend/config/application-local.yml'))
$dbUser=[regex]::Match($local,'(?m)^\s*username:\s*["'']?([^\s"'']+)').Groups[1].Value
$dbPassword=[regex]::Match($local,'(?m)^\s*password:\s*["'']?([^\s"'']+)').Groups[1].Value
if(-not $dbUser -or -not $dbPassword){throw 'Local development database configuration is incomplete'}
$previous=$env:MYSQL_PWD;$env:MYSQL_PWD=$dbPassword
try {
 $sql=[IO.File]::ReadAllText($migrationPath)
 & 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' --host=127.0.0.1 --user=$dbUser --default-character-set=utf8mb4 --batch '--database=management-system' --execute=$sql
 if($LASTEXITCODE -ne 0){throw 'Migration failed; inspect schema before retrying'}
 Write-Output "MIGRATION_OK=$File DATABASE=management-system HOST=127.0.0.1"
} finally {if($null -eq $previous){Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue}else{$env:MYSQL_PWD=$previous};$dbPassword=$null;$local=$null}
