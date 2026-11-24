# Replaces all data in the database with the synthetic seed (two nonprofits). Separate from normal startup.
# Uses CB_DB_PATH if set, otherwise data/cb.sqlite. Run from the repository root after building:  .\scripts\reset-db.ps1
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
if (-not (Test-Path target/cb-mcp.jar)) { .\mvnw.cmd -q -B -DskipTests package }
java --enable-native-access=ALL-UNNAMED -jar target/cb-mcp.jar --reset
exit $LASTEXITCODE
