# 水表抄表收费管理系统 - Web管理端

> 2026-09-18：权限、用户工作台、自动采集/补抄、违约金及数据库报表已接入，具体启动与演示边界以 [根目录指南](../README.md) 为准。智能体业务接口现在只接受 Java 网关的内部密钥，手动启动须设置同一个 `AGENT_INTERNAL_TOKEN`；不再允许前端绕过后端直接调用。


基于 Vue3 + Element Plus + ECharts 的前端管理系统。

## 功能模块

- 📊 数据大屏 - 实时数据可视化
- 💧 水表管理 - 水表列表、AI抄表
- 📝 抄表管理 - 抄表记录、审核
- 💰 账单管理 - 账单生成、缴费
- ⚠️ 异常管理 - 异常检测、工单
- 👤 用户管理 - 用户信息
- 📈 统计报表 - 数据分析

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.4 | 渐进式JavaScript框架 |
| Vue Router | 4.2 | 路由管理 |
| Pinia | 2.1 | 状态管理 |
| Element Plus | 2.4 | UI组件库 |
| ECharts | 5.4 | 数据可视化 |
| Axios | 1.6 | HTTP请求 |
| Vite | 5.0 | 构建工具 |
| Sass | 1.69 | CSS预处理器 |

## 快速开始

完整的首次初始化、后端和 AI 引擎启动步骤见 [项目启动指南](../README.md)。前端依赖业务后端 `8080` 与智能体引擎 `8087`，仅运行 Vite 无法完成登录和接口调用。

以下 PowerShell 命令从项目根目录执行：

```powershell
Set-Location ./web-admin
# 首次安装或 package-lock.json 变更后执行
npm.cmd ci

# 启动开发服务器（保持终端运行）
npm.cmd run dev -- --host 127.0.0.1 --strictPort
```

访问 [登录页面](http://127.0.0.1:3000/login)，账号 `admin`，密码 `admin123`。新数据库必须先按根目录指南执行 `database/seed_ops.sql`；`init.sql` 不创建管理员账号。按 `Ctrl+C` 停止前端。

```powershell
# 构建生产静态资源到 dist/
npm.cmd run build
```

Vite 开发代理不等于生产部署配置。部署 `dist/` 时还需由 Web 服务器配置 SPA 路由回退和 API 反向代理。

## 目录结构

```
src/
├── api/                 # API接口
├── assets/              # 静态资源
│   └── styles/          # 样式文件
├── components/          # 公共组件
├── router/              # 路由配置
├── stores/              # Pinia状态
├── utils/               # 工具函数
└── views/               # 页面组件
    ├── dashboard/       # 数据大屏
    ├── meter/           # 水表管理
    ├── bill/            # 账单管理
    ├── anomaly/         # 异常管理
    ├── user/            # 用户管理
    └── report/          # 统计报表
```

## 页面截图

### 登录页
- 渐变背景
- 表单验证
- 记住密码

### 数据大屏
- 四大核心指标卡片
- 用水趋势图
- 收入统计图
- 异常分布饼图
- 抄表方式占比

### 水表管理
- 水表列表搜索
- AI图像抄表对话框
- 批量操作

### 抄表管理
- 抄表记录列表
- 置信度进度条
- 审核功能

### 账单管理
- 统计卡片
- 账单列表
- 缴费对话框

## 环境配置

开发环境代理配置：

```javascript
// vite.config.js
proxy: {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true
  },
  '/agent-api': {
    target: 'http://localhost:8080',
    changeOrigin: true
  }
}
```

后端接口前缀为 `/api/v1`。智能体代理须携管理员 `Authorization: Bearer ...` 会话，通过 Java 授权后才调用引擎。完整验收命令见根目录 README。用户工作台、自动化、反馈及报表均已接入真实持久化接口。

## 部署

```bash
# 构建
npm run build

# 部署到nginx
cp -r dist/* /var/www/html/
```

## License

MIT
