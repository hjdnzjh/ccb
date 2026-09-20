param([switch]$RunExperiments)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    $env:PYTHONUTF8 = '1'
    $env:PYTHONPATH = Join-Path $projectRoot 'agent-engine'
    & '.venv-local/Scripts/python.exe' -m unittest discover -s agent-engine/tests
    if ($LASTEXITCODE -ne 0) { throw 'Innovation Python tests failed.' }
    & './tools/test-billing.ps1'
    if ($LASTEXITCODE -ne 0) { throw 'Backend regression failed.' }
    Push-Location web-admin
    try {
        & 'D:/nodejs/node.exe' --test src/utils/billing.test.js src/utils/session.test.js src/utils/diagnosis.test.js
        if ($LASTEXITCODE -ne 0) { throw 'Frontend utility tests failed.' }
        & 'D:/nodejs/npm.cmd' run build
        if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
    } finally { Pop-Location }
    if ($RunExperiments) {
        $env:PYTHONPATH = Join-Path $projectRoot 'agent-engine'
        foreach ($experimentSeed in @(20260920,20260921,20260922)) {
            & '.venv-local/Scripts/python.exe' -m innovation.evaluate --seed $experimentSeed --meters 60 --days 84 --output "logs/innovation/evaluation-v2-$experimentSeed.json"
            if ($LASTEXITCODE -ne 0) { throw 'Evaluation failed.' }
        }
    }
} finally { Pop-Location }
