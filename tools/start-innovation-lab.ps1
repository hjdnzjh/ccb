param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$logs = Join-Path $projectRoot 'logs'
$secretFile = Join-Path $logs 'agent-internal-token.txt'
if (Get-NetTCPConnection -State Listen -LocalPort 8088 -ErrorAction SilentlyContinue) { throw 'Port 8088 is already in use.' }
if (-not (Test-Path -LiteralPath $secretFile)) { throw 'Run tools/start-local.ps1 first to create the internal token.' }
$env:AGENT_INTERNAL_TOKEN = [IO.File]::ReadAllText($secretFile).Trim()
if ($env:AGENT_INTERNAL_TOKEN.Length -lt 40) { throw 'Invalid internal token.' }
if (-not (Test-Path -LiteralPath (Join-Path $logs 'innovation/deps/sklearn'))) { throw 'Install agent-engine/requirements-innovation.txt into logs/innovation/deps first. See README.' }
$env:PYTHONUTF8 = '1'
$labProcess = Start-Process -FilePath (Join-Path $projectRoot '.venv-local/Scripts/python.exe') -ArgumentList '-u','-m','innovation.lab' -WorkingDirectory (Join-Path $projectRoot 'agent-engine') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logs 'innovation-lab.stdout.log') -RedirectStandardError (Join-Path $logs 'innovation-lab.stderr.log') -PassThru
$labProcess | Select-Object Id,ProcessName | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $logs 'innovation/lab-process.json') -Encoding UTF8
Write-Output 'Lab process started on http://127.0.0.1:8088. Open /lab/replay in the administrator interface.'
