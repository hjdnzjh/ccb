# 水表抄表收费管理系统：本地启动指南

项目由 Vue 管理端、Spring Boot 业务后端、Flask 智能体引擎组成。Docker Compose **只启动五个中间件**，三个应用需要分别启动。

管理端：[http://127.0.0.1:3000](http://127.0.0.1:3000)。登录账号：`admin`，密码：`admin123`。

新增“诊断中心”和“创新演练”入口，串联诊断证据、有限复测、工单完成后核验、用户进度与消息提醒。**诊断默认关闭，当前复测为模拟采集；三组模型实验未达到效果门槛。** 完整启动、实验结果与实现边界见 [创新实施说明](docs/innovation-implementation.md)，测试对应关系见 [创新验收记录](docs/innovation-acceptance.md)。

常规服务启动后，可选运行 `& ./tools/start-innovation-lab.ps1` 开启8088隔离演练服务；首次安装实验依赖和完整复现命令见上述说明。`tools/start-local.ps1` 会应用新增诊断表迁移。新增两个PowerShell脚本使用UTF-8 BOM，兼容Windows PowerShell 5.1。

普通用户演示账号：`U004 / pass123`、`U006 / pass123`，登录后进入「我的用水工作台」，只能查看自己的数据。历史密码在首次正确登录后升级为带盐哈希；管理员新建或修改密码时至少 8 个字符。

**依赖和演示数据已准备好时，推荐一键启动：**

```powershell
& './tools/start-local.ps1'
```

命令末尾不要添加 `\`，它不是 PowerShell 的续行符。含中文的 `.ps1` 文件采用 **UTF-8 with BOM** 编码，兼容 Windows PowerShell 5.1 和 PowerShell 7；编辑器保存时应保留 BOM（仓库 `.editorconfig` 已指定）。如果中文提示出现乱码并伴随“字符串缺少终止符”，先检查脚本文件编码，修改终端代码页不能修复脚本解码问题。

脚本启动中间件、等待 MySQL、执行增量迁移、构建后端并在后台启动三个应用，同时生成和传入智能体内部密钥。默认 Java/Maven/Node 路径适用于本机，其他电脑可通过 `-Java`、`-Maven`、`-Node` 指定。JAR 已构建且代码未变时可加 `-SkipBuild`。先打开 Docker Desktop；端口被占用时脚本会停止并提示，不会结束其他进程。首次空库仍需按第 2 节导入演示数据。

以下命令面向 Windows PowerShell；路径含空格时必须保留引号。每个新终端先执行：

```powershell
$projectRoot = 'D:\AI-based water meter reading and fee management system\ccb'
Set-Location -LiteralPath $projectRoot
```

## 1. 环境准备

需要 Docker Desktop（Linux 容器）、JDK 17 或 21、Maven 3.9、Node.js 22、Python 3.10–3.12。
本机已用 JDK 21.0.7、Maven 3.9.11、Node 22.16.0、Python 3.12.7 验证启动。

```powershell
docker version
docker compose version
java -version
mvn.cmd -version
node --version
npm.cmd --version
python --version
```

本机 Maven 位于 `D:\Mavaen\apache-maven-3.9.11`。如果提示找不到 `mvn.cmd`，在当前终端执行：

```powershell
$env:Path = 'D:\Mavaen\apache-maven-3.9.11\bin;' + $env:Path
```

其他电脑请替换为自己的安装目录。使用 `npm.cmd` 可避免 PowerShell 对 `npm.ps1` 的执行策略限制。

## 2. 首次启动：中间件和演示数据

```powershell
docker compose config --quiet
docker compose up -d
docker compose ps
docker compose logs --tail 30 mysql
```

等 MySQL 日志显示 `ready for connections`，再执行以下初始化。`database/init.sql` 只在 MySQL 数据卷为空时自动运行，它不包含管理员账号；登录演示还需要 `seed_ops.sql`。

```powershell
docker cp ./database/seed_ops.sql water-mysql:/tmp/ccb-seed-ops.sql
docker compose exec -T mysql sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -uroot --default-character-set=utf8mb4 water_meter_db < /tmp/ccb-seed-ops.sql'

docker cp ./database/seed_hangzhou_map.sql water-mysql:/tmp/ccb-seed-map.sql
docker compose exec -T mysql sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -uroot --default-character-set=utf8mb4 water_meter_db < /tmp/ccb-seed-map.sql'
```

上述方式直接复制 UTF-8 文件，避免 PowerShell 文本管道导致中文乱码。每条命令应成功退出再继续。
`seed_ops.sql` 创建 `admin / admin123`、演示水表、抄表、账单及异常；重复执行会重建标记为 `SEED_OPS` 的样例记录。`seed_hangzhou_map.sql` 更新演示分区名称及坐标。**平常重启无需再次导入。**

本次新库已执行以上两份脚本。`seed_demo_showcase.sql` 和 CSV 导入工具是可选扩充数据步骤，不是启动前置条件；不要在已有业务库中随意执行重置样例的脚本。

### 已有数据库升级（收款流水）

已有数据卷不会重新执行 `init.sql`。首次更新到账务版本时，先检查重复账单：

```powershell
docker compose exec -T mysql sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -uroot water_meter_db -e ''SELECT reading_id,COUNT(*) AS count FROM bill WHERE deleted=0 AND reading_id IS NOT NULL GROUP BY reading_id HAVING COUNT(*)>1;'''
```

预期无结果行。如有重复，先核对原始抄表、账单和收款，不能直接删除。新后端也会检查并拒绝带有这些冲突的数据启动。
确认无冲突后执行增量迁移；新库已经包含此表，重复执行同样安全：

```powershell
docker cp ./database/migrations/20260917_bill_payment.sql water-mysql:/tmp/ccb-bill-payment.sql
docker compose exec -T mysql sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -uroot water_meter_db < /tmp/ccb-bill-payment.sql'
```

迁移只增加 `bill_payment`，保留原有账单及已付汇总，不补造历史收款流水。本机已执行两次验证。

2026-09-18 新增权限、自动化和报表结构，2026-09-19 起新增自主覆盖策略、居民年度计费和消息已读记录。`start-local.ps1` 按文件名执行 `database/migrations/*.sql`；手动升级时同样依次执行 `20260918_auth.sql`、`20260918_automation.sql`、`20260918_reporting.sql`、`20260918_text_encoding.sql`、`20260919_autonomous_coverage.sql`、`20260919_notification.sql`、`20260919_residential_tariff.sql`，采用上面的复制及 MySQL 重定向方式。均为增量迁移，可重复运行。新库 `init.sql` 已包含这些表。自主覆盖策略初始关闭，迁移不会自动启用批量模拟出账。

### 中文乱码修复

早期初始化如果使用 latin1 客户端，会把 UTF-8 中文错误入库，表现为姓名、地址等出现 `å¼` 一类文字。数据库和表的 `utf8mb4` 设置不能代替导入连接的编码设置。现在 `init.sql` 和 `seed_ops.sql` 已显式执行 `SET NAMES utf8mb4`；导入仍应使用上面的 `docker cp` + `--default-character-set=utf8mb4` 方式，避免 PowerShell 文本管道改变文件字节。

已有数据库先备份，再运行修复迁移，无需重建数据卷或重启应用：

```powershell
docker cp ./database/migrations/20260918_text_encoding.sql water-mysql:/tmp/ccb-text-encoding.sql
docker compose exec -T mysql sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -uroot --default-character-set=utf8mb4 water_meter_db < /tmp/ccb-text-encoding.sql'
```

迁移仅精确匹配已知初始化乱码的原始字节，恢复姓名、地址、区域及配置说明；保留已修改的正常文字、更新时间与所有金额，重复运行不会二次转码。执行后刷新页面即可。编码回归测试使用独立临时库，验证错误客户端编码下的新库导入、旧库修复及重复执行：

```powershell
.venv-local/Scripts/python.exe tools/test_database_encoding.py
```

## 3. 首次安装应用依赖

### Python 环境

虚拟环境不能跨电脑直接复制。本仓库原 `.venv` 指向旧电脑的 Python，当前使用新建的 `.venv-local`，保留原目录。
先确认 `python --version` 能正常运行；若终端已激活失效的 `.venv`，先执行 `deactivate` 或新开终端。

```powershell
$env:PYTHONUTF8 = '1'
python -m venv .venv-local
& ./.venv-local/Scripts/python.exe -m pip install -r ./agent-engine/requirements.txt
& ./.venv-local/Scripts/python.exe -m pip check
```

本机可用的基础解释器是 `D:\ANACONDA\anaconda\python.exe`。如 PATH 中的 Python 不可用，将创建命令替换为：

```powershell
& 'D:\ANACONDA\anaconda\python.exe' -m venv .venv-local
```

不必激活虚拟环境，后续直接调用其 `python.exe`。`PYTHONUTF8=1` 用于避免中文 Windows 上 pip 读取 requirements 注释时出现 `UnicodeDecodeError: gbk`。

### 后端和前端

```powershell
Set-Location (Join-Path $projectRoot 'water-service')
mvn.cmd '-Dmaven.repo.local=../.m2repository' package -DskipTests

Set-Location (Join-Path $projectRoot 'web-admin')
$env:npm_config_cache = Join-Path $projectRoot 'logs/npm-cache'
npm.cmd ci
```

后端使用项目内 `.m2repository` 缓存；首次缺少依赖时需要联网。构建命令跳过测试，用于生成启动 JAR。依赖已安装且未变更时，无需反复安装。

## 4. 分别启动三个应用

每个服务使用一个独立终端，先设置本文开头的 `$projectRoot`。保持终端运行；`Ctrl+C` 停止对应服务。

手动启动时，Python 和 Java 必须使用相同内部密钥。在项目根目录仅生成一次，随后在终端 A、B 分别载入（不要输出或分享该文件）：

```powershell
New-Item -ItemType Directory -Path (Join-Path $projectRoot 'logs') -Force | Out-Null
$secretFile = Join-Path $projectRoot 'logs/agent-internal-token.txt'
if (-not (Test-Path -LiteralPath $secretFile)) {
  $bytes = New-Object byte[] 32
  $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
  try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
  [IO.File]::WriteAllText($secretFile, [Convert]::ToBase64String($bytes))
}
$env:AGENT_INTERNAL_TOKEN = [IO.File]::ReadAllText($secretFile).Trim()
```

### 终端 A：智能体引擎（8087）

```powershell
Set-Location (Join-Path $projectRoot 'agent-engine')
$env:PYTHONUTF8 = '1'
$env:FLASK_HOST = '127.0.0.1'
$env:FLASK_PORT = '8087'
$env:FLASK_DEBUG = 'False'
& ../.venv-local/Scripts/python.exe -u main.py
```

启动时会执行内置演示，随后显示 `Running on http://127.0.0.1:8087`。默认抄表使用 CSV 传感样例，不需要安装 PaddleOCR 或配置模型密钥。

### 终端 B：业务后端（8080）

```powershell
Set-Location (Join-Path $projectRoot 'water-service')
java -Duser.timezone=Asia/Shanghai -jar ./target/water-meter-service-1.0.0.jar --logging.file.name=../logs/water-meter-service.log
```

等日志出现 `Started WaterMeterApplication`。修改 Java 或 `application.yml` 后，先停止后端，重新执行第 3 节 Maven 构建，再启动 JAR，避免运行旧配置。

### 终端 C：管理端（3000）

```powershell
Set-Location (Join-Path $projectRoot 'web-admin')
npm.cmd run dev -- --host 127.0.0.1 --strictPort
```

打开 [登录页面](http://127.0.0.1:3000/login)，输入 `admin / admin123`。
`--strictPort` 会在端口被占用时明确报错，防止 Vite 自动换端口。

## 5. 地址、端口和配置

| 服务 | 宿主机地址 / 端口 | 容器内端口 / 说明 |
|---|---|---|
| 管理端 | [127.0.0.1:3000](http://127.0.0.1:3000) | Vite 开发服务 |
| 业务 API | `localhost:8080/api/v1` | [API 文档](http://localhost:8080/doc.html) |
| 智能体 API | `127.0.0.1:8087/api` | [健康检查](http://127.0.0.1:8087/api/health) |
| MySQL | `localhost:3308` | `3306`；库 `water_meter_db`，本地账号 `root / 123456` |
| Redis | `localhost:16379` | `6379`；本地无密码 |
| InfluxDB | [localhost:8086](http://localhost:8086) | `8086` |
| MinIO | API `localhost:9000`；[控制台](http://localhost:9001) | `9000 / 9001`；`minioadmin / minioadmin` |
| Nacos | [localhost:18848/nacos](http://localhost:18848/nacos)；gRPC `19848` | `8848 / 9848` |

前端 `vite.config.js` 将 `/api` 和 `/agent-api` 均代理到后端 `8080`。后端校验管理员会话后，携内部密钥访问本机 `8087` 引擎；直接访问引擎业务接口会被拒绝，只有健康检查公开。前端没有单独的 `.env` 前置要求。

后端连接设置在 `water-service/src/main/resources/application.yml`。需要覆盖默认值时，在**启动 Java 的同一终端**设置环境变量，例如 `SPRING_DATA_REDIS_PORT`、`SPRING_DATASOURCE_URL`、`AGENT_ENGINE_URL`。

本地默认 `NACOS_DISCOVERY_ENABLED=false`；只有要启用注册发现时才设为 `true`，并使用 `NACOS_SERVER=localhost:18848`。Compose 未部署 Kafka、Elasticsearch；Elasticsearch 健康检查默认关闭，接入实际服务后设置 `ES_URIS` 和 `ES_HEALTH_ENABLED=true`。

## 6. 启动验收

在第四个 PowerShell 终端执行：

```powershell
(Invoke-WebRequest -UseBasicParsing http://127.0.0.1:3000).StatusCode
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://127.0.0.1:8087/api/health

$login = Invoke-RestMethod -Method Post `
  -Uri http://127.0.0.1:3000/api/v1/auth/login `
  -ContentType 'application/json' `
  -Body '{"username":"admin","password":"admin123"}'
$login.success
$login.data.userInfo.username
$headers = @{ Authorization = "Bearer $($login.data.token)" }
Invoke-RestMethod http://127.0.0.1:3000/agent-api/health -Headers $headers
Invoke-RestMethod http://127.0.0.1:3000/api/v1/ops/cockpit -Headers $headers
```

预期：页面 `200`、后端健康 `UP`、引擎 `healthy`、驾驶舱 `success=true`，登录返回 `True` 和 `admin`。未带会话的业务请求返回 `401`，普通用户访问管理接口返回 `403`。会话有效期 8 小时，退出、过期或停用账号后失效。健康检查通过不代表外部设备或支付机构已接入。

## 7. 日常重启、停止和日志

日常启动：在根目录执行 `docker compose up -d`，再按第 4 节运行三个应用。已初始化的数据保留在 Docker 命名卷中。

停止前台应用：在各自终端按 `Ctrl+C`。停止中间件：在根目录执行 `docker compose stop`；恢复使用 `docker compose up -d`。不要用 `docker compose down -v` 做日常停止，它会删除数据库等数据卷。

本次后台启动的标准输出保存在根目录 `logs/backend.stdout.log`、`logs/frontend.stdout.log`、`logs/agent.stdout.log`；错误输出为对应的 `.stderr.log`。后端业务日志为 `logs/water-meter-service.log`，引擎滚动日志在 `agent-engine/logs/`。

```powershell
Get-Content ./logs/backend.stdout.log -Tail 50
Get-Content ./logs/agent.stderr.log -Tail 50
docker compose logs --tail 50 mysql redis nacos
```

后台服务不随终端关闭而停止。需要手动停止时，先找到对应监听进程并核对命令行，再停止该 PID；不要批量结束全部 Java、Node 或 Python 进程：

```powershell
Get-NetTCPConnection -State Listen |
  Where-Object { $_.LocalPort -in @(3000,8080,8087) } |
  Select-Object LocalAddress,LocalPort,OwningProcess
# 将 12345 替换为上一步要停止的服务 PID
Get-CimInstance Win32_Process -Filter 'ProcessId=12345' |
  Select-Object ProcessId,ExecutablePath,CommandLine
Stop-Process -Id 12345
```

## 8. 常见问题

| 现象 | 处理 |
|---|---|
| MinIO `pull access denied` | 当前 Compose 已改用 `quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z`；执行 `docker compose pull minio` 后重试启动。 |
| `version is obsolete` | 当前 Compose 已移除顶层 `version`，确认执行的是本目录文件。 |
| 端口报 `forbidden by its access permissions` | 执行 `netsh interface ipv4 show excludedportrange protocol=tcp`。本机原 `6379`、`9848` 落在保留范围，现使用 `16379`、`18848/19848`；改端口时同步后端，Nacos gRPC 映射须保持 HTTP 端口 + 1000。 |
| `No Python at ...` | `.venv` 来自其他电脑；按第 3 节使用本机解释器创建 `.venv-local`。 |
| pip 报 `gbk` 解码错误 | 同一终端设置 `$env:PYTHONUTF8='1'`，再安装依赖。 |
| 登录提示用户不存在 | `init.sql` 不创建 admin；新演示库需执行第 2 节的 `seed_ops.sql`。 |
| `/api` 或 `/agent-api` 返回代理连接错误 | 分别检查后端 `8080` 和引擎 `8087` 是否运行；不能只启动 Vite。 |
| 后端健康检查因 Elasticsearch 返回 503 | 重新构建加载当前配置；本地默认关闭该可选检查，若环境设置了 `ES_HEALTH_ENABLED=true`，需启动实际 ES 服务或取消覆盖。 |
| 改配置后没有生效 | JAR 内嵌旧配置；停止后端、重新 Maven 构建，再启动。 |
| 页面图表没有实时数据 | 先检查接口响应和种子数据；CSV/演示数据时间可能早于今天，不能仅凭服务启动判断数据采集链路已接入。 |

## 9. 计费与缴费操作

登录后进入「智能收费 → 缴费管理」或「账单列表」，两个入口使用同一账务流程：

1. 按账单编号、用户、账期或状态查询；顶部显示当前筛选范围的应付、已付、剩余及已到期欠款。
2. 点击「登记收款」，核对最新余额，输入正数且最多两位小数的金额。可分次收款，不能超过剩余应付。
3. 部分收款后状态为「部分支付」；结清后为「已支付」。进入「详情 / 流水」查看起止读数、费用和每笔登记。
4. 若网络中断导致结果未确认，保留当前浏览器标签页，使用「核对并重试原登记」。刷新页面后仍能复用原请求号，避免重复入账；金额与渠道在核对期间锁定。

这是**线下 / 演示收款登记**，渠道选择不会调用微信、支付宝或银行扣款。原请求保存在当前标签页的 sessionStorage 中；不要在结果未确认时关闭标签页或清理存储。

出账接口 `POST /api/v1/bill/generate?meterId=...&readingId=...` 仅接受审核通过且归属匹配的抄表，使用 `usageAmount` 计算本期用量。同一抄表重复出账返回原账单。费率沿用 `application.yml`，未绑定居民年度结算户的水表仍按原演示方案计算，污水费按水费乘 `sewage-rate` 计算；例如居民用量 30 吨：水费 62.10 + 污水费 55.89 = 117.99 元。这是当前演示配置，不代表当地正式收费政策。

### 回归测试

中间件启动后，在项目根目录运行：

```powershell
& ./tools/test-billing.ps1 -Maven 'D:\Mavaen\apache-maven-3.9.11\bin\mvn.cmd'
Set-Location (Join-Path $projectRoot 'web-admin')
node --test src/utils/billing.test.js src/utils/session.test.js
$env:npm_config_cache = Join-Path $projectRoot 'logs/npm-cache'
npm.cmd run build
```

脚本准备独立的 `water_meter_billing_test` 库并开启所有模块 MySQL 集成测试；使用本地 Compose 的 3308 端口和开发账号。测试后只清理本次创建的数据，测试库保留供下次运行。不要让多个集成测试进程同时使用此库（回滚测试会短暂创建故障触发器）。普通 `mvn test` 只运行单元测试，MySQL 测试默认跳过。智能体鉴权测试：在 `agent-engine` 下运行 `../.venv-local/Scripts/python.exe -m unittest discover -s tests -p test_gateway_auth.py`。

Windows 上重新打包前应先停止当前项目的 Java 进程，否则可能出现 `Unable to rename ...jar` 文件占用错误。构建完成后按第 4 节重新启动。

## 10. 用户端、自动调度、违约金和智能报表

**用户端：** 普通用户进入「我的用水工作台」查看自己的水表、最近 200 条抄表、账单与流水；即将到期 7 天内及逾期账单显示站内提醒。可执行演示缴费、提交异常反馈；管理员在「用户反馈」回复并设为处理中或已解决。提醒和反馈均在系统内，不代表已经发送短信或邮件。

**自动采集：** 管理员进入「自动采集与计费」，选择水表、采集间隔、补抄次数和模拟场景，创建启用计划。每 10 秒检查到期计划，失败按配置间隔补抄；最多尝试 5 次，全部失败保留记录并生成告警。弱信号、低电或故障设备的自适应间隔减半。计划、每表任务及原七字段报文持久化；重启后继续到期任务。成功采集自动保存读数并出账，相同表号和时间的相同报文重复提交只返回原读数；内容冲突或累计读数回退被拒绝。旧待审核记录不能倒退计费基线。

该模块使用标注清晰的模拟设备，不代表真实 NB-IoT 接入。可以选择「首次超时，补抄成功」验收自动重试。「七字段模拟报文接入」支持赛题协议（本地时间精确到秒）；此接口仅管理员可调用。

**自主制定计划：** 同页顶部“自主制定采集计划”设置基础间隔和每次模拟增量，保存启用后，后台自动为已绑定有效用户的正常/故障水表补建计划，新建水表下一轮自动纳入。每轮最多新增100个计划，持续推进；已启用手工计划优先，停用/更换水表或停用用户退出自主覆盖。自动计划不能单独暂停，需修改覆盖策略或停用对应档案。关闭策略只停止后续计划执行，已下发的采集/补抄继续完成。模拟成功采集会生成系统账单，测试时宜使用独立库。

**阶梯范围：** 已配置“居民年度计费”结算户的水表按结算户年度累计额度计费（见第13节）。未绑定的水表仍用原逐条抄表演示算法；商业/工业的年度超定额加价尚未接入。

**违约金：** 在同页切换「违约金规则与流水」，设置启用、宽限天数、日费率、累计封顶比例与生效日期。默认关闭，避免未配置规则时给历史演示账单加费。启用后每小时自动计算，也可手动运行；只处理昨日及以前已结束的自然日，不追收首次升级前日期。按每日未付本金计算，付款优先抵本金，不复利，已结清不重开；历史无流水的已付金额保守视为已付。每账单每天只登记一次，修改规则保留版本且不追溯已登记日期。未启用收费时仍会更新到期欠款状态。页面费率仅为演示配置。

**智能报表：** 管理员在「统计报表」选择日期及日/周/月/年，或输入 `生成本月按日报表`、`2026-08-01至2026-08-31按周报表`、`生成今年按年报表`。采用确定性中文指令解析，不支持的过滤条件明确报错，不需要大模型密钥。用量取已确认读数，收入取收款流水；历史已付汇总没有流水时不计入新报表收入。故障率采用去重故障水表数 / 截至期末累计建档水表数，页面及导出均标明口径。

生成后下载 XLSX 或 PDF，二者与页面使用同一个已保存快照；修改筛选需重新生成。日报最多 366 天，其他粒度最多 3660 天。PDF 在本机自动使用 `C:/Windows/Fonts/simhei.ttf`；其他环境通过 `REPORT_PDF_FONT` 指定可用中文 TTF 字体。缺字体时明确报错。

## 11. 水务智能助手

管理员进入「水务智能助手」。该模块已改为查询真实业务记录的规则问答，返回结论、数据依据、统计范围、查询时间及相关业务入口，不再调用随机模拟报表或显示引擎原始对象。

- `哪些水表需要优先巡检？`：按故障、未处理告警、电量、信号、缺失数据及最后抄表时间排序，展示前10块水表、具体理由和处理建议。可添加已建档区域名称、编码、A区/B区等别名或一个 `WM-...` 表号；区域包含下级区域。排序分数不是故障概率。
- `A区是不是漏水？`：只读取该范围内未处理的漏水相关告警；提示核查依据，不把告警或告警缺失视为现场结论。
- `为什么本月水费收入下降？` / `本月收入与上月同期相比如何？`：按北京时间比较本月截至查询时刻与上月同日同时刻的收款登记流水，月末按实际天数截断。不预设收入下降；无流水、零基数及不能推断原因会明确提示。历史 `bill.paid_amount` 未迁为流水的金额不混入统计。
- `当前有多少欠费账单？`：使用应收减累计已付计算当前余额，包含部分付款。收入/欠费问答暂不支持区域、单表或个人限定；其他日期请使用统计报表。

该模块当前不是自由对话大模型，不支持连续追问、任意条件组合或自动派单/调价/扣费。不能识别的对象、日期、用户类型或自定义阈值会返回澄清，不会忽略条件后给出全局数字。无需配置模型密钥，Python 引擎不可用也不影响该问答模块。每次查询沿用管理员接口权限；数据库读取事务不会修改业务数据。

前端支持中文输入法、Enter发送、Shift+Enter换行、失败重试与清空对话。回归测试 `AssistantServiceTest` 和 `AssistantMySqlIntegrationTest` 纳入第9节的全项目测试脚本。

## 12. 水表档案、异常处置与统计口径

“水表资产”支持真实分页、按表号/状态/通信检索、建档、编辑和详情。用户及区域从系统读取；读数不可从档案表单覆盖。已有读数、账单或采集计划的水表禁止直接改表号、转户；使用停用保留历史。

“异常记录”展示采集检测产生的告警。可填写核查结论直接处理，或生成处置工单。重复生成返回同一工单；未完成关联工单时不能直接关闭告警。工单接单后，填写处置结果并完成，自动同步告警为已处理；关闭未解决工单需填写原因，告警恢复待处理，可重新派单。

首页今日用量仅统计今天已审核记录，无当日读数时显示0，不回退为历史用量。分区先分别汇总读数和告警，避免多条告警重复放大用量。收入来自实际收款登记流水；没有流水的历史已付汇总不计入某日收入。收费图的“当月出账应收”“当月登记收入”“当月账单剩余”分别依据出账日期、收款日期和当前余额，不能直接相减等同。健康度为规则分，夜间用量按抄表时刻分类，均不能当作已验证故障概率或现场漏损率。

## 本次验证（2026-09-19）

重新逐项核对赛题，补齐真实水表维护、异常到工单的处置闭环、自主制定覆盖计划，并修复统计口径。76项 Java 测试通过（含40项真实 MySQL 集成测试），9项前端测试通过，后端JAR打包及前端生产构建通过。Edge实测页面建档和编辑、模拟故障入库、告警转工单、接单完成同步告警、新接口普通用户403；使用当次唯一标记的临时数据并清理，未启用业务库的全域模拟采集。前端仍有大资源包及 Sass 弃用警告。

自主覆盖策略迁移已应用，本机新版后端已启动。完整差距、证据与未完成交付见 [赛题对照与缺口验收](docs/competition-requirements.md)。此为赛题初次整改验证；随后新增的居民年度计费见第13节。比赛正式材料、真实支付接入、非居民超定额及学习模型效果评估仍需继续完成。

## 前一轮验证记录（2026-09-18）

助手修复后再次运行全项目测试：67项 Java 测试通过（含31项真实 MySQL 集成测试），后端打包和前端生产构建通过。新增用例覆盖巡检排序、数据缺失、区域与下级区域、未知/混合条件拒绝、收入零基数、同期比较和部分付款。Edge验证原巡检问题、其他快捷提问、重复请求稳定性、清空对话、中文输入法、失败重试及手机布局。

此前用户端、调度和报表版本的55项 Java 测试（含28项真实 MySQL 集成）、9项前端测试、2项 Flask 内部鉴权测试全部通过。当时运行了 Edge 全流程验收；实际下载的 XLSX 用 openpyxl 打开后与快照逐项核对，PDF 中文、分页及表头正常。独立审查发现的问题已修复并复核。

五个中间件、三个应用已启动；管理员登录、后端健康检查、智能体健康检查、Vite 双代理、驾驶舱、水表列表和地图接口已验证。账务测试包括金额边界、重复及并发出账、部分收款、并发防超收、重复请求和事务回滚。Edge 已验证登录、40 + 77.99 元分次收款、丢失响应后刷新重试、最终仅两笔流水及余额归零。Java JAR 打包及前端生产构建通过；前端存在 Sass 弃用提示及较大资源包警告。

新增四项已完成实现与联调：角色与自有数据隔离、用户缴费反馈闭环、持久化自动采集与补抄、可配置每日违约金、数据库报表快照及真正 XLSX/PDF 导出。测试还覆盖等待补抄计划不能阻塞其他计划，以及旧审核不能倒退累计读数。比赛材料、真实设备接入和真实第三方支付不包含在本次实现，详见 [赛题需求与开发验收清单](docs/competition-requirements.md)。

模块细节见 [智能体说明](agent-engine/README.md)、[管理端说明](web-admin/README.md)。[项目总览](PROJECT_OVERVIEW.md) 包含历史能力评估，启动步骤以本文件为准。

## 13. 杭州居民年度计费

资料核对见 [杭州水务资料与系统差距](docs/hangzhou-water-policy-notes.md)。管理员进入“智能收费 → 居民年度计费”，可创建独立结算户并绑定同一居民用户的1至50块水表。同一登录账号可以有多个用水地址的结算户，不自动按账号合并所有水表。

1. 先核对水表实际适用地区，选择市区、余杭其他地区或余杭西部四镇方案。三者供水阶梯均为年度216/300立方米、1.90/2.85/5.70元，污水单位费额分别为1.00/0.95/0.65元。
2. 输入生效日期、生效日前的当年累计用量及核对依据。新开户可为0，已有用户不能为了演示省略年内已用量。生效账期已有账单时禁止直接绑定，须选择后续未出账账期并核对期初用量。
3. 绑定后，正常采集、审核出账和批量出账使用同一年度台账。此前累计210立方米，本次20立方米，市区方案应生成供水费51.30、污水费20.00，合计71.30元；年度累计变为230。分多次出账使用累计费用差额，避免小数舍入叠加偏差。
4. 账单“详情 / 流水”显示结算户、地区方案、年度累计起止量、各阶梯数量/单价/费用及污水费。管理员和账单所属用户均可查看；年度台账及方案配置仅管理员可访问。

**边界：** 有上一年抄表且用量区间跨年时，要求上一年末23:59:59分界读数，不能自动猜测跨年拆量。新完整年度从0重新累计；同年晚到记录不能倒序插入已结算时间之前。生效前未出账读数会要求核查，因此应先完成历史出账再配置。已绑定结算户暂不开放直接改价、重分组或转户，已有账单不重算。

三种预置方案只覆盖标准居民阶梯；多人口增额、合表价、非居民超定额及合同差异仍需完善。默认不为现有水表自动绑定地区，也不将资料中的3‰自动设为违约金。未绑定水表继续原演示方案，页面有明确提示。

验收：92项 Java 测试通过（含51项真实 MySQL 集成测试）、9项前端测试通过，后端打包和前端生产构建通过。新增覆盖跨档、分区污水费、累计舍入、并发共享额度、重复请求、失败回滚、跨年分界、迟到数据和保护旧账单；修复了测试复现的账单间隙锁与同表锁升级死锁。完整日志 `logs/tariff-package.log`。

Edge已实际验证：新建结算户录入期初210，模拟上报新增20，自动出账71.30；重复出账返回原单且累计保持230。账单页面显示供水51.30、污水20.00和分档数量，所属用户可获取自己的计价快照，普通用户访问配置接口403。验收临时记录已清理，日志见 `logs/tariff-browser.log`，截图见 `logs/tariff-annual-ledger.png`、`logs/tariff-bill-detail.png`。

## 14. 站内消息中心

点击右上角铃铛打开消息抽屉，在主页也能直接使用。管理员汇总待处理异常、未完成工单、七天内到期及逾期欠费账单（包含部分付款）、补抄耗尽重试后的失败任务、未解决用户反馈及诊断进度；普通用户只查看本人的到期账单、反馈回复及辅助诊断进度。

- 角标代表当前消息范围内的未读数；每类展示最近100条，有超出时显示提示，完整数据请进入业务页面。分类按钮上的数字也是未读数，顶部“当前消息”包含已读。
- 支持分类、未读筛选、内容搜索、分页、单条已读/未读及“当前筛选全部已读”（包含筛选后的所有分页）。打开消息详情或业务入口会标记该消息已读，批量操作不会标记此后新到达的消息。
- 已读状态在数据库按账号保存，刷新、重新登录仍保留，管理员之间也互不影响。内容或业务状态变化产生新的未读版本。已读不会完成业务处置；异常结案、工单完成、账单结清、反馈解决后，对应管理员待办退出列表。用户已收到的回复仍可查看。
- 异常、工单和反馈可直达对应记录详情；账单在消息中心打开真实账单及流水。采集失败入口进入采集管理。普通用户可查看自己的反馈回复并进入反馈进度。
- 页面可见时每60秒刷新，切回页面或点击刷新立即更新。失败时角标显示“!”，抽屉保留上次内容并提示重试，不将加载失败显示为“零未读”。

这是站内业务提醒，不发送短信、邮件或微信消息。当前列表属于业务状态视图，不是完整历史事件档案。

迁移 `20260919_notification.sql` 只添加 `notification_read`，不会修改账单或工单。新接口 `GET /api/v1/me/notifications`、`POST /api/v1/me/notifications/read` 从登录会话确定身份，不接受客户端指定用户或角色。后端6项新增MySQL测试覆盖隔离、已读持久化、版本变化、批量快照、部分付款和列表截断，纳入第9节测试脚本。

Windows更新后端时，先停止本项目旧Java进程，再打包启动，避免运行中的JAR被占用导致Maven提示无法重命名。前端开发服务运行时刷新浏览器即可加载新入口；手动部署静态文件需重新执行前端构建。
消息中心验收（2026-09-20）：98项Java测试全部通过（含57项真实MySQL集成测试），9项前端测试通过。测试日志 `logs/notification-package.log`；该次打包因旧进程占用JAR失败，停止对应旧Java后重打包成功，见 `logs/notification-repackage.log`。前端生产构建通过，仍有原有大资源包/Sass警告。

Edge实测通过：首页铃铛打开、各类真实消息、已读刷新保留、按筛选批量已读、直达异常/工单/反馈记录、加载错误保留数据并重试恢复，以及390像素手机宽度的个人消息、账单详情、反馈回复和未读空态。测试使用独立临时账号及记录，结束已清理，不改变原管理员的阅读状态。日志 `logs/notification-browser.log`，截图 `logs/notification-center-desktop.png`、`logs/notification-center-mobile.png`。同时修复了窄屏侧栏默认遮挡顶部操作、消息内账单弹窗被抽屉覆盖的问题。
