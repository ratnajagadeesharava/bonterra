# Reproducible MCP demo: builds the server jar, resets a separate demo database, then runs DemoClient,
# which launches the server once per identity (Priya, Arjun, Neha) and drives create -> approve -> pay -> summary.
# Output is shown and saved to docs/demo-output.txt. Run from the repository root:  .\scripts\demo.ps1
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

.\mvnw.cmd -q -B -DskipTests package
if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
.\mvnw.cmd -q -B test-compile dependency:build-classpath "-Dmdep.outputFile=target/test-classpath.txt" "-Dmdep.includeScope=test"
if ($LASTEXITCODE -ne 0) { throw 'Test classpath build failed' }

$env:CB_DB_PATH = 'data/demo.sqlite'
java --enable-native-access=ALL-UNNAMED -jar target/cb-mcp.jar --reset
if ($LASTEXITCODE -ne 0) { throw 'Reset failed' }

$cp = "target/test-classes;target/classes;" + (Get-Content target/test-classpath.txt -Raw).Trim()
java --enable-native-access=ALL-UNNAMED -cp $cp org.communitybridge.demo.DemoClient target/cb-mcp.jar data/demo.sqlite |
    Tee-Object -FilePath docs/demo-output.txt
