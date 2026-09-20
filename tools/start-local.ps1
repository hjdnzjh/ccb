param(
    [switch]$SkipBuild,
    [string]$Java = 'D:\soft\Java\jdk-21\bin\java.exe',
    [string]$Maven = 'D:\Mavaen\apache-maven-3.9.11\bin\mvn.cmd',
    [string]$Node = 'D:\nodejs\node.exe'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$logs = Join-Path $projectRoot 'logs'
New-Item -ItemType Directory -Path $logs -Force | Out-Null
foreach ($port in @(3000,8080,8087)) {
    if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) { throw "端口 $port 已占用，请先核对并停止原项目服务。" }
}
foreach ($executable in @($Java,$Node,(Join-Path $projectRoot '.venv-local/Scripts/python.exe'))) {
    if (-not (Test-Path -LiteralPath $executable)) { throw "缺少运行环境：$executable；请先按 README 安装。" }
}
Push-Location $projectRoot
try {
    docker compose up -d
    if ($LASTEXITCODE -ne 0) { throw '请先启动 Docker Desktop，再运行本脚本。' }
    $ready = $false
    for ($attempt=0; $attempt -lt 30; $attempt++) {
        # Keep nested SQL quotes as single quotes: Windows PowerShell 5.1 strips
        # embedded double quotes when passing native executable arguments.
        try {
            docker compose exec -T mysql sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -uroot -N -e ''SELECT 1''' 2>$null | Out-Null
            if ($LASTEXITCODE -eq 0) { $ready=$true; break }
        } catch {
            # Native stderr is terminating with ErrorActionPreference=Stop in
            # Windows PowerShell 5.1. MySQL may still be starting; retry below.
        }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw 'MySQL 尚未就绪，请检查 docker compose logs mysql。' }
    foreach ($migration in (Get-ChildItem -LiteralPath (Join-Path $projectRoot 'database/migrations') -Filter '*.sql' | Sort-Object Name)) {
        docker cp $migration.FullName water-mysql:/tmp/ccb-migration.sql
        if($LASTEXITCODE -ne 0) { throw '复制迁移失败' }
        docker compose exec -T mysql sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -uroot --default-character-set=utf8mb4 water_meter_db < /tmp/ccb-migration.sql'
        if($LASTEXITCODE -ne 0) { throw "迁移失败：$($migration.Name)" }
    }
    if (-not $SkipBuild) {
        Push-Location (Join-Path $projectRoot 'water-service')
        try { & $Maven '-Dmaven.repo.local=../.m2repository' package; if($LASTEXITCODE -ne 0) { throw '后端构建失败' } }
        finally { Pop-Location }
    }
    $secretFile=Join-Path $logs 'agent-internal-token.txt'
    if(-not (Test-Path -LiteralPath $secretFile)) {
        $secretBytes=New-Object byte[] 32
        $generator=[Security.Cryptography.RandomNumberGenerator]::Create()
        try {$generator.GetBytes($secretBytes)} finally {$generator.Dispose()}
        [IO.File]::WriteAllText($secretFile,[Convert]::ToBase64String($secretBytes))
    }
    $env:AGENT_INTERNAL_TOKEN=[IO.File]::ReadAllText($secretFile).Trim()
    if($env:AGENT_INTERNAL_TOKEN.Length -lt 40) {throw '内部密钥文件无效，请重新生成。'}
    $env:PYTHONUTF8='1'; $env:FLASK_HOST='127.0.0.1'; $env:FLASK_DEBUG='False'
    $started=@()
    try {
        $started+=Start-Process -FilePath (Join-Path $projectRoot '.venv-local/Scripts/python.exe') -ArgumentList '-u','main.py' -WorkingDirectory (Join-Path $projectRoot 'agent-engine') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logs 'agent.stdout.log') -RedirectStandardError (Join-Path $logs 'agent.stderr.log') -PassThru
        $started+=Start-Process -FilePath $Java -ArgumentList '-Duser.timezone=Asia/Shanghai','-jar','target/water-meter-service-1.0.0.jar','--logging.file.name=../logs/water-meter-service.log' -WorkingDirectory (Join-Path $projectRoot 'water-service') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logs 'backend.stdout.log') -RedirectStandardError (Join-Path $logs 'backend.stderr.log') -PassThru
        $started+=Start-Process -FilePath $Node -ArgumentList 'node_modules/vite/bin/vite.js','--host','127.0.0.1','--strictPort' -WorkingDirectory (Join-Path $projectRoot 'web-admin') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logs 'frontend.stdout.log') -RedirectStandardError (Join-Path $logs 'frontend.stderr.log') -PassThru
        $started | Select-Object Id,ProcessName | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $logs 'local-processes.json') -Encoding UTF8
        Write-Output '已启动后台进程。打开 http://127.0.0.1:3000；请按 README 健康检查确认就绪。'
        $started | Select-Object Id,ProcessName
    } catch {
        foreach($startedProcess in $started) { Stop-Process -Id $startedProcess.Id -ErrorAction SilentlyContinue }
        throw
    }
} finally { Pop-Location }
