# 水表抄表收费管理系统 - Web管理端

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

```bash
# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 构建生产版本
npm run build

# 预览生产版本
npm run preview
```

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
    target: 'http://localhost:8087',
    changeOrigin: true
  }
}
```

## 部署

```bash
# 构建
npm run build

# 部署到nginx
cp -r dist/* /var/www/html/
```

## License

MIT