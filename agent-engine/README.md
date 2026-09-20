# 智能体引擎 (Agent Engine)

> 2026-09-18：权限、用户工作台、自动采集/补抄、违约金及数据库报表已接入，具体启动与演示边界以 [根目录指南](../README.md) 为准。智能体业务接口现在只接受 Java 网关的内部密钥，手动启动须设置同一个 `AGENT_INTERNAL_TOKEN`；不再允许前端绕过后端直接调用。


基于AI的水表抄表收费管理系统的多智能体核心引擎。

## 架构设计

```
┌──────────────────────────────────────────────────────────┐
│                  Agent Orchestrator                       │
│                 (智能体协调器)                              │
├──────────────────────────────────────────────────────────┤
│                                                           │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐       │
│  │ 抄表智能体   │  │ 计费智能体   │  │ 异常智能体   │       │
│  │             │  │             │  │             │       │
│  │ • AI视觉抄表 │  │ • 阶梯水价   │  │ • 三层检测   │       │
│  │ • 远程抄表   │  │ • 账单生成   │  │ • 分级预警   │       │
│  │ • 读数校验   │  │ • 优惠计算   │  │ • 工单派发   │       │
│  └─────────────┘  └─────────────┘  └─────────────┘       │
│                                                           │
│  ┌─────────────┐  ┌─────────────┐                        │
│  │ 催缴智能体   │  │ 分析智能体   │                        │
│  │             │  │             │                        │
│  │ • 用户画像   │  │ • 趋势预测   │                        │
│  │ • 策略选择   │  │ • 模式分析   │                        │
│  │ • 差异化催缴 │  │ • 报告生成   │                        │
│  └─────────────┘  └─────────────┘                        │
│                                                           │
├──────────────────────────────────────────────────────────┤
│                  Knowledge Base (知识库)                   │
│                  Event Bus (事件总线)                       │
└──────────────────────────────────────────────────────────┘
```

## 核心特性

### 1. 自主感知 (Perceive)
- 抄表智能体: 感知水表图像、远程数据、抄表任务
- 计费智能体: 感知抄表数据、费率规则、用户类型
- 异常智能体: 感知用水波动、设备状态、异常信号
- 催缴智能体: 感知欠费状态、用户行为、催缴效果
- 分析智能体: 感知历史数据、区域统计、预测输入

### 2. 自主决策 (Decide)
- 三层异常检测: 规则引擎 → 统计模型 → AI深度检测
- 智能催缴策略: 基于用户画像的差异化催缴
- 趋势预测: 时序模型预测未来用水量

### 3. 自主执行 (Execute)
- 自动录入有效读数
- 自动生成账单
- 自动派发工单
- 自动发送催缴通知

## 快速开始

完整的中间件、后端、前端启动顺序和管理员初始化见 [项目启动指南](../README.md)。引擎默认端口为 `8087`，使用 Python 3.10–3.12；默认 CSV 演示无需 OCR 依赖。

### 安装依赖（PowerShell，从项目根目录执行）

```powershell
$env:PYTHONUTF8 = '1'
python -m venv .venv-local
& ./.venv-local/Scripts/python.exe -m pip install -r ./agent-engine/requirements.txt
```

如旧 `.venv` 提示 `No Python at ...`，使用本机有效 Python 创建上面的新环境；不要复制其他电脑的虚拟环境。

### 运行系统（独立终端）

```powershell
Set-Location ./agent-engine
$env:PYTHONUTF8 = '1'
$env:FLASK_HOST = '127.0.0.1'
$env:FLASK_PORT = '8087'
# 先按根 README 生成密钥文件；Java 端也加载同一个值。
$env:AGENT_INTERNAL_TOKEN = [IO.File]::ReadAllText((Join-Path (Split-Path (Get-Location) -Parent) 'logs/agent-internal-token.txt')).Trim()
$env:FLASK_DEBUG = 'False'
& ../.venv-local/Scripts/python.exe -u main.py
```

启动会先执行内置演示，再提供 Flask API。健康检查：`Invoke-RestMethod http://127.0.0.1:8087/api/health`，预期 `status=healthy`。按 `Ctrl+C` 停止。应用滚动日志位于当前工作目录的 `logs/`。

### API 接口

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/health` | GET | 健康检查 |
| `/api/status` | GET | 系统状态 |
| `/api/agents` | GET | 智能体列表 |
| `/api/meter-reading` | POST | 执行抄表 |
| `/api/billing` | POST | 生成账单 |
| `/api/anomaly/detect` | POST | 异常检测 |
| `/api/collection/execute` | POST | 执行催缴 |
| `/api/analysis/forecast` | POST | 用水预测 |
| `/api/workflow/create` | POST | 创建工作流 |

## 使用示例

### 1. CSV 传感抄表（默认）

数据文件：`data/hangzhou_water_meter_1000.csv`（表号如 `HZ000001`）。

```python
import requests
import os

# 仅限本机服务联调；前端使用带管理员会话的 Java 网关。
internal_headers = {"X-Agent-Token": os.environ["AGENT_INTERNAL_TOKEN"]}

# 单表远程抄表
response = requests.post('http://localhost:8087/api/meter-reading', headers=internal_headers, json={
    'mode': 'remote',
    'meter_id': 'HZ000001'
})
print(response.json())

# 按区批量
response = requests.post('http://localhost:8087/api/meter-reading', headers=internal_headers, json={
    'mode': 'schedule',
    'district': '西湖区',
    'meter_count': 5
})
```

### 2. 异常检测

```python
response = requests.post('http://localhost:8087/api/anomaly/detect', headers=internal_headers, json={
    'meter_id': 'WM-A001-0001',
    'current_usage': 150,
    'usage_history': [10, 12, 11, 10, 13, 11],
    'zero_usage_days': 0
})
print(response.json())
```

### 3. 执行工作流

```python
# 创建抄表→计费工作流
response = requests.post('http://localhost:8087/api/workflow/create', headers=internal_headers, json={
    'template': 'reading_billing_workflow',
    'context': {'meter_id': 'WM-A001-0001'}
})
workflow_id = response.json()['data']['workflow_id']

# 执行工作流
response = requests.post(f'http://localhost:8087/api/workflow/{workflow_id}/execute', headers=internal_headers)
print(response.json())
```

## 智能体详解

### MeterReadingAgent (抄表智能体)

```python
from agents import MeterReadingAgent

agent = MeterReadingAgent()
agent.start()

# 执行抄表周期（CSV 传感读数）
result = agent.run_cycle({
    'mode': 'remote',
    'meter_id': 'HZ000001'
})
```


### AnomalyAgent (异常智能体)

三层检测机制:
1. **规则层**: 快速过滤明显异常（突增>300%、零用量>30天等）
2. **统计层**: Z-Score、同比环比检测
3. **AI层**: 深度模型检测漏水和偷水特征

## 配置说明

当前 `main.py` 读取以下 Flask 环境变量，也可在本目录自行创建 `.env` 设置（文件不是启动前置条件）：

```env
# API服务
FLASK_HOST=127.0.0.1
FLASK_PORT=8087
FLASK_DEBUG=False
```

默认引擎使用内存状态与 CSV 样例，代码没有读取此前文档列出的 `DB_*`、`REDIS_*`、`AGENT_CONFIDENCE_THRESHOLD` 和 `ANOMALY_ALERT_THRESHOLD` 环境变量。MySQL / Redis 由 Java 后端连接，分别使用宿主机 `3308` / `16379`。

## 目录结构

```
agent-engine/
├── main.py              # 主程序入口
├── requirements.txt     # Python依赖
├── .env                 # 环境配置
├── core/                # 核心模块
│   ├── base_agent.py    # 智能体基类
│   ├── orchestrator.py  # 协调器
│   ├── knowledge_base.py# 知识库
│   └── event_bus.py     # 事件总线
├── agents/              # 具体智能体
│   ├── meter_reading_agent.py
│   ├── billing_agent.py
│   ├── anomaly_agent.py
│   ├── collection_agent.py
│   └── analysis_agent.py
├── api/                 # API接口
│   └── routes.py
├── models/              # AI模型
└── utils/               # 工具函数
```

## License

MIT
