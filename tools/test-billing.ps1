param([string]$Maven = 'mvn.cmd')
$ErrorActionPreference = 'Stop'
$billingRoot = Split-Path -Parent $PSScriptRoot
$schema = Get-Content -LiteralPath (Join-Path $billingRoot 'database/init.sql') -Raw -Encoding UTF8
$statements = @('CREATE DATABASE IF NOT EXISTS water_meter_billing_test CHARACTER SET utf8mb4;', 'USE water_meter_billing_test;')
$tableMatches = [regex]::Matches($schema, '(?s)CREATE TABLE IF NOT EXISTS\s+`?\w+`?\s*\(.*?;')
if ($tableMatches.Count -lt 11) { throw '测试表定义不完整' }
foreach ($tableMatch in $tableMatches) { $statements += $tableMatch.Value }
Get-ChildItem -LiteralPath (Join-Path $billingRoot 'database/migrations') -Filter '*.sql' | Sort-Object Name | ForEach-Object {
    $statements += Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8
}
$logDir = Join-Path $billingRoot 'logs'
New-Item -ItemType Directory -Path $logDir -Force | Out-Null
$testSchema = Join-Path $logDir 'billing-test-schema.sql'
[IO.File]::WriteAllText($testSchema, ($statements -join "`n"), [Text.UTF8Encoding]::new($false))
docker cp $testSchema water-mysql:/tmp/billing-test-schema.sql
if ($LASTEXITCODE -ne 0) { throw '测试数据库脚本复制失败' }
docker exec water-mysql sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -uroot --default-character-set=utf8mb4 < /tmp/billing-test-schema.sql'
if ($LASTEXITCODE -ne 0) { throw '测试数据库准备失败' }
$previousOptIn = $env:BILLING_MYSQL_TESTS
Push-Location (Join-Path $billingRoot 'water-service')
try {
    $env:BILLING_MYSQL_TESTS = 'true'
    & $Maven '-Dmaven.repo.local=../.m2repository' test
    if ($LASTEXITCODE -ne 0) { throw '账务测试未通过' }
} finally {
    $env:BILLING_MYSQL_TESTS = $previousOptIn
    Pop-Location
}
